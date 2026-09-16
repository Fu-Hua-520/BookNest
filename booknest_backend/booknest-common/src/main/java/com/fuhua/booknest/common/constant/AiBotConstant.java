package com.fuhua.booknest.common.constant;

import java.util.List;
import java.util.Map;

/**
 * 评论区 AI 机器人常量。
 *
 * <p>厂商 / 模型 / base-url 的映射集中在这里，Service 与前端下拉都以此为准。
 * 统一走 OpenAI 兼容协议（{@code /v1/chat/completions}），因此
 * <b>base-url 一律不带结尾的 {@code /v1}</b> —— Spring AI 的
 * {@code OpenAiApi} 会自动补 {@code /v1/chat/completions}，写重了会变成
 * {@code /v1/v1/chat/completions} → 404。</p>
 */
public class AiBotConstant {

    private AiBotConstant() {
    }

    /* ------------------------- 厂商 ------------------------- */

    /** DeepSeek */
    public static final String PROVIDER_DEEPSEEK = "deepseek";

    /** 阿里云百炼（DashScope OpenAI 兼容模式） */
    public static final String PROVIDER_DASHSCOPE = "dashscope";

    /**
     * 厂商 → base-url（不含 /v1）。
     * DeepSeek 最终请求：https://api.deepseek.com/v1/chat/completions
     * 百炼最终请求　　：https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
     */
    public static final Map<String, String> PROVIDER_BASE_URL = Map.of(
            PROVIDER_DEEPSEEK, "https://api.deepseek.com",
            PROVIDER_DASHSCOPE, "https://dashscope.aliyuncs.com/compatible-mode"
    );

    /** 厂商 → 默认模型 */
    public static final Map<String, String> PROVIDER_DEFAULT_MODEL = Map.of(
            PROVIDER_DEEPSEEK, "deepseek-flash",
            PROVIDER_DASHSCOPE, "qwen-max"
    );

    /** 厂商 → 可选模型（前端下拉用；也可以填列表外的模型名，后端只做非空校验） */
    public static final Map<String, List<String>> PROVIDER_MODELS = Map.of(
            PROVIDER_DEEPSEEK, List.of("deepseek-flash", "deepseek-chat", "deepseek-reasoner"),
            PROVIDER_DASHSCOPE, List.of("qwen-max", "qwen-plus", "qwen-turbo", "qwen-long")
    );

    /** 厂商展示名 */
    public static final Map<String, String> PROVIDER_LABEL = Map.of(
            PROVIDER_DEEPSEEK, "DeepSeek",
            PROVIDER_DASHSCOPE, "阿里云百炼"
    );

    /**
     * 归一化厂商名（大小写、别名容错），非法值返回 null。
     * 例如「DASHSCOPE」「qwen」「百炼」都归到 dashscope。
     */
    public static String normalizeProvider(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase();
        if (v.isEmpty()) {
            return null;
        }
        if (v.contains("deepseek") || v.equals("ds")) {
            return PROVIDER_DEEPSEEK;
        }
        if (v.contains("dashscope") || v.contains("qwen") || v.contains("aliyun")
                || v.contains("bailian") || v.contains("百炼") || v.contains("通义")) {
            return PROVIDER_DASHSCOPE;
        }
        return null;
    }

    /* ------------------------- 审核状态 ------------------------- */

    /** 待审核 */
    public static final int AUDIT_PENDING = 0;
    /** 已通过 */
    public static final int AUDIT_APPROVED = 1;
    /** 已驳回 */
    public static final int AUDIT_REJECTED = 2;

    /** 创建者侧开关 */
    public static final int ENABLED = 1;
    public static final int DISABLED = 0;

    /* ------------------------- 校验阈值 ------------------------- */

    /** 单用户最多能建多少个机器人 */
    public static final int MAX_BOTS_PER_USER = 5;

    /** 机器人名称长度（名称即 @ 触发词） */
    public static final int NAME_MIN_LEN = 2;
    public static final int NAME_MAX_LEN = 20;

    /** 机器人简介长度 */
    public static final int DESCRIPTION_MAX_LEN = 200;

    /** 系统提示词长度 */
    public static final int SYSTEM_PROMPT_MAX_LEN = 2000;

    /** API Key 长度上限 */
    public static final int API_KEY_MAX_LEN = 200;

    /** 默认温度与 token 上限 */
    public static final double DEFAULT_TEMPERATURE = 0.7;
    public static final int DEFAULT_MAX_TOKENS = 800;
    public static final int MAX_TOKENS_MIN = 100;
    public static final int MAX_TOKENS_MAX = 4000;

    /* ------------------------- 触发与回复 ------------------------- */

    /** 触发前缀 */
    public static final String TRIGGER_PREFIX = "@";

    /**
     * 触发词候选字符集：汉字 / 字母 / 数字 / 下划线 / 连字符。
     * 机器人名由 {@link #NAME_PATTERN} 限制，两处保持一致。
     */
    public static final String NAME_PATTERN = "^[\\p{IsHan}A-Za-z0-9_-]+$";

    /** 一条评论里最多触发几个机器人（防刷屏） */
    public static final int MAX_TRIGGERS_PER_COMMENT = 3;

    /**
     * 机器人回复正文长度上限（字符，超出截断）。
     *
     * <p>这个值同时会写进系统提示词，作为模型侧的硬性上限。
     * 两道约束都要有：提示词管「别写太长」，这里管「超了就兜底截断」——
     * 模型不一定听话，而评论区被一条上千字的回复刷屏很难看，
     * 更何况调用费用是机器人作者自己出的。</p>
     */
    public static final int REPLY_CONTENT_MAX_LEN = 200;

    /** 单次模型调用超时（秒） */
    public static final int REPLY_TIMEOUT_SECONDS = 60;

    /** 触发索引（机器人名 → ID）的进程内缓存时长（毫秒） */
    public static final long TRIGGER_INDEX_TTL_MS = 60_000L;

    /** 动态 ChatClient 缓存的实例数上限（超出后整体清空重建，避免无界增长） */
    public static final int CHAT_CLIENT_CACHE_LIMIT = 128;

    /** 回复失败时落库的提示文案模板（%s = 机器人名） */
    public static final String REPLY_FAILED_TEXT = "（「%s」暂时没能回复成功，稍后再试试吧）";

    /** 场景约束（贴在被 @ 的场景里，拼在机器人自定义人设之后） */
    public static final String SCENE_PROMPT_TEMPLATE = """
            你是「%s」，BookNest 书友社区里的 AI 机器人。
            %s

            【当前场景】一位书友在帖子《%s》的评论区 @ 了你，你需要直接回复这条评论。
            【回复要求】
            1. 只输出回复正文，不要加「@某某」前缀，也不要复述对方的问题；
            2. 【字数上限】全文（含标点）最多 %d 字，这是硬性上限，任何情况下都不要超。
               快写到上限时就把话收住，给一个完整的结尾，别让句子停在半截。
               用中文，语气自然口语化，可用换行分段；
            3. 不要输出 Markdown 表格、代码块或标题记号；
            4. 涉及书籍时给出书名与作者；不确定的信息不要编造；
            5. 【帖子正文】给你的可能只是节选（末尾带「已在此截断」标记）。
               遇到这个标记、或者干脆没看到正文段落时，就如实说明你只看到了部分内容，
               能答多少答多少 —— 绝不要编造正文里不存在的情节、人物或观点。
            """;

    /** 触发用户消息模板（%s 依次为：书吧 / 标题 / 摘要 / 正文块（可能为空串）/ 评论者 / 评论原文） */
    public static final String USER_PROMPT_TEMPLATE = """
            【所在书吧】%s
            【帖子标题】%s
            【帖子摘要】%s
            %s【评论者】%s
            【评论原文】%s
            """;

    /** 没有自定义系统提示词时的兜底身份描述 */
    public static final String DEFAULT_PERSONA = "你是一位热心的读书人，熟悉各类书籍，乐于用简短、友好的话回应书友。";

    /* ------------------------- 帖子正文注入 ------------------------- */

    /**
     * 注入给模型的帖子正文长度上限（字符）。
     *
     * <p>正文并不在数据库里 —— 发帖时它作为 Markdown 文件传到了 OSS，
     * 库里 {@code post.content_url} 只存对象名（见 {@code PostServiceImpl#uploadContent}），
     * 所以每次回复都要从 OSS 下载一次。</p>
     *
     * <p>上限压得比较小是刻意的：机器人作者用的是自己的 API Key，
     * 正文越长 input token 越多、账单越贵；888 字足够覆盖绝大多数
     * 书评 / 读书笔记的核心内容。</p>
     */
    public static final int POST_CONTENT_MAX_LEN = 888;

    /** 正文被截断时追加在末尾的标记（模型据此知道「后面还有，只是没给我」） */
    public static final String POST_CONTENT_TRUNCATED_MARK = "\n……（正文过长，已在此截断）";

    /**
     * 清洗并截断帖子正文，供拼进提示词。
     *
     * <p>正文是 Markdown：图片语法对模型是纯噪声（只剩 URL，没有可用信息），
     * 链接 URL 同理。所以在截断之前先把这两类去掉，
     * 让 888 字的额度尽量留给真正的文字。</p>
     *
     * @param raw OSS 里的正文原文
     * @return 清洗 + 截断后的正文；原文为空时返回 null
     */
    public static String sanitizePostContent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw
                // 图片整段删除：![alt](url)
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")
                // 链接只留可见文字：[文字](url) -> 文字
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                // 连续空行压成一个
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > POST_CONTENT_MAX_LEN) {
            text = text.substring(0, POST_CONTENT_MAX_LEN) + POST_CONTENT_TRUNCATED_MARK;
        }
        return text;
    }

    /**
     * 判断名称是否合法（汉字/字母/数字/下划线/连字符，2~20 位）。
     *
     * @param name 名称
     * @return 合法返回 true
     */
    public static boolean isValidName(String name) {
        if (name == null) {
            return false;
        }
        String v = name.trim();
        if (v.length() < NAME_MIN_LEN || v.length() > NAME_MAX_LEN) {
            return false;
        }
        return v.matches(NAME_PATTERN);
    }
}
