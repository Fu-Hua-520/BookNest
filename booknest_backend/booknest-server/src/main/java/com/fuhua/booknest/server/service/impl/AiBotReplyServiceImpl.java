package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.AiBotConstant;
import com.fuhua.booknest.common.constant.NotificationConstant;
import com.fuhua.booknest.pojo.entity.AiBot;
import com.fuhua.booknest.pojo.entity.Category;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.entity.PostComment;
import com.fuhua.booknest.server.ai.AiBotChatClientFactory;
import com.fuhua.booknest.server.ai.AiBotExecutorConfig;
import com.fuhua.booknest.server.ai.AiBotTriggerContext;
import com.fuhua.booknest.server.common.CountBuffer;
import com.fuhua.booknest.server.mapper.AiBotMapper;
import com.fuhua.booknest.server.mapper.CategoryMapper;
import com.fuhua.booknest.server.mapper.PostCommentMapper;
import com.fuhua.booknest.server.mq.NotificationProducer;
import com.fuhua.booknest.server.service.AiBotReplyService;
import com.fuhua.booknest.server.service.AiBotService;
import com.fuhua.booknest.server.utils.AliOssUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * AI 机器人回复服务实现。
 *
 * <p>完整链路：评论提交事务 → {@link #onCommentPublished} 解析 @ →
 * 丢进 {@code aiBotReplyExecutor} → 构造提示词调用模型 →
 * 以 bot_id 落库成楼中楼回复 → 通知评论者。</p>
 */
@Service
@Slf4j
public class AiBotReplyServiceImpl implements AiBotReplyService {

    /** 幂等失败重试窗口等场景下的日志标识 */
    private static final String LOG_TAG = "AI机器人回复";

    /** 通知正文摘要长度上限（与回复评论通知保持一致） */
    private static final int EXCERPT_MAX = 40;

    @Autowired
    private AiBotService aiBotService;
    @Autowired
    private AiBotChatClientFactory chatClientFactory;
    @Autowired
    private AiBotMapper aiBotMapper;
    @Autowired
    private PostCommentMapper postCommentMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private NotificationProducer notificationProducer;
    @Autowired
    private AliOssUtil aliOssUtil;
    @Autowired
    private CountBuffer countBuffer;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    @Qualifier(AiBotExecutorConfig.EXECUTOR_BEAN_NAME)
    private TaskExecutor aiBotReplyExecutor;

    /**
     * 手工构造 TransactionTemplate。
     * 这里的落库动作跑在线程池里，不是从 Controller 进来的调用链，
     * 用显式模板比依赖 @Transactional 代理更一目了然（也避免自调用失效的坑）。
     */
    private TransactionTemplate txTemplate;

    @PostConstruct
    void init() {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void onCommentPublished(Post post, PostComment comment, String commenterName) {
        if (post == null || comment == null || comment.getContent() == null) {
            return;
        }
        List<AiBot> bots;
        try {
            bots = aiBotService.matchTriggered(comment.getContent());
        } catch (Exception e) {
            // 解析失败绝不能影响评论本身：评论已经落库了，这里只记日志
            log.warn("{}触发解析失败，跳过: commentId={}", LOG_TAG, comment.getId(), e);
            return;
        }
        if (bots == null || bots.isEmpty()) {
            return;
        }

        AiBotTriggerContext ctx = AiBotTriggerContext.builder()
                .postId(post.getId())
                .postTitle(post.getTitle())
                .postSummary(post.getSummary())
                .barName(resolveBarName(post.getCategoryId()))
                .triggerCommentId(comment.getId())
                .commentContent(comment.getContent())
                .commenterId(comment.getUserId())
                .commenterName(commenterName)
                // 正文不在库里（是 OSS 上的 Markdown 文件），这里只带对象名，
                // 下载放到线程池里做 —— 见 loadPostContent
                .postContentUrl(post.getContentUrl())
                .build();

        log.info("{}触发：postId={}, commentId={}, 命中机器人 {} 个 -> {}",
                LOG_TAG, post.getId(), comment.getId(), bots.size(),
                bots.stream().map(AiBot::getName).toList());

        for (AiBot bot : bots) {
            String botId = bot.getId();
            try {
                aiBotReplyExecutor.execute(() -> replyOne(botId, ctx));
            } catch (RejectedExecutionException e) {
                // 队列满：丢掉这次 AI 回复比阻塞发评论的请求更合理
                log.warn("{}任务队列已满，本次回复被丢弃: botId={}, commentId={}", LOG_TAG, botId, comment.getId());
            }
        }
    }

    /**
     * 取书吧名给模型当上下文（Post 实体上没有冗余这个字段，得按 categoryId 查一次）。
     * 查不到不影响回复，只是少一句上下文。
     */
    private String resolveBarName(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        try {
            Category category = categoryMapper.selectById(categoryId);
            return category == null ? null : category.getName();
        } catch (Exception e) {
            log.debug("{}取书吧名失败: categoryId={}", LOG_TAG, categoryId);
            return null;
        }
    }

    /* ============================ 单次回复 ============================ */

    /**
     * 单个机器人回复一条评论（跑在线程池里）。
     *
     * @param botId 机器人ID
     * @param ctx   触发上下文
     */
    private void replyOne(String botId, AiBotTriggerContext ctx) {
        try {
            AiBot bot = aiBotService.getById(botId);
            if (bot == null) {
                log.warn("{}机器人已不存在，跳过: botId={}", LOG_TAG, botId);
                return;
            }
            // 幂等：同一条评论被重复触发（用户重发、重试）时不重复回复
            if (postCommentMapper.countByBotAndReply(botId, ctx.getTriggerCommentId()) > 0) {
                log.debug("{}该机器人已回复过这条评论，跳过: botId={}, commentId={}",
                        LOG_TAG, botId, ctx.getTriggerCommentId());
                return;
            }
            // 取正文：正文在 OSS 上，每条回复都下载一次（不再按关键词取舍）
            String postContent = loadPostContent(ctx);
            String text = generate(bot, ctx, postContent);
            persist(bot, ctx, text);
        } catch (Exception e) {
            // 兜底：任何未预期异常都不能让线程池线程带着异常退出
            log.error("{}未预期异常: botId={}, commentId={}", LOG_TAG, botId, ctx.getTriggerCommentId(), e);
        }
    }

    /**
     * 调用模型生成回复。失败不抛异常，而是返回一段「暂时没能回复」的提示文案 ——
     * 用户主动 @ 了机器人，什么都不发生比一句道歉更让人困惑；
     * 同时这段文案会出现在评论区，机器人作者也能立刻发现自己的 Key 有问题。
     *
     * @param postContent 已按需取到的帖子正文（没取到为 null，此时按摘要回答）
     */
    private String generate(AiBot bot, AiBotTriggerContext ctx, String postContent) {
        try {
            ChatClient chatClient = chatClientFactory.getChatClient(bot);
            String raw = chatClient.prompt()
                    .system(buildSystemPrompt(bot, ctx))
                    .user(buildUserPrompt(ctx, postContent))
                    .call()
                    .content();
            String clean = sanitize(raw, bot.getName());
            if (clean.isEmpty()) {
                log.warn("{}模型返回空内容: botId={}, model={}", LOG_TAG, bot.getId(), bot.getModel());
                return String.format(AiBotConstant.REPLY_FAILED_TEXT, bot.getName());
            }
            return clean;
        } catch (Exception e) {
            log.warn("{}调用模型失败: botId={}, provider={}, model={}, reason={}",
                    LOG_TAG, bot.getId(), bot.getProvider(), bot.getModel(), e.getMessage());
            return String.format(AiBotConstant.REPLY_FAILED_TEXT, bot.getName());
        }
    }

    /**
     * 组装系统提示词：机器人自定义人设 + 当前场景约束。
     */
    private String buildSystemPrompt(AiBot bot, AiBotTriggerContext ctx) {
        String persona = (bot.getSystemPrompt() == null || bot.getSystemPrompt().isBlank())
                ? AiBotConstant.DEFAULT_PERSONA
                : bot.getSystemPrompt().trim();
        return String.format(AiBotConstant.SCENE_PROMPT_TEMPLATE,
                bot.getName(),
                persona,
                nvl(ctx.getPostTitle(), "（无标题）"),
                AiBotConstant.REPLY_CONTENT_MAX_LEN);
    }

    /**
     * 组装用户消息：把帖子与评论的上下文摊平给模型，避免它答非所问。
     *
     * @param postContent 帖子正文（可为 null；为 null 时只给摘要，提示词里已交代模型别硬编）
     */
    private String buildUserPrompt(AiBotTriggerContext ctx, String postContent) {
        // 只有真取到正文时才拼这一段 —— 拼个空的「【帖子正文】」反而会误导模型以为正文是空的
        String contentBlock = (postContent == null || postContent.isBlank())
                ? ""
                : "【帖子正文】\n" + postContent + "\n";
        return String.format(AiBotConstant.USER_PROMPT_TEMPLATE,
                nvl(ctx.getBarName(), "未分类"),
                nvl(ctx.getPostTitle(), "（无标题）"),
                nvl(ctx.getPostSummary(), "（无摘要）"),
                contentBlock,
                nvl(ctx.getCommenterName(), "书友"),
                ctx.getCommentContent());
    }

    /**
     * 取帖子正文：无条件下载（每次 @ 都带正文，交给模型自己判断用不用得上）。
     *
     * <p>两点刻意设计：<br>
     * ① <b>下载放在线程池里</b>（本方法只在 {@link #replyOne} 中调用）。
     *    绝不能挪到 {@code onCommentPublished} —— 那是发评论的请求线程
     *    （{@code afterCommit} 也仍在请求线程上），在那儿下载等于给用户的
     *    「发表评论」按钮平白加一次 OSS 往返；<br>
     * ② <b>失败一律降级成 null</b>（只记日志）。拿不到正文顶多回复得浅一点，
     *    不该让整条 AI 回复失败 —— 提示词里已经交代过模型：没看到正文段落就照实说。</p>
     *
     * <p>代价是每条回复都多一次 OSS 往返 + 一份正文 token（上限
     * {@link AiBotConstant#POST_CONTENT_MAX_LEN} 字，由作者自付）。换来的是不再有
     * 「措辞没命中关键词就答不上来」这种时灵时不灵的行为。</p>
     *
     * @param ctx 触发上下文
     * @return 清洗 + 截断后的正文；没配正文或取不到时为 null
     */
    private String loadPostContent(AiBotTriggerContext ctx) {
        String objectName = ctx.getPostContentUrl();
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        try {
            String content = AiBotConstant.sanitizePostContent(aliOssUtil.downloadAsString(objectName));
            if (content == null) {
                log.debug("{}帖子正文为空，改按摘要回复: postId={}", LOG_TAG, ctx.getPostId());
            }
            return content;
        } catch (Exception e) {
            log.warn("{}帖子正文获取失败，降级为摘要回复: postId={}, objectName={}",
                    LOG_TAG, ctx.getPostId(), objectName, e);
            return null;
        }
    }

    /**
     * 清洗模型输出：
     * 去掉可能带的「@机器人名」前缀（评论里已经显示了是谁在回复，再 @ 一次很怪）、
     * 压掉多余空行、按长度截断。
     */
    private String sanitize(String raw, String botName) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        // 模型偶尔会自作聪明加个称呼，形态是 @名字 / @名字：/ @名字，
        String stripped = stripLeadingMention(text, botName);
        if (!stripped.isEmpty()) {
            text = stripped;
        }
        // 连续 3 个以上换行压成 2 个，避免评论区出现大片空白
        text = text.replaceAll("\\n{3,}", "\n\n").trim();
        if (text.length() > AiBotConstant.REPLY_CONTENT_MAX_LEN) {
            text = text.substring(0, AiBotConstant.REPLY_CONTENT_MAX_LEN) + "…";
        }
        return text;
    }

    /**
     * 去掉开头的 «@机器人名» 及其后面的冒号/逗号/空白。
     *
     * @param text    原文
     * @param botName 机器人名
     * @return 去掉前缀后的文本（没匹配上则原样返回）
     */
    private String stripLeadingMention(String text, String botName) {
        if (botName == null || botName.isEmpty() || text.length() < botName.length() + 1) {
            return text;
        }
        if (!text.startsWith(AiBotConstant.TRIGGER_PREFIX)) {
            return text;
        }
        String afterAt = text.substring(1);
        if (!afterAt.regionMatches(true, 0, botName, 0, botName.length())) {
            return text;
        }
        return afterAt.substring(botName.length()).replaceFirst("^[：:，,、\\s]+", "").trim();
    }

    /* ============================ 落库与通知 ============================ */

    /**
     * 把 AI 回复写成一条评论（bot_id 有值、user_id 为空），并更新计数、通知评论者。
     */
    private void persist(AiBot bot, AiBotTriggerContext ctx, String text) {
        PostComment reply = PostComment.builder()
                .id(UUID.randomUUID().toString())
                .postId(ctx.getPostId())
                // 机器人没有 user 行，这里必须是 null；评论区靠 bot_id 认出它是 AI
                .userId(null)
                .botId(bot.getId())
                .content(text)
                // 挂在触发它的那条评论下面 → 前端渲染成楼中楼
                .replyId(ctx.getTriggerCommentId())
                .likeCount(0)
                .createTime(LocalDateTime.now())
                .build();

        txTemplate.executeWithoutResult(status -> {
            postCommentMapper.insert(reply);
            aiBotMapper.incrementReplyCount(bot.getId());
        });

        // 帖子评论数 +1：不直写 MySQL，而是记进 Redis 计数缓冲，由 CountFlushJob 定时批量落库。
        // 放在事务之外 —— 事务回滚了这条回复就不存在，计数也不该加。
        countBuffer.addDelta(CountBuffer.CounterType.POST_COMMENT, ctx.getPostId(), 1L);

        // 通知走 MQ，放在事务外：通知失败不该把已经写好的回复回滚
        notifyCommenter(bot, ctx, reply.getId());

        log.info("{}已回复: botId={}, name={}, postId={}, replyCommentId={}, 长度={}",
                LOG_TAG, bot.getId(), bot.getName(), ctx.getPostId(), reply.getId(), text.length());
    }

    /**
     * 通知触发者：类型 AI_REPLY，sourceId = 帖子ID（落点页面），
     * anchorId = 这条 AI 回复自己的 ID（落点位置，前端据此滚到该评论并高亮）。
     */
    private void notifyCommenter(AiBot bot, AiBotTriggerContext ctx, String replyCommentId) {
        if (ctx.getCommenterId() == null || ctx.getCommenterId().isBlank()) {
            return;
        }
        String content = "AI 机器人「" + bot.getName() + "」回复了你的评论：" + excerpt(ctx.getCommentContent());
        try {
            notificationProducer.sendNotification(ctx.getCommenterId(), NotificationConstant.TYPE_AI_REPLY,
                    content, ctx.getPostId(), replyCommentId);
        } catch (Exception e) {
            log.warn("{}回复通知发送失败: botId={}, commenterId={}", LOG_TAG, bot.getId(), ctx.getCommenterId(), e);
        }
    }

    /** 通知摘要：压掉换行并截断 */
    private String excerpt(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= EXCERPT_MAX ? flat : flat.substring(0, EXCERPT_MAX) + "…";
    }

    private String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
