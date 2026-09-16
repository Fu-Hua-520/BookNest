package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.BarConstant;
import com.fuhua.booknest.common.constant.PostSortConstant;
import com.fuhua.booknest.common.constant.PostStatusConstant;
import com.fuhua.booknest.common.constant.PostTypeConstant;
import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.dto.PostPublishDTO;
import com.fuhua.booknest.pojo.dto.PostUpdateDTO;
import com.fuhua.booknest.pojo.entity.Book;
import com.fuhua.booknest.pojo.entity.Category;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.entity.PostTag;
import com.fuhua.booknest.pojo.entity.Tag;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.entity.UserBrowseHistory;
import com.fuhua.booknest.pojo.vo.PostDetailVO;
import com.fuhua.booknest.pojo.vo.PostVO;
import com.fuhua.booknest.server.common.CountBuffer;
import com.fuhua.booknest.server.common.RedisCacheHelper;
import com.fuhua.booknest.server.mapper.BookMapper;
import com.fuhua.booknest.server.mapper.CategoryMapper;
import com.fuhua.booknest.server.mapper.PostCollectMapper;
import com.fuhua.booknest.server.mapper.PostCommentLikeMapper;
import com.fuhua.booknest.server.mapper.PostCommentMapper;
import com.fuhua.booknest.server.mapper.PostLikeMapper;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.mapper.PostTagMapper;
import com.fuhua.booknest.server.mapper.PostTagMapper.PostTagRow;
import com.fuhua.booknest.server.mapper.TagMapper;
import com.fuhua.booknest.server.mapper.UserBrowseHistoryMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.BarService;
import com.fuhua.booknest.server.service.PostService;
import com.fuhua.booknest.server.service.UserStateCacheService;
import com.fuhua.booknest.server.utils.AliOssUtil;
import com.github.pagehelper.PageHelper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class PostServiceImpl implements PostService {

    // OSS 对象名中的日期格式（yyyy/MM/dd）
    private static final DateTimeFormatter OSS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

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
    private PostTagMapper postTagMapper;
    @Autowired
    private TagMapper tagMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserBrowseHistoryMapper userBrowseHistoryMapper;
    @Autowired
    private AliOssUtil aliOssUtil;
    @Autowired
    private BarService barService;
    @Autowired
    private RedisCacheHelper cache;
    @Autowired
    private CountBuffer countBuffer;
    @Autowired
    private UserStateCacheService userStateCache;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 显式事务模板。
     *
     * <p><b>为什么需要它：</b>发帖 / 编辑要把 OSS 上传挪到事务之外，就得把
     * 「上传」和「写库」拆成两个方法。拆完之后写库那个方法如果加 {@code @Transactional}，
     * 同类内部自调用不走 Spring 代理，注解会被<b>静默忽略</b> —— 事务压根没开，
     * 而且编译期毫无提示。用模板显式圈出事务边界，既不依赖代理，也让边界一眼可辨。</p>
     *
     * <p>与 {@code AiBotReplyServiceImpl.txTemplate} 是同一套写法。</p>
     */
    private TransactionTemplate txTemplate;

    @PostConstruct
    void initTxTemplate() {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 发帖。
     *
     * <p><b>OSS 上传被刻意留在事务外：</b>正文 Markdown 上传 OSS 是一次几十到几百毫秒的
     * 网络往返（详见 {@link #uploadContentOutsideTransaction}）。原实现把 upload 放在
     * {@code @Transactional} 方法体内，意味着整个上传期间都占着一条数据库连接、
     * 事务也一直开着 —— 而上传过程根本不碰数据库。一旦 OSS 卡顿，连接池会被这类
     * 「等网络」的事务占满，正常查询反而拿不到连接。
     * 所以顺序调整成：<b>先传 OSS 拿到 objectName → 再开事务写库</b>。</p>
     *
     * <p>代价与取舍：上传成功但写库失败时会留下一个 OSS 孤儿对象（DB 里没有任何行指向它）。
     * 这是可接受的 —— 孤儿对象只占存储、不影响任何读路径，而反过来「先写库后上传」
     * 一旦上传失败，就会在库中留下一条 contentUrl 指向不存在对象的帖子，
     * 用户点进去直接报错。<b>孤儿对象是脏数据，坏指针是故障</b>，两者不对称，
     * 所以选择前者。清理这类孤儿交给后续的 OSS 生命周期规则或对账任务。</p>
     */
    @Override
    public PostVO publishPost(PostPublishDTO dto) {
        // 校验必填字段（@Valid 之外再兜底）
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BaseException("标题不能为空");
        }
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new BaseException("正文不能为空");
        }
        if (dto.getCategoryId() == null || dto.getCategoryId().trim().isEmpty()) {
            throw new BaseException("分类不能为空");
        }

        // 正文 Markdown 上传 OSS，objectName 存入 contentUrl。
        // 放在事务之前 —— 见方法注释，上传是纯网络 IO，不该占着事务与连接。
        String objectName = uploadContentOutsideTransaction(dto.getContent());

        // 写库走显式事务模板。这里不能给一个 private/public 的 doPublishPost 加 @Transactional：
        // 自调用不走 Spring 代理，注解会被静默忽略，事务根本不会开启。
        // 与 AiBotReplyServiceImpl 的 txTemplate 是同一套写法。
        return txTemplate.execute(status -> doPublishPost(dto, objectName));
    }

    /**
     * 发帖的数据库部分（真正需要事务的只有这一段）。
     * 正文已由调用方上传完毕，这里只负责写库。
     *
     * <p>不加 {@code @Transactional}：它由 {@link #publishPost} 通过 {@code txTemplate}
     * 调用，注解在这里既无效也容易误导（自调用不走代理）。</p>
     *
     * @param dto        发帖参数
     * @param objectName OSS 正文对象名
     * @return 帖子 VO
     */
    private PostVO doPublishPost(PostPublishDTO dto, String objectName) {
        LocalDateTime now = LocalDateTime.now();
        Post post = Post.builder()
                .id(UUID.randomUUID().toString())
                .userId(BaseContext.getCurrentId())
                .bookId(dto.getBookId())
                .title(dto.getTitle())
                .summary(dto.getSummary())
                .contentUrl(objectName)
                .coverImage(dto.getCoverImage())
                .categoryId(dto.getCategoryId())
                .postType(PostTypeConstant.normalize(dto.getPostType()))
                .viewCount(0L)
                .likeCount(0)
                .commentCount(0)
                .collectCount(0)
                .status(PostStatusConstant.STATUS_PUBLISHED)
                .auditStatus(PostStatusConstant.AUDIT_PENDING)
                .isTop(0)
                .createTime(now)
                .updateTime(now)
                .publishTime(now)
                .build();
        postMapper.insert(post);

        // 保存标签关联
        savePostTags(post.getId(), dto.getTagIds());

        // 吧内经验：在本吧发帖加经验（未关注该吧则不累计）
        barService.addExp(post.getCategoryId(), post.getUserId(), BarConstant.EXP_PUBLISH_POST);

        // 新帖要立刻出现在列表首屏，所以不能等 TTL 自然过期。
        // 注意：此刻事务尚未提交（@Transactional 语义），但列表缓存失效是「宁早勿晚」的
        // 动作 —— 早清只会让并发请求多查一次库，晚清才会让人看到脏数据。
        // 新帖 auditStatus=PENDING，本就不在用户端列表里，所以这里的清理实际是为
        // 管理端审核通过后立即上首页做准备（审核走 admin 的 PostAdminService，见其内部失效）。
        evictPostListCache();

        return toPostVO(post);
    }

    /**
     * 帖子详情（缓存优先）。
     *
     * <h3>为什么详情值得缓存</h3>
     * 详情接口有两处昂贵开销，且都与「谁在看」无关：
     * <ol>
     *   <li><b>正文要下载 OSS</b> —— 正文 Markdown 不在数据库里，
     *       {@code post.content_url} 只是一个对象名，每次打开详情都是一次
     *       几十到几百毫秒的对象存储往返；</li>
     *   <li><b>元数据要 4 次批量关联查询</b> —— 分类名、书名、作者、标签。</li>
     * </ol>
     * 这两块正是缓存的内容（{@link RedisConstant#POST_DETAIL}）。
     *
     * <h3>为什么命中时仍然查一次 post 行</h3>
     * 因为「快」不能以「可能看错」为代价，而权威数据只有数据库有：
     * <ul>
     *   <li><b>鉴权走当次的行，不走缓存</b>。详情页的可见性规则是「已过审 + 已上架，
     *       或你是作者，或你是本吧吧主/管理员」。若改成信缓存里的 auditStatus/status，
     *       那么只要有一次 evict 丢失（Redis 抖动、删键失败），
     *       已下架 / 未过审的帖子就会被漏给所有人 —— 这是安全问题，不是性能问题；</li>
     *   <li><b>易变字段走当次的行</b>。置顶、上下架、审核状态会在编辑之外被随时改动，
     *       命中时一律用行值覆盖，于是它们永远不脏。</li>
     * </ul>
     * 主键查一行是百微秒量级，而这个请求本来就要写库（浏览历史 upsert），
     * 多这一次读换来的是「鉴权永远实时」。真正省掉的是 OSS 下载与关联查询。
     *
     * <h3>计数为什么要「行值 + 缓冲增量」</h3>
     * 浏览 / 点赞 / 评论 / 收藏这四个计数不再直写 MySQL，而是先在 Redis 里累加
     * （见 {@code CountBuffer}），由 {@code CountFlushJob} 定时批量落库 ——
     * 所以数据库里那几列是<b>滞后</b>的。展示值必须取
     * 「行里的值 + 缓冲里还没落库的增量」，否则会出现「刚点的赞要等半分钟才显示」
     * 「评论完回来评论数没变」这类问题。
     *
     * <h3>失效策略</h3>
     * 缓存里只有「正文 + 元数据」，而它们只在编辑、删除时改变：
     * <ul>
     *   <li>编辑 → {@link #doUpdatePost} 提交后清（见那里的注释）；</li>
     *   <li>删除 → {@code UserStateCacheServiceImpl.forgetPostEverywhere} 清；</li>
     *   <li>审核 / 置顶 / 上下架 / 吧务隐藏 → <b>不需要清</b>，读路径会用当次的行值盖掉；</li>
     *   <li>TTL 10 分钟兜住其余一切（分类改名、用户改名这类不改 post 表的动作）。</li>
     * </ul>
     */
    @Override
    public PostDetailVO getPostDetail(String postId) {
        String cacheKey = RedisConstant.POST_DETAIL + postId;
        String currentId = BaseContext.getCurrentId();

        // 命中的话，命中的是「正文 + 元数据」，不是鉴权结果
        PostDetailVO cached = cache.get(cacheKey);

        Post post = postMapper.selectById(postId);
        if (post == null) {
            // 帖子行都没了，缓存里那份必然是脏的，顺手清掉再报错
            cache.evict(cacheKey);
            throw new BaseException("帖子不存在");
        }

        // 访问控制：非作者本人需同时满足已过审且已发布，否则不可见。
        // 例外：本吧的吧主 / 管理员即使帖子已下架也能打开 ——
        // 不然他点完「隐藏」就被自己关在门外，连「恢复」都点不到。
        // ⚠️ 全部用刚查出的行判断，绝不用缓存里的 auditStatus / status（见方法注释）。
        boolean isAuthor = currentId != null && currentId.equals(post.getUserId());
        boolean isPublic = PostStatusConstant.AUDIT_APPROVED.equals(post.getAuditStatus())
                && PostStatusConstant.STATUS_PUBLISHED.equals(post.getStatus());
        if (!isAuthor && !isPublic && !barService.isBarManager(post.getCategoryId(), currentId)) {
            throw new BaseException("帖子不存在或审核未通过");
        }

        // 浏览量 +1：不再直写 MySQL，而是在 Redis 计数缓冲里累加，由 CountFlushJob 定时批量落库。
        // 一万次浏览从一万条单点 UPDATE 变成一条 update ... view_count = view_count + 10000。
        countBuffer.addDelta(CountBuffer.CounterType.POST_VIEW, postId, 1L);

        // 四个计数的「当前值」= 行里的值 + 缓冲里还没落库的增量。
        // 增量里已经含刚记下的这一次浏览，所以 viewCount 就是「含本次访问」的值。
        // ⚠️ 顺序不能反：必须先写下增量再读缓冲，否则本次浏览会被漏掉。
        CountBuffer.PostCountDelta delta = countBuffer.pendingPostCounts(List.of(postId))
                .getOrDefault(postId, CountBuffer.PostCountDelta.ZERO);
        long viewCount = CountBuffer.merge(post.getViewCount(), delta.view());
        int likeCount = CountBuffer.merge(post.getLikeCount(), delta.like());
        int commentCount = CountBuffer.merge(post.getCommentCount(), delta.comment());
        int collectCount = CountBuffer.merge(post.getCollectCount(), delta.collect());

        PostDetailVO detail;
        if (cached != null) {
            // ---- 命中：省掉 OSS 下载与关联查询，只用当次的行覆盖易变字段 ----
            // 覆盖范围 = 「会在编辑之外被改动的字段」：四个计数 + 置顶 + 上下架 + 审核。
            // 标题/摘要/封面/分类/标签这类元数据不覆盖 —— 它们只随编辑变化，
            // 而编辑路径会显式失效缓存（doUpdatePost）。
            detail = cached;
            detail.setViewCount(viewCount);
            detail.setLikeCount(likeCount);
            detail.setCommentCount(commentCount);
            detail.setCollectCount(collectCount);
            detail.setIsTop(post.getIsTop());
            detail.setStatus(post.getStatus());
            detail.setAuditStatus(post.getAuditStatus());
            detail.setAuditReason(post.getAuditReason());
        } else {
            // ---- 未命中：下载正文 + 组装元数据，然后写回缓存 ----
            if (post.getContentUrl() == null || post.getContentUrl().isEmpty()) {
                throw new BaseException("正文加载失败");
            }
            String content;
            try {
                content = aliOssUtil.downloadAsString(post.getContentUrl());
            } catch (BaseException e) {
                log.warn("帖子正文加载失败，postId: {}", postId);
                throw new BaseException("正文加载失败");
            }

            PostVO base = toPostVO(post);
            detail = PostDetailVO.builder()
                    .id(base.getId())
                    .title(base.getTitle())
                    .summary(base.getSummary())
                    .coverImage(base.getCoverImage())
                    .bookId(base.getBookId())
                    .bookTitle(base.getBookTitle())
                    .categoryId(base.getCategoryId())
                    .categoryName(base.getCategoryName())
                    .postType(base.getPostType())
                    .authorId(base.getAuthorId())
                    .authorName(base.getAuthorName())
                    .authorAvatar(base.getAuthorAvatar())
                    .tags(base.getTags())
                    .viewCount(base.getViewCount())
                    .likeCount(base.getLikeCount())
                    .commentCount(base.getCommentCount())
                    .collectCount(base.getCollectCount())
                    .isTop(base.getIsTop())
                    .publishTime(base.getPublishTime())
                    .content(content)
                    .auditStatus(post.getAuditStatus())
                    .auditReason(post.getAuditReason())
                    // 帖子状态（1-上架 0-草稿 3-下架）。前台据此显示「已被吧务隐藏」，
                    // 吧务本人则据此显示「恢复」按钮。
                    .status(post.getStatus())
                    .build();

            // 未过审 / 已下架的帖子也照缓存：鉴权每次都用当次的行判断，
            // 缓存不承担可见性职责，所以让作者与吧务刷新自己那篇也不必重复下载 OSS。
            cache.set(cacheKey, detail, RedisConstant.POST_DETAIL_TTL);
        }

        // 记录浏览历史（游客不记）。写历史失败不能拖垮详情页，内部单独 try 住。
        recordBrowseHistory(currentId, postId);

        return detail;
    }

    @Override
    public List<PostVO> listPosts(String categoryId, String tagId, String userId, Integer auditStatus,
                                  String sort, String postType, Integer page, Integer pageSize) {
        // 用户端强制只返回已过审帖子，防止客户端传入 0/2 枚举未过审/被拒帖子；
        // 管理端审核走 admin 的 PostAdminService 直调 mapper，不受此影响
        auditStatus = PostStatusConstant.AUDIT_APPROVED;
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        // 排序标识必须落在白名单内，否则直接回落「最新」。
        // 排序字段无法用 #{} 参数化（要拼进 order by），所以白名单是唯一防线。
        String normalizedSort = PostSortConstant.normalize(sort);

        // 类型同样走白名单，但要保留「不过滤」语义：
        // 首页主频道完全不传 postType（看全部），传进来的值才做归一化。
        String normalizedPostType = (postType == null || postType.trim().isEmpty())
                ? null
                : PostTypeConstant.normalize(postType);

        // 缓存键含全部分页与筛选维度。
        // userId 维度不能省：它是 PostMapper.list 的筛选条件（「看某人发的帖」），
        // 不是个性化状态 —— 真正跟「当前登录用户」绑定的点赞态不在 PostVO 里，故列表可跨用户共享。
        String cacheKey = RedisCacheHelper.key(RedisConstant.POST_LIST,
                categoryId, tagId, userId, normalizedSort, normalizedPostType, page, pageSize);
        @SuppressWarnings("unchecked")
        List<PostVO> cached = cache.get(cacheKey);
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        PageHelper.startPage(page, pageSize);
        // status 传已发布，只展示已发布帖子
        List<Post> posts = postMapper.list(categoryId, tagId, userId, auditStatus,
                PostStatusConstant.STATUS_PUBLISHED, normalizedSort, normalizedPostType);

        // 批量组装：分类/书/作者/标签都只查一轮（原来是循环内逐条查，一页 10 篇约 70 次查询）
        List<PostVO> result = toPostVOList(posts);
        // 只缓存「第 1 页」：首页占绝大多数流量，而翻到第 5 页的请求既少又长尾，
        // 缓存它们只会让键空间膨胀、失效时扫更多键，收益为负。
        if (page == 1 && !result.isEmpty()) {
            cache.set(cacheKey, result, RedisConstant.EXPIRE_1_MINUTE * 5);
        }
        return result;
    }

    @Override
    public List<PostVO> listMyCollectedPosts(Integer page, Integer pageSize) {
        String userId = BaseContext.getCurrentId();
        if (userId == null || userId.isEmpty()) {
            throw new BaseException("请先登录");
        }
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        if (pageSize > 50) {
            pageSize = 50;
        }

        // 先分页取「收藏了哪些帖子」，再批量回查帖子本体。
        // 这样能复用 toPostVO 的组装逻辑（分类名/书名/作者/标签），
        // 代价是两次查询，换取与列表页完全一致的字段语义。
        PageHelper.startPage(page, pageSize);
        List<String> postIds = postCollectMapper.listCollectedPostIdsByUser(userId);
        if (postIds == null || postIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Post> posts = postMapper.selectByIds(postIds);
        Map<String, Post> postMap = new HashMap<>();
        if (posts != null) {
            for (Post post : posts) {
                postMap.put(post.getId(), post);
            }
        }

        // selectByIds 用的是 in 查询，不保证返回顺序，这里按收藏顺序还原，
        // 否则「最近收藏」会变成随机顺序
        List<Post> ordered = new ArrayList<>(postIds.size());
        for (String postId : postIds) {
            Post post = postMap.get(postId);
            if (post != null) {
                ordered.add(post);
            }
        }
        return toPostVOList(ordered);
    }

    @Override
    public List<PostVO> searchPosts(String keyword, int limit) {
        // 关键词为空时直接返回空列表，不报错
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return toPostVOList(postMapper.searchByKeyword(keyword, limit));
    }

    /**
     * 编辑帖子。
     *
     * <p><b>慢活出事务：</b>这里有两处 OSS 网络往返，都必须挪出事务边界 ——</p>
     * <ul>
     *   <li><b>上传新正文</b>：纯网络 IO，不碰数据库，放在 {@code @Transactional} 里
     *       等于开着事务等 OSS 响应，白占一条连接；</li>
     *   <li><b>删除旧正文</b>：它是「新正文已经写库成功」之后才该发生的事，
     *       而且是<b>不可逆</b>的。原先放在事务内，一旦后面的
     *       {@code postTagMapper.deleteByPostId} 或标签重建失败导致整个事务回滚，
     *       数据库里 {@code content_url} 会回滚回旧对象名，可旧对象<b>已经被删掉了</b> ——
     *       帖子正文彻底丢失，且无法恢复。改成 afterCommit 之后，
     *       只有事务真正提交成功才会去删旧对象，回滚路径上旧正文完好无损。</li>
     * </ul>
     *
     * <p>注意提交顺序上的取舍：新正文是<b>先传后写库</b>（同发帖），失败会留孤儿对象；
     * 旧正文是<b>提交后才删</b>，回滚时不会误删。两个方向都偏向「宁留垃圾，不失数据」。</p>
     */
    @Override
    public void updatePost(String postId, PostUpdateDTO dto) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }
        String currentId = BaseContext.getCurrentId();
        if (currentId == null || !currentId.equals(post.getUserId())) {
            throw new BaseException("无权限操作");
        }

        // 正文重新上传 OSS，得到新 objectName（事务外：纯网络 IO）
        String oldObjectName = post.getContentUrl();
        String newObjectName = uploadContentOutsideTransaction(dto.getContent());

        // 同发帖：写库走显式事务模板。旧正文的删除注册在这个事务的 afterCommit 上，
        // 所以它必须由 txTemplate 开启的事务来承载 —— 见 doUpdatePost。
        txTemplate.executeWithoutResult(status -> doUpdatePost(post, dto, oldObjectName, newObjectName));
    }

    /**
     * 编辑帖子的数据库部分（事务边界内），并在事务提交后才清理旧正文。
     *
     * <p>旧正文的删除注册在 {@code afterCommit} 上，而不是在方法末尾直接调用 ——
     * 见 {@link #updatePost} 的注释：事务内删 OSS 对象，回滚后会留下指向空对象的帖子。</p>
     *
     * <p>不加 {@code @Transactional}：由 {@link #updatePost} 通过 {@code txTemplate}
     * 调用，注解在自调用路径上无效。</p>
     */
    private void doUpdatePost(Post post, PostUpdateDTO dto, String oldObjectName, String newObjectName) {
        String postId = post.getId();

        // 更新字段：编辑后回退审核状态为待审核，清空审核原因与审核时间，重新进入审核流程
        post.setTitle(dto.getTitle());
        post.setSummary(dto.getSummary());
        post.setContentUrl(newObjectName);
        post.setBookId(dto.getBookId());
        post.setCategoryId(dto.getCategoryId());
        post.setPostType(PostTypeConstant.normalize(dto.getPostType()));
        post.setCoverImage(dto.getCoverImage());
        post.setAuditStatus(PostStatusConstant.AUDIT_PENDING);
        post.setAuditReason("");
        post.setAuditTime(null);
        post.setUpdateTime(LocalDateTime.now());
        postMapper.update(post);

        // 重建标签关联（简化处理，不做使用次数递减）
        postTagMapper.deleteByPostId(postId);
        rebuildPostTags(postId, dto.getTagIds());

        evictPostListCache();

        // 详情缓存里装的是「正文 + 标题/摘要/封面/分类/标签」这一整块元数据，
        // 编辑正好把它们全换了，必须清。
        // ⚠️ 与列表缓存「宁早勿晚」的清法不同，这里刻意推迟到<b>提交之后</b>：
        // 详情缓存命中时会回读 post 行，所以若在提交前清掉，一个并发请求完全可能
        // 赶在提交之前回源、把「还没生效的旧标题/旧正文」重新灌回缓存 ——
        // 那就等于把脏数据钉住一个 TTL。提交后清没有这个反向窗口
        // （提交后回源读到的必然是已生效的新值）。
        runAfterCommit(() -> evictPostDetailCache(postId));

        // 旧正文的删除推迟到事务提交之后。
        // 只有真的提交成功才执行 —— 回滚了就说明新正文并没有生效，旧正文必须留着。
        if (oldObjectName != null && !oldObjectName.isEmpty() && !oldObjectName.equals(newObjectName)) {
            runAfterCommit(() -> deleteOssQuietly(oldObjectName, "清理旧正文"));
        }
    }

    /**
     * 删除帖子。
     *
     * <p><b>慢活出事务：</b>OSS 正文的删除挪到 {@code afterCommit}。原实现在事务内先删
     * OSS 再删库，如果后面的级联删除（评论点赞 → 评论 → 帖子点赞 → 收藏 → 浏览历史 →
     * 标签 → 帖子）任何一步失败导致回滚，结果就是：<b>帖子还在，正文没了</b>，
     * 点进去直接报错，而且没法修 —— OSS 对象已经不可逆地删掉了。
     * 改成提交后才删，回滚路径上帖子与正文都完好。</p>
     *
     * <p>代价是「库删了但 OSS 对象还在」的孤儿对象可能残留（提交后删除失败，或进程正好挂掉）。
     * 与编辑同理：孤儿对象只是垃圾，坏指针才是故障。</p>
     */
    @Override
    @Transactional
    public void deletePost(String postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }
        String currentId = BaseContext.getCurrentId();
        if (currentId == null || !currentId.equals(post.getUserId())) {
            throw new BaseException("无权限操作");
        }

        // 级联删除互动数据：评论点赞 → 评论 → 帖子点赞 → 收藏 → 浏览历史 → 标签关联 → 帖子
        postCommentLikeMapper.deleteByPostId(postId);
        postCommentMapper.deleteByPostId(postId);
        postLikeMapper.deleteByPostId(postId);
        postCollectMapper.deleteByPostId(postId);
        userBrowseHistoryMapper.deleteByPostId(postId);
        postTagMapper.deleteByPostId(postId);
        postMapper.deleteById(postId);

        evictPostListCache();
        // 帖子没了，它在所有用户「我点赞/我收藏」集合里的残留状态也不该继续命中
        userStateCache.forgetPostEverywhere(postId);

        // OSS 正文删除推迟到事务提交之后：回滚时帖子还在，正文必须还在
        String contentUrl = post.getContentUrl();
        if (contentUrl != null && !contentUrl.isEmpty()) {
            runAfterCommit(() -> deleteOssQuietly(contentUrl, "清理帖子正文"));
        }
    }

    /**
     * 把动作注册到「当前事务提交成功之后」执行；没有活动事务时立即执行。
     *
     * <p>用于把 OSS 删除这类<b>不可逆</b>的副作用挪出事务 —— 详见
     * {@link #updatePost} 与 {@link #deletePost} 的注释。
     * 与 {@code PostInteractionServiceImpl.triggerAiBotsAfterCommit} 同一套模式：
     * {@code isSynchronizationActive()} 才是正确的前置判断，因为
     * {@code registerSynchronization} 依赖的就是事务同步而不只是「有事务」。</p>
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
     * 删除 OSS 对象，失败只告警。
     *
     * <p>走到这里事务已经提交，数据库状态是最终态。删不掉只会留下一个孤儿对象，
     * 绝不能把异常抛回给已经成功的业务请求。</p>
     *
     * @param objectName 对象名
     * @param scene      日志里的场景描述
     */
    private void deleteOssQuietly(String objectName, String scene) {
        try {
            aliOssUtil.delete(objectName);
        } catch (Exception e) {
            log.warn("{}失败（仅残留 OSS 孤儿对象），objectName: {}", scene, objectName, e);
        }
    }

    /**
     * 组装帖子卡片 VO（填充分类名、书名、作者信息、标签）
     *
     * <p>单条调用时内部会自建一份只含本条数据的快照 —— 与批量路径共用同一套组装逻辑，
     * 保证「详情页字段」和「列表页字段」永远不会因为两处代码而分叉。</p>
     */
    private PostVO toPostVO(Post post) {
        PostVO vo = toPostVO(post, prefetchForPosts(List.of(post)));
        // 单条路径自己补一次计数增量；批量路径由 toPostVOList 统一补，
        // 免得一页 10 篇变成 10 次缓冲查询
        applyPendingCounts(List.of(vo));
        return vo;
    }

    /**
     * 批量组装帖子卡片 VO。
     *
     * <p><b>为什么要预取：</b>原实现在循环里逐条查「分类 / 书 / 作者 / 每个标签」，
     * 一页 10 篇帖子若有 3 个标签，就是 10 × (1+1+1+1+3) ≈ 70 次查询、
     * 而其中分类、作者、标签存在大量重复（同一个吧、同一个作者、同一批常用标签）。
     * 现在改成：先在循环外把这 4 类数据整批捞进 Map，循环内零查询。</p>
     */
    private List<PostVO> toPostVOList(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return new ArrayList<>();
        }
        PostAssembly ctx = prefetchForPosts(posts);
        List<PostVO> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            result.add(toPostVO(post, ctx));
        }
        // 计数增量整页一次性补上（4 × N 个 field 合并成一次 HMGET，不做 N 次往返）
        applyPendingCounts(result);
        return result;
    }

    /**
     * 给一批 VO 的四个计数补上「Redis 计数缓冲里还没落库的增量」。
     *
     * <p>列表页的所有出口（首页 / 吧内 / 个人主页 / 搜索 / 收藏夹 / 发帖回显）都汇到
     * {@link #toPostVOList} 与 {@link #toPostVO(Post)} 这两个入口，所以合并只放在这两处，
     * 既不会漏也不会重复加 —— <b>不要把合并下放到单条组装方法（2 参重载）里</b>，
     * 那是循环内路径，会把一次 HMGET 变成一页 N 次。</p>
     *
     * @param vos 帖子 VO 列表（就地修改计数）
     */
    private void applyPendingCounts(List<PostVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<String> postIds = new ArrayList<>(vos.size());
        for (PostVO vo : vos) {
            if (vo != null && vo.getId() != null) {
                postIds.add(vo.getId());
            }
        }
        if (postIds.isEmpty()) {
            return;
        }
        Map<String, CountBuffer.PostCountDelta> deltas = countBuffer.pendingPostCounts(postIds);
        for (PostVO vo : vos) {
            if (vo == null) {
                continue;
            }
            CountBuffer.PostCountDelta d =
                    deltas.getOrDefault(vo.getId(), CountBuffer.PostCountDelta.ZERO);
            vo.setViewCount(CountBuffer.merge(vo.getViewCount(), d.view()));
            vo.setLikeCount(CountBuffer.merge(vo.getLikeCount(), d.like()));
            vo.setCommentCount(CountBuffer.merge(vo.getCommentCount(), d.comment()));
            vo.setCollectCount(CountBuffer.merge(vo.getCollectCount(), d.collect()));
        }
    }

    /**
     * 帖子列表的批量装配快照：把「循环内会反复查的字典数据」一次性取齐。
     *
     * <p>字段说明：
     * categories — 分类ID → 分类；
     * books — 书ID → 书；
     * users — 用户ID → 用户；
     * tagMap — 帖子ID → 该帖的标签列表。</p>
     */
    private static class PostAssembly {
        final Map<String, Category> categories;
        final Map<String, Book> books;
        final Map<String, User> users;
        final Map<String, List<Tag>> tagMap;

        PostAssembly(Map<String, Category> categories, Map<String, Book> books,
                     Map<String, User> users, Map<String, List<Tag>> tagMap) {
            this.categories = categories;
            this.books = books;
            this.users = users;
            this.tagMap = tagMap;
        }
    }

    /** 为一批帖子预取分类 / 书 / 作者 / 标签（去重后批量查询） */
    private PostAssembly prefetchForPosts(List<Post> posts) {
        Set<String> categoryIds = new LinkedHashSet<>();
        Set<String> bookIds = new LinkedHashSet<>();
        Set<String> userIds = new LinkedHashSet<>();
        List<String> postIds = new ArrayList<>(posts.size());
        for (Post post : posts) {
            if (post == null) {
                continue;
            }
            postIds.add(post.getId());
            if (post.getCategoryId() != null && !post.getCategoryId().isEmpty()) {
                categoryIds.add(post.getCategoryId());
            }
            if (post.getBookId() != null && !post.getBookId().isEmpty()) {
                bookIds.add(post.getBookId());
            }
            if (post.getUserId() != null && !post.getUserId().isEmpty()) {
                userIds.add(post.getUserId());
            }
        }

        Map<String, Category> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<Category> categories = categoryMapper.selectByIds(new ArrayList<>(categoryIds));
            if (categories != null) {
                for (Category c : categories) {
                    categoryMap.put(c.getId(), c);
                }
            }
        }

        Map<String, Book> bookMap = new HashMap<>();
        if (!bookIds.isEmpty()) {
            List<Book> books = bookMapper.selectByIds(new ArrayList<>(bookIds));
            if (books != null) {
                for (Book b : books) {
                    bookMap.put(b.getId(), b);
                }
            }
        }

        Map<String, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.listByIds(new ArrayList<>(userIds));
            if (users != null) {
                for (User u : users) {
                    userMap.put(u.getId(), u);
                }
            }
        }

        // 标签：一次取回「帖子ID + 标签ID」扁平行，再一次取回标签实体，两层都是批量
        Map<String, List<Tag>> tagMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            Map<String, List<String>> tagIdsByPost = new HashMap<>();
            Set<String> allTagIds = new LinkedHashSet<>();
            List<PostTagRow> rows = postTagMapper.listTagRowsByPostIds(postIds);
            if (rows != null) {
                for (PostTagRow row : rows) {
                    if (row.getPostId() == null || row.getTagId() == null) {
                        continue;
                    }
                    tagIdsByPost.computeIfAbsent(row.getPostId(), k -> new ArrayList<>()).add(row.getTagId());
                    allTagIds.add(row.getTagId());
                }
            }
            Map<String, Tag> tagById = new HashMap<>();
            if (!allTagIds.isEmpty()) {
                List<Tag> tags = tagMapper.listByIds(new ArrayList<>(allTagIds));
                if (tags != null) {
                    for (Tag tag : tags) {
                        tagById.put(tag.getId(), tag);
                    }
                }
            }
            for (String postId : postIds) {
                List<Tag> tags = new ArrayList<>();
                List<String> ids = tagIdsByPost.get(postId);
                if (ids != null) {
                    for (String tagId : ids) {
                        Tag tag = tagById.get(tagId);
                        if (tag != null) {
                            tags.add(tag);
                        }
                    }
                }
                tagMap.put(postId, tags);
            }
        }

        return new PostAssembly(categoryMap, bookMap, userMap, tagMap);
    }

    /** 用预取快照组装单条帖子 VO（循环内不产生任何查询） */
    private PostVO toPostVO(Post post, PostAssembly ctx) {
        // 分类名
        String categoryName = null;
        if (post.getCategoryId() != null) {
            Category category = ctx.categories.get(post.getCategoryId());
            if (category != null) {
                categoryName = category.getName();
            }
        }

        // 书名
        String bookTitle = null;
        if (post.getBookId() != null && !post.getBookId().isEmpty()) {
            Book book = ctx.books.get(post.getBookId());
            if (book != null) {
                bookTitle = book.getTitle();
            }
        }

        // 作者信息
        String authorName = null;
        String authorAvatar = null;
        if (post.getUserId() != null) {
            User user = ctx.users.get(post.getUserId());
            if (user != null) {
                authorName = user.getUsername();
                authorAvatar = user.getAvatar();
            }
        }

        // 标签（已在预取阶段组装好）
        List<Tag> tags = ctx.tagMap.getOrDefault(post.getId(), new ArrayList<>());

        return PostVO.builder()
                .id(post.getId())
                .title(post.getTitle())
                .summary(post.getSummary())
                .coverImage(post.getCoverImage())
                .bookId(post.getBookId())
                .bookTitle(bookTitle)
                .categoryId(post.getCategoryId())
                .categoryName(categoryName)
                .postType(post.getPostType())
                .authorId(post.getUserId())
                .authorName(authorName)
                .authorAvatar(authorAvatar)
                .tags(tags)
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .collectCount(post.getCollectCount())
                .isTop(post.getIsTop())
                .publishTime(post.getPublishTime())
                .build();
    }

    /**
     * 帖子列表缓存失效。
     *
     * <p>帖子列表的缓存键把「筛选维度 + 页码」都编进了 key，一篇新帖理论上只影响
     * 「不带筛选条件、第 1 页、按最新排序」那一个键 —— 但穷举出哪些键受影响很脆弱
     * （吧里发帖影响吧列表，标签帖影响标签列表，热榜排序被点赞影响……），
     * 所以干脆按前缀整体清掉。键空间是「维度组合数 × 第 1 页」，量级可控。</p>
     */
    private void evictPostListCache() {
        cache.evictByPattern(RedisConstant.POST_LIST_PATTERN);
    }

    /**
     * 帖子详情缓存失效（单篇）。
     *
     * <p>只有「编辑」需要它 —— 详情缓存里装的正文与元数据只随编辑变化。
     * 审核 / 置顶 / 上下架 / 吧务隐藏<b>不在</b>此列：详情读路径每次都回读 post 行，
     * 并用行值覆盖这些易变字段，所以它们天然实时，不需要谁去清缓存。
     * 删除走 {@code UserStateCacheServiceImpl.forgetPostEverywhere}。</p>
     *
     * @param postId 帖子ID
     */
    private void evictPostDetailCache(String postId) {
        cache.evict(RedisConstant.POST_DETAIL + postId);
    }

    /**
     * 记录一次浏览。命中 (user_id, post_id) 唯一键时只推进时间并累加次数，
     * 因此同一篇帖子反复打开不会把历史列表刷屏。
     * 这里吞掉异常：浏览历史属于附加能力，不该让它影响帖子详情的可用性。
     */
    private void recordBrowseHistory(String userId, String postId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            userBrowseHistoryMapper.upsert(UserBrowseHistory.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .postId(postId)
                    .viewTime(now)
                    .createTime(now)
                    .build());
        } catch (Exception e) {
            log.warn("写入浏览历史失败 userId={} postId={}: {}", userId, postId, e.getMessage());
        }
    }

    /**
     * 上传正文 Markdown 到 OSS，返回 objectName。
     *
     * <p><b>必须在事务外调用</b>（发帖 / 编辑都是先调用它、再进入 {@code @Transactional}
     * 的 DB 方法）。名字里带 {@code OutsideTransaction} 是刻意的：它要提示后来者
     * 不要顺手把调用点挪回事务方法体内 —— 那样一次几十到几百毫秒的 OSS 往返
     * 会把数据库连接一起占住。详见 {@code publishPost} / {@code updatePost} 的注释。</p>
     *
     * @param content 正文 Markdown
     * @return OSS 对象名
     */
    private String uploadContentOutsideTransaction(String content) {
        String objectName = "post/" + LocalDateTime.now().format(OSS_DATE_FORMAT) + "/" + UUID.randomUUID() + ".md";
        aliOssUtil.upload(content.getBytes(StandardCharsets.UTF_8), objectName);
        return objectName;
    }

    /**
     * 保存帖子标签关联。
     * 标签的使用次数不再在这里累加 —— useCount 已改为由 post_tag 关联表实时统计
     * （见 TagMapper.xml），增删关联即自动同步，无需业务代码手工维护。
     */
    private void savePostTags(String postId, List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        List<PostTag> postTags = new ArrayList<>();
        for (String tagId : tagIds) {
            if (tagId == null || tagId.trim().isEmpty()) {
                continue;
            }
            postTags.add(PostTag.builder()
                    .id(UUID.randomUUID().toString())
                    .postId(postId)
                    .tagId(tagId)
                    .build());
        }
        if (!postTags.isEmpty()) {
            postTagMapper.batchInsert(postTags);
        }
    }

    /**
     * 重建帖子标签关联（调用方已先 deleteByPostId 清空旧关联，这里只负责批量插入）
     */
    private void rebuildPostTags(String postId, List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        // 去重：post_tag 有 uk_post_tag(post_id, tag_id) 唯一键，同一篇帖子挂两次同一个标签
        // 会让整条 insert 失败、连带整篇发帖回滚。前端允许用户「选已有标签 + 手敲同名标签」，
        // 两路都会解析成同一个 tag_id，所以这里必须用集合兜一层。
        Set<String> uniqueTagIds = new LinkedHashSet<>();
        for (String tagId : tagIds) {
            if (tagId == null || tagId.trim().isEmpty()) {
                continue;
            }
            uniqueTagIds.add(tagId.trim());
        }
        if (uniqueTagIds.isEmpty()) {
            return;
        }

        List<PostTag> postTags = new ArrayList<>(uniqueTagIds.size());
        for (String tagId : uniqueTagIds) {
            postTags.add(PostTag.builder()
                    .id(UUID.randomUUID().toString())
                    .postId(postId)
                    .tagId(tagId)
                    .build());
        }
        postTagMapper.batchInsert(postTags);
    }
}
