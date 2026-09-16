package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.BarConstant;
import com.fuhua.booknest.common.constant.NotificationConstant;
import com.fuhua.booknest.common.constant.PostStatusConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.dto.CommentPublishDTO;
import com.fuhua.booknest.pojo.entity.AiBot;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.entity.PostCollect;
import com.fuhua.booknest.pojo.entity.PostComment;
import com.fuhua.booknest.pojo.entity.PostCommentLike;
import com.fuhua.booknest.pojo.entity.PostLike;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.CommentVO;
import com.fuhua.booknest.server.common.CountBuffer;
import com.fuhua.booknest.server.mapper.AiBotMapper;
import com.fuhua.booknest.server.mapper.PostCollectMapper;
import com.fuhua.booknest.server.mapper.PostCommentLikeMapper;
import com.fuhua.booknest.server.mapper.PostCommentMapper;
import com.fuhua.booknest.server.mapper.PostLikeMapper;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.mq.NotificationProducer;
import com.fuhua.booknest.server.service.AiBotReplyService;
import com.fuhua.booknest.server.service.BarService;
import com.fuhua.booknest.server.service.PostInteractionService;
import com.fuhua.booknest.server.service.UserStateCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class PostInteractionServiceImpl implements PostInteractionService {

    @Autowired
    private PostMapper postMapper;
    @Autowired
    private PostCommentMapper postCommentMapper;
    @Autowired
    private PostCommentLikeMapper postCommentLikeMapper;
    @Autowired
    private PostLikeMapper postLikeMapper;
    @Autowired
    private PostCollectMapper postCollectMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NotificationProducer notificationProducer;
    @Autowired
    private BarService barService;
    @Autowired
    private AiBotMapper aiBotMapper;
    @Autowired
    private AiBotReplyService aiBotReplyService;
    @Autowired
    private UserStateCacheService userStateCache;
    @Autowired
    private CountBuffer countBuffer;

    @Override
    @Transactional
    public CommentVO publishComment(String postId, CommentPublishDTO dto) {
        // 校验评论内容非空
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new BaseException("评论内容不能为空");
        }
        // 校验帖子存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }

        // 楼中楼回复：规范化 replyId，并校验被回复评论存在、且确实属于本帖
        String replyId = null;
        PostComment replyTarget = null;
        if (dto.getReplyId() != null && !dto.getReplyId().trim().isEmpty()) {
            replyId = dto.getReplyId().trim();
            replyTarget = postCommentMapper.selectById(replyId);
            if (replyTarget == null) {
                throw new BaseException("回复的评论不存在");
            }
            // 跨帖回复会把通知链到别的帖子上、楼层也会错位，直接拒掉
            if (!postId.equals(replyTarget.getPostId())) {
                throw new BaseException("回复的评论不属于该帖子");
            }
        }

        // 插入评论
        PostComment comment = PostComment.builder()
                .id(UUID.randomUUID().toString())
                .postId(postId)
                .userId(BaseContext.getCurrentId())
                // 真人评论没有机器人身份；bot_id 由 AI 回复那条链路自己填
                .botId(null)
                .content(dto.getContent())
                .replyId(replyId)
                .likeCount(0)
                .createTime(LocalDateTime.now())
                .build();
        postCommentMapper.insert(comment);

        // 帖子评论数 +1：不直写 MySQL，而是记进 Redis 计数缓冲，由 CountFlushJob 定时批量落库。
        // 注册在 afterCommit 上 —— 事务一旦回滚，这条评论根本不存在，计数也就不该加，
        // 而 Redis 不参与事务、写进去退不回来。
        runAfterCommit(() -> countBuffer.addDelta(CountBuffer.CounterType.POST_COMMENT, postId, 1L));

        // ── 下面这些都不写「评论」本身，统一挪到事务提交之后执行 ──
        // 通知发送与吧内经验都是附加动作。放在事务内会有两个问题：
        // ① 通知走 MQ（网络 IO）、加经验要额外读改写 bar_member，都在拖长评论事务的持锁时间；
        // ② 通知一旦先于提交投出去，别人点进通知可能 404（评论还没提交）。
        // 快照必须在这里取好 —— afterCommit 虽然仍在请求线程上，但把「取什么」提前固化
        // 比依赖执行时机更稳（同 triggerAiBotsAfterCommit 的写法）。
        String commenterId = comment.getUserId();
        // replyTarget 在方法前段被赋值，不是 effectively final，lambda 里直接引用编译不过，
        // 这里取一份快照
        final PostComment replyTargetSnapshot = replyTarget;
        runAfterCommit(() -> {
            // 评论通知（自己评论自己的帖子不发通知）
            sendPostInteractionNotification(post, NotificationConstant.TYPE_COMMENT, "评论了你的帖子");
            // 回复通知：被回复的人单独收一条 TYPE_REPLY
            sendReplyNotification(post, replyTargetSnapshot, comment);
            // 吧内经验：在本吧发评论加经验（未关注该吧则不累计）
            addExpQuietly(post.getCategoryId(), commenterId, BarConstant.EXP_COMMENT);
        });

        // 评论区 AI 机器人：正文里 @ 到已过审的机器人就异步让它回复。
        // 必须等本事务提交之后再投递 —— 异步线程要按 reply_id 找这条评论，
        // 提交前它还不存在。
        triggerAiBotsAfterCommit(post, comment);

        return buildCommentVO(comment, null);
    }

    /**
     * 把动作注册到「当前事务提交成功之后」执行；没有活动事务时立即执行。
     *
     * <p><b>这是「慢活出事务」的统一入口。</b>放进来的活必须满足两条：
     * ① 不参与本次事务的原子性（失败了也不该回滚业务）；
     * ② 相对慢（网络 IO、额外的读写、或不可逆的副作用）。
     * 典型例子：MQ 投递、OSS 删除、吧内经验累加。</p>
     *
     * <p>用 {@code isSynchronizationActive()} 判断而不是 {@code isActualTransactionActive()}：
     * {@code registerSynchronization} 要求的正是「事务同步已激活」，前者才是对应条件。</p>
     *
     * <p><b>注意 afterCommit 不换线程</b> —— 仍在请求线程上执行，异常也不会回滚已提交的事务。
     * 所以注册前要把需要的数据取好（别在闭包里读 {@code BaseContext} 之外的请求态数据）。</p>
     *
     * @param action 提交成功后要执行的动作
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * 加吧内经验，失败只记日志。
     *
     * <p>调用点已经挪到事务提交之后，这里再抛异常只会变成用户侧的 500，
     * 而评论早就落库了 —— 经验没加上是次要问题，不能反过来报错给对方。</p>
     *
     * @param barId  吧ID
     * @param userId 用户ID
     * @param amount 经验增减量
     */
    private void addExpQuietly(String barId, String userId, int amount) {
        try {
            barService.addExp(barId, userId, amount);
        } catch (Exception e) {
            log.warn("吧内经验结算失败: barId={}, userId={}, amount={}", barId, userId, amount, e);
        }
    }

    /**
     * 事务提交后触发评论区 AI 机器人。
     *
     * <p>用 {@code afterCommit} 而不是直接调用：<br>
     * ① AI 回复要挂在 {@code comment} 这条刚插入的评论下面，提交前别的连接看不到它；<br>
     * ② 模型调用是几十秒级别的慢操作，绝不能让它把评论事务拖住。</p>
     *
     * @param post    帖子
     * @param comment 刚插入的评论
     */
    private void triggerAiBotsAfterCommit(Post post, PostComment comment) {
        String commenterId = comment.getUserId();
        String commenterName = resolveUserName(commenterId);
        Runnable trigger = () -> {
            try {
                aiBotReplyService.onCommentPublished(post, comment, commenterName);
            } catch (Exception e) {
                // 触发链路出问题只能记日志：评论本身已经成功落库，不能反过来报错给用户
                log.warn("AI 机器人触发失败: postId={}, commentId={}", post.getId(), comment.getId(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trigger.run();
                }
            });
        } else {
            trigger.run();
        }
    }

    /**
     * 取用户昵称（评论者 / 通知文案都用它）
     * @param userId 用户ID
     * @return 昵称，取不到时返回「用户」
     */
    private String resolveUserName(String userId) {
        if (userId == null) {
            return "用户";
        }
        User user = userMapper.getUserById(userId);
        return (user == null || user.getUsername() == null) ? "用户" : user.getUsername();
    }

    @Override
    public List<CommentVO> listComments(String postId) {
        // 校验帖子存在
        if (postMapper.selectById(postId) == null) {
            throw new BaseException("帖子不存在");
        }

        String currentId = BaseContext.getCurrentId();
        List<PostComment> comments = postCommentMapper.listByPostId(postId);
        List<CommentVO> result = new ArrayList<>();
        if (comments == null || comments.isEmpty()) {
            return result;
        }

        // ── 批量预取，把「每条评论 4~5 次查询」压成常数次 ──
        // 原先 buildCommentVO 在循环里逐条查机器人/用户/父评论/点赞态，
        // 一条帖子有天量评论时查询次数是 1 + 5N。这里先把整个列表需要的外部数据
        // 一次性捞齐，再纯内存组装。
        CommentAssembly ctx = prefetchForComments(comments, currentId);

        for (PostComment comment : comments) {
            result.add(buildCommentVO(comment, currentId, ctx));
        }
        // 评论点赞数同样走 Redis 缓冲：整批一次 HMGET 把未落库的增量补回来。
        // 少了这一步，刚点的赞要等落库（默认 30 秒）才显示。
        applyPendingCommentLikes(result);
        return result;
    }

    /**
     * 给一批评论 VO 补上「Redis 计数缓冲里还没落库的点赞增量」。
     *
     * <p>评论点赞不再直写 MySQL（见 {@code CountBuffer}），所以
     * {@code post_comment.like_count} 是滞后的，展示值必须取「行值 + 缓冲增量」。</p>
     *
     * <p>并发点赞撞唯一键时不算增量（见 {@link #toggleCommentLike}），
     * 所以这里补出来的值与点赞接口返回的值口径一致。</p>
     *
     * @param vos 评论 VO 列表（就地修改计数）
     */
    private void applyPendingCommentLikes(List<CommentVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<String> commentIds = new ArrayList<>(vos.size());
        for (CommentVO vo : vos) {
            if (vo != null && vo.getId() != null) {
                commentIds.add(vo.getId());
            }
        }
        if (commentIds.isEmpty()) {
            return;
        }
        Map<String, Long> deltas =
                countBuffer.pendingBatch(CountBuffer.CounterType.COMMENT_LIKE, commentIds);
        for (CommentVO vo : vos) {
            if (vo == null) {
                continue;
            }
            Long delta = deltas.get(vo.getId());
            if (delta != null && delta != 0L) {
                vo.setLikeCount(CountBuffer.merge(vo.getLikeCount(), delta));
            }
        }
    }

    /**
     * 评论 VO 组装所需的外部数据快照（一次性查好，避免循环内查询）。
     */
    private static final class CommentAssembly {
        /** commentId -> 该评论是否被当前用户点赞 */
        Set<String> likedCommentIds = Set.of();
        /** 评论者/被回复者涉及的用户：userId -> User */
        Map<String, User> users = Map.of();
        /** 评论者/被回复者涉及的机器人：botId -> AiBot */
        Map<String, AiBot> bots = Map.of();
        /** replyId -> 父评论实体（用于取「回复 @某某」的展示名） */
        Map<String, PostComment> parentComments = Map.of();
    }

    /**
     * 为一个评论列表批量预取组装所需的数据。
     *
     * @param comments  评论列表
     * @param currentId 当前用户ID（可空）
     */
    private CommentAssembly prefetchForComments(List<PostComment> comments, String currentId) {
        CommentAssembly ctx = new CommentAssembly();

        // 1) 当前用户在这批评论里点赞了哪些 —— 走 Redis 状态缓存，一次批量判断
        Set<String> commentIds = new LinkedHashSet<>();
        for (PostComment c : comments) {
            if (c.getId() != null) {
                commentIds.add(c.getId());
            }
        }
        ctx.likedCommentIds = userStateCache.filterLikedComments(currentId, commentIds);

        // 2) 需要展示的「人」：直接评论者 + 被回复的父评论作者，都要查名字/头像
        Set<String> userIds = new HashSet<>();
        Set<String> botIds = new HashSet<>();
        Set<String> parentCommentIds = new HashSet<>();

        for (PostComment c : comments) {
            collectSpeaker(c, userIds, botIds);
            if (c.getReplyId() != null) {
                parentCommentIds.add(c.getReplyId());
            }
        }

        // 3) 这批评论的父评论可能不在 comments 里（分页/裁剪场景），必须单独捞。
        //    一次 in 查询取代「循环里逐条 selectById」—— 一页里若有一半是回复，
        //    原本就是十几次单条往返。
        if (!parentCommentIds.isEmpty()) {
            Map<String, PostComment> parents = new HashMap<>();
            List<PostComment> parentList = postCommentMapper.selectByIds(new ArrayList<>(parentCommentIds));
            if (parentList != null) {
                for (PostComment parent : parentList) {
                    if (parent == null || parent.getId() == null) {
                        continue;
                    }
                    parents.put(parent.getId(), parent);
                    // 父评论的作者也是要展示的「人」，一并纳入批量查询
                    collectSpeaker(parent, userIds, botIds);
                }
            }
            ctx.parentComments = parents;
        }

        // 4) 批量查用户与机器人。
        //    ⚠️ 原注释写着「一次 IN 查询」，实现却是循环里逐条 getXxxById —— 注释与代码不符，
        //    这里补成真的批量：userIds 走 UserMapper.mapByIds，botIds 走 AiBotMapper.selectByIds。
        if (!userIds.isEmpty()) {
            ctx.users = userMapper.mapByIds(userIds);
        }
        if (!botIds.isEmpty()) {
            Map<String, AiBot> bots = new HashMap<>();
            List<AiBot> botList = aiBotMapper.selectByIds(new ArrayList<>(botIds));
            if (botList != null) {
                for (AiBot bot : botList) {
                    if (bot != null && bot.getId() != null) {
                        bots.put(bot.getId(), bot);
                    }
                }
            }
            ctx.bots = bots;
        }
        return ctx;
    }

    /** 把一条评论的「发言者」纳入待查集合（AI 回复取机器人，真人取用户） */
    private void collectSpeaker(PostComment comment, Set<String> userIds, Set<String> botIds) {
        if (comment.getBotId() != null) {
            botIds.add(comment.getBotId());
        } else if (comment.getUserId() != null) {
            userIds.add(comment.getUserId());
        }
    }

    /** 从预取快照里取评论者展示信息 */
    private String[] speakerOf(PostComment comment, CommentAssembly ctx) {
        if (comment.getBotId() != null) {
            AiBot bot = ctx.bots.get(comment.getBotId());
            // 机器人被删了但回复还在（历史数据）：给个兜底名字，别显示成「匿名书友」
            return bot == null
                    ? new String[]{"AI 机器人", null, null}
                    : new String[]{bot.getName(), bot.getAvatar(), bot.getOwnerId()};
        }
        if (comment.getUserId() != null) {
            User user = ctx.users.get(comment.getUserId());
            if (user != null) {
                return new String[]{user.getUsername(), user.getAvatar(), null};
            }
        }
        return new String[]{null, null, null};
    }

    @Override
    @Transactional
    public void deleteComment(String commentId) {
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BaseException("评论不存在");
        }

        // 权限校验：评论作者本人、帖子作者本人，或 AI 机器人的创建者可删除
        String currentId = BaseContext.getCurrentId();
        boolean isCommentAuthor = currentId != null && currentId.equals(comment.getUserId());
        boolean isPostAuthor = false;
        Post post = postMapper.selectById(comment.getPostId());
        if (post != null && currentId != null && currentId.equals(post.getUserId())) {
            isPostAuthor = true;
        }
        // AI 回复的「作者」是机器人，它的 user_id 为空，光靠 isCommentAuthor 判不出来，
        // 得顺着 bot_id 找到创建者 —— 否则用户没法制止自己机器人说错话
        boolean isBotOwner = false;
        if (comment.getBotId() != null && currentId != null) {
            AiBot bot = aiBotMapper.selectById(comment.getBotId());
            isBotOwner = bot != null && currentId.equals(bot.getOwnerId());
        }
        if (!isCommentAuthor && !isPostAuthor && !isBotOwner) {
            throw new BaseException("无权限操作");
        }

        // 递归收集所有楼中楼后代评论 ID
        List<String> replyIds = collectReplyIds(commentId);

        // 先删每个子回复的点赞，再删子回复
        for (String replyId : replyIds) {
            postCommentLikeMapper.deleteByCommentId(replyId);
            postCommentMapper.deleteById(replyId);
        }

        // 再删本评论的点赞与本评论
        postCommentLikeMapper.deleteByCommentId(commentId);
        postCommentMapper.deleteById(commentId);

        // 评论数按「本评论 + 所有后代回复」实际删除条数扣减：记一笔负数增量进 Redis 计数缓冲，
        // 由 CountFlushJob 定时落库。同样放在 afterCommit —— 事务回滚了评论还在，计数不该减。
        final int deletedComments = 1 + replyIds.size();
        runAfterCommit(() -> countBuffer.addDelta(
                CountBuffer.CounterType.POST_COMMENT, comment.getPostId(), -deletedComments));
    }

    /**
     * 递归收集某个评论的全部后代评论 ID（楼中楼的子回复及其更深层回复）
     * @param commentId 评论ID
     * @return 所有后代评论 ID 列表
     */
    private List<String> collectReplyIds(String commentId) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        collectReplyIdsRecursive(commentId, result, visited);
        return result;
    }

    /**
     * 递归遍历子回复（visited 防循环引用）
     * @param commentId 当前评论ID
     * @param result 结果集合
     * @param visited 已访问集合
     */
    private void collectReplyIdsRecursive(String commentId, List<String> result, Set<String> visited) {
        List<PostComment> children = postCommentMapper.listByReplyId(commentId);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (PostComment child : children) {
            if (child.getId() == null || visited.contains(child.getId())) {
                continue;
            }
            visited.add(child.getId());
            result.add(child.getId());
            collectReplyIdsRecursive(child.getId(), result, visited);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> toggleCommentLike(String commentId) {
        // 校验评论存在
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BaseException("评论不存在");
        }

        String userId = BaseContext.getCurrentId();
        boolean alreadyLiked = userStateCache.isCommentLiked(userId, commentId);

        // 本次是否真的改动了计数。并发撞唯一键时「行根本没插进去」，这一笔就不能算 ——
        // 否则它和另一个请求各自的增量会叠成 +2。
        final long countDelta;
        boolean liked;
        if (alreadyLiked) {
            // 同帖子点赞：只有真删掉了行才扣减，避免并发取消把计数减成负数
            countDelta = postCommentLikeMapper.deleteByCommentAndUser(commentId, userId) > 0 ? -1L : 0L;
            liked = false;
        } else {
            // 并发点赞会撞 uk_comment_user 唯一键，捕获后按幂等成功处理
            long applied = 0L;
            try {
                postCommentLikeMapper.insert(PostCommentLike.builder()
                        .id(UUID.randomUUID().toString())
                        .commentId(commentId)
                        .userId(userId)
                        .build());
                applied = 1L;
            } catch (DuplicateKeyException e) {
                log.debug("并发点赞评论，已幂等处理: commentId={}, userId={}", commentId, userId);
            }
            countDelta = applied;
            liked = true;
        }

        userStateCache.markCommentLiked(userId, commentId, liked);

        // 计数不直写 MySQL：记进 Redis 计数缓冲，由 CountFlushJob 定时落库。
        // 注册在 afterCommit —— 事务回滚时点赞行不存在，这笔增量也不该留下。
        if (countDelta != 0L) {
            runAfterCommit(() -> countBuffer.addDelta(
                    CountBuffer.CounterType.COMMENT_LIKE, commentId, countDelta));
        }

        // 返回给前端的计数 = 行里的值 + 缓冲里未落库的增量 + 本次这一笔。
        // 本次增量此刻还没进缓冲（要等事务提交后才写），所以必须显式补上 ——
        // 前端是拿这个返回值直接覆盖本地计数的，少了它就会出现「点赞成功但数字没动」。
        PostComment latest = postCommentMapper.selectById(commentId);
        int likeCount = CountBuffer.merge(latest == null ? null : latest.getLikeCount(),
                countBuffer.pending(CountBuffer.CounterType.COMMENT_LIKE, commentId)) + (int) countDelta;

        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", likeCount);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> togglePostLike(String postId) {
        // 校验帖子存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }
        // 未过审 / 已下架的帖子不允许点赞：作者自己刚发完就能在详情页看到预览，
        // 但这时候帖子还没进入公开流通，攒赞会把审核流程架空。
        requirePublic(post);

        String userId = BaseContext.getCurrentId();
        // 状态判断改走状态缓存（未命中会回源查库），避免每次点赞都多一次 DB 查询
        boolean alreadyLiked = userStateCache.isPostLiked(userId, postId);

        // 本次是否真的改动了计数。并发撞唯一键时行根本没插进去，这一笔就不能算，
        // 否则它和另一个请求各自的增量会叠成 +2。
        final long countDelta;
        boolean liked;
        if (alreadyLiked) {
            // 已点赞 → 取消点赞。
            // 只有 DELETE 真正删掉了行（影响行数 > 0）才扣减计数：
            // 并发下两个「取消」都读到已点赞，若都扣减就会把 like_count 减成负数。
            countDelta = postLikeMapper.deleteByPostAndUser(postId, userId) > 0 ? -1L : 0L;
            liked = false;
        } else {
            // 未点赞 → 点赞。
            // 并发下两个请求可能都判定为「未点赞」，第二个 insert 会撞 uk_post_user 唯一键。
            // 捕获后按「已点赞」处理（幂等），而不是把场景当错误抛给用户。
            long applied = 0L;
            try {
                postLikeMapper.insert(PostLike.builder()
                        .id(UUID.randomUUID().toString())
                        .postId(postId)
                        .userId(userId)
                        .build());
                applied = 1L;
            } catch (DuplicateKeyException e) {
                // 说明另一个并发请求已经点赞成功，计数也已由它加上，这里什么都不做
                log.debug("并发点赞，已幂等处理: postId={}, userId={}", postId, userId);
            }
            countDelta = applied;
            liked = true;
        }

        // 同步状态缓存（缓存未加载时是空操作，下次读会从 DB 重建）
        userStateCache.markPostLiked(userId, postId, liked);

        // 计数不直写 MySQL：记进 Redis 计数缓冲，由 CountFlushJob 定时批量落库。
        // 同样放在 afterCommit —— 事务回滚了点赞行就不在，这笔增量也不该留下。
        if (countDelta != 0L) {
            runAfterCommit(() -> countBuffer.addDelta(
                    CountBuffer.CounterType.POST_LIKE, postId, countDelta));
        }

        // ── 慢活出事务：通知发送（MQ 网络 IO）与吧内经验（额外读改写 bar_member）
        // 都不参与点赞本身的原子性，挪到提交之后执行。点赞是全站频率最高的写操作之一，
        // 让它在事务里顺带做这两件事，等于把这个热点操作的锁持有时间拉长一截。
        final boolean finalLiked = liked;
        final String barId = post.getCategoryId();
        runAfterCommit(() -> {
            // 本次为点赞（非取消）时发送点赞通知
            if (finalLiked) {
                sendPostInteractionNotification(post, NotificationConstant.TYPE_LIKE, "赞了你的帖子");
            }
            // 吧内经验：点赞加、取消点赞扣回（扣回不返还当日额度，避免反复点赞刷经验）
            addExpQuietly(barId, userId, finalLiked ? BarConstant.EXP_LIKE : -BarConstant.EXP_LIKE);
        });

        // 返回给前端的计数 = 行里的值 + 缓冲里未落库的增量 + 本次这一笔。
        // 本次增量要等事务提交后才写进缓冲，所以这里必须显式补上 ——
        // 前端拿这个返回值直接覆盖本地计数，少了它就会「点赞成功但数字没动」。
        Post latest = postMapper.selectById(postId);
        int likeCount = CountBuffer.merge(latest == null ? null : latest.getLikeCount(),
                countBuffer.pending(CountBuffer.CounterType.POST_LIKE, postId)) + (int) countDelta;

        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", likeCount);
        return result;
    }

    @Override
    public boolean isPostLiked(String postId) {
        return userStateCache.isPostLiked(BaseContext.getCurrentId(), postId);
    }

    @Override
    @Transactional
    public Map<String, Object> togglePostCollect(String postId) {
        // 校验帖子存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }
        // 同点赞：未过审 / 已下架的帖子不允许收藏
        requirePublic(post);

        String userId = BaseContext.getCurrentId();
        boolean alreadyCollected = userStateCache.isPostCollected(userId, postId);

        // 本次是否真的改动了计数（并发撞唯一键时行没插进去，这一笔不该算）
        final long countDelta;
        boolean collected;
        if (alreadyCollected) {
            // 同点赞：按影响行数决定是否扣减
            countDelta = postCollectMapper.deleteByPostAndUser(postId, userId) > 0 ? -1L : 0L;
            collected = false;
        } else {
            // 并发收藏会撞 uk_post_user 唯一键，捕获后幂等处理
            long applied = 0L;
            try {
                postCollectMapper.insert(PostCollect.builder()
                        .id(UUID.randomUUID().toString())
                        .postId(postId)
                        .userId(userId)
                        .build());
                applied = 1L;
            } catch (DuplicateKeyException e) {
                log.debug("并发收藏，已幂等处理: postId={}, userId={}", postId, userId);
            }
            countDelta = applied;
            collected = true;
        }

        userStateCache.markPostCollected(userId, postId, collected);

        // 计数不直写 MySQL：记进 Redis 计数缓冲，由 CountFlushJob 定时落库（同点赞）
        if (countDelta != 0L) {
            runAfterCommit(() -> countBuffer.addDelta(
                    CountBuffer.CounterType.POST_COLLECT, postId, countDelta));
        }

        // 返回给前端的计数 = 行里的值 + 缓冲里未落库的增量 + 本次这一笔
        Post latest = postMapper.selectById(postId);
        int collectCount = CountBuffer.merge(latest == null ? null : latest.getCollectCount(),
                countBuffer.pending(CountBuffer.CounterType.POST_COLLECT, postId)) + (int) countDelta;

        Map<String, Object> result = new HashMap<>();
        result.put("collected", collected);
        result.put("collectCount", collectCount);
        return result;
    }

    @Override
    public boolean isPostCollected(String postId) {
        return userStateCache.isPostCollected(BaseContext.getCurrentId(), postId);
    }

    /**
     * 互动前置校验：只有「已过审 + 已上架」的帖子才允许点赞 / 收藏。
     * <p>详情页对作者本人放开预览（能看到自己刚发的待审帖），
     * 所以前端按钮会置灰，但服务端必须再拦一次，否则直接打接口照样能赞。</p>
     */
    private void requirePublic(Post post) {
        if (!PostStatusConstant.AUDIT_APPROVED.equals(post.getAuditStatus())
                || !PostStatusConstant.STATUS_PUBLISHED.equals(post.getStatus())) {
            throw new BaseException("帖子尚未通过审核，暂不支持点赞或收藏");
        }
    }

    /**
     * 发送帖子互动通知（点赞/评论），自己对自己操作不发送
     * @param post 帖子实体
     * @param type 通知类型
     * @param verb 动作文案（如 "评论了你的帖子"）
     */
    private void sendPostInteractionNotification(Post post, String type, String verb) {
        String currentId = BaseContext.getCurrentId();
        if (currentId == null || currentId.equals(post.getUserId())) {
            return;
        }
        User currentUser = userMapper.getUserById(currentId);
        String userName = (currentUser == null || currentUser.getUsername() == null) ? "用户" : currentUser.getUsername();
        String content = userName + " " + verb + "《" + post.getTitle() + "》";
        notificationProducer.sendNotification(post.getUserId(), type, content, post.getId());
    }

    /**
     * 组装评论 VO（填充评论者信息、被回复用户名与当前用户点赞状态）
     *
     * <p>AI 机器人回复也走这里：它的 user_id 为空、bot_id 有值，
     * 于是昵称/头像换成机器人的，并额外带出 botId 与 botOwnerId ——
     * 前端据此渲染「AI」徽标、并判断当前用户能不能删这条回复。</p>
     *
     * @param comment 评论实体
     * @param currentId 当前用户ID（可空，未登录时点赞状态为 false）
     * @return 评论 VO
     */
    private CommentVO buildCommentVO(PostComment comment, String currentId) {
        // 单条场景（发布评论后立即回显）：没有批量上下文，就地构造一个只含这条评论的快照
        return buildCommentVO(comment, currentId, prefetchForComments(List.of(comment), currentId));
    }

    /**
     * 组装评论 VO（使用预取好的外部数据，循环内零查询）。
     *
     * <p>AI 机器人回复也走这里：它的 user_id 为空、bot_id 有值，
     * 于是昵称/头像换成机器人的，并额外带出 botId 与 botOwnerId ——
     * 前端据此渲染「AI」徽标、并判断当前用户能不能删这条回复。</p>
     *
     * @param comment   评论实体
     * @param currentId 当前用户ID（可空，未登录时点赞状态为 false）
     * @param ctx       批量预取的外部数据
     */
    private CommentVO buildCommentVO(PostComment comment, String currentId, CommentAssembly ctx) {
        // 评论者信息
        String[] speaker = speakerOf(comment, ctx);
        String userName = speaker[0];
        String userAvatar = speaker[1];
        String botOwnerId = speaker[2];

        // 被回复用户名（顶级评论为 null）
        String replyToUserName = null;
        if (comment.getReplyId() != null) {
            PostComment parentComment = ctx.parentComments.get(comment.getReplyId());
            if (parentComment != null) {
                replyToUserName = resolveReplyToName(parentComment, ctx);
            }
        }

        // 当前用户是否已点赞该评论（未登录则 false）—— 来自 Redis 状态缓存的批量结果
        boolean liked = currentId != null && ctx.likedCommentIds.contains(comment.getId());

        return CommentVO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .userId(comment.getUserId())
                .userName(userName)
                .userAvatar(userAvatar)
                .botId(comment.getBotId())
                .botOwnerId(botOwnerId)
                .content(comment.getContent())
                .replyId(comment.getReplyId())
                .replyToUserName(replyToUserName)
                .likeCount(comment.getLikeCount())
                .createTime(comment.getCreateTime())
                .liked(liked)
                .build();
    }

    /**
     * 「回复 @某某」里的那个某某。
     * 父级评论可能是 AI 回复（user_id 为空），此时取机器人名。
     *
     * @param parentComment 被回复的评论
     * @return 展示名，取不到返回 null
     */
    /**
     * 「回复 @某某」里的那个某某。
     * 父级评论可能是 AI 回复（user_id 为空），此时取机器人名。
     *
     * @param parentComment 被回复的评论
     * @param ctx           批量预取的外部数据（用户与机器人都在里面）
     * @return 展示名，取不到返回 null
     */
    private String resolveReplyToName(PostComment parentComment, CommentAssembly ctx) {
        if (parentComment.getBotId() != null) {
            AiBot bot = ctx.bots.get(parentComment.getBotId());
            return bot == null ? "AI 机器人" : bot.getName();
        }
        if (parentComment.getUserId() == null) {
            return null;
        }
        User user = ctx.users.get(parentComment.getUserId());
        return user == null ? null : user.getUsername();
    }

    /** 回复通知里正文摘要的字数上限（通知表 content 是 VARCHAR(500)，但列表里太长不好看） */
    private static final int REPLY_EXCERPT_MAX = 40;

    /**
     * 回复某条评论时，单独通知「被回复的那个人」。
     * <p>三种情况不发：</p>
     * <ul>
     *   <li>顶级评论（没有 replyTarget）；</li>
     *   <li>自己回复自己的评论；</li>
     *   <li>被回复的人就是帖子作者 —— 上面 {@code TYPE_COMMENT} 那条「评论了你的帖子」已经发过了，
     *       再发一条就是同一条回复给同一个人推两条通知。</li>
     * </ul>
     *
     * @param post 帖子实体（sourceId 用帖子 ID，点通知能跳回详情页）
     * @param replyTarget 被回复的那条评论（可为空）
     * @param newComment 本次新发的回复（摘要取它的正文，anchorId 取它的 ID 供前端定位）
     */
    private void sendReplyNotification(Post post, PostComment replyTarget, PostComment newComment) {
        if (replyTarget == null) {
            return;
        }
        String currentId = BaseContext.getCurrentId();
        String receiverId = replyTarget.getUserId();
        if (receiverId == null
                || receiverId.equals(currentId)
                || receiverId.equals(post.getUserId())) {
            return;
        }
        User currentUser = userMapper.getUserById(currentId);
        String userName = (currentUser == null || currentUser.getUsername() == null) ? "用户" : currentUser.getUsername();
        String content = userName + " 回复了你的评论：" + excerpt(newComment.getContent());
        // sourceId = 帖子 ID（落点页面），anchorId = 新回复自己的 ID（落点位置）
        notificationProducer.sendNotification(
                receiverId, NotificationConstant.TYPE_REPLY, content, post.getId(), newComment.getId());
    }

    /**
     * 生成通知摘要：把换行/连续空白压成单个空格，超长截断
     * @param text 原始正文
     * @return 单行摘要
     */
    private String excerpt(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        if (flat.length() <= REPLY_EXCERPT_MAX) {
            return flat;
        }
        return flat.substring(0, REPLY_EXCERPT_MAX) + "…";
    }
}
