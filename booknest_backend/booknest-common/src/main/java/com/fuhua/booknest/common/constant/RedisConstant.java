package com.fuhua.booknest.common.constant;

/**
 * Redis Key常量类
 */
public class RedisConstant {
    
    // ==================== 文章相关 ====================
    
    /**
     * 热门文章列表
     * Key: hot:articles
     * Value: List<Article> (JSON)
     * 过期时间: 30分钟
     */
    public static final String HOT_ARTICLES = "hot:articles";
    
    /**
     * 文章详情缓存
     * Key: article:detail:{articleId}
     * Value: Article (JSON)
     * 过期时间: 1小时
     */
    public static final String ARTICLE_DETAIL = "article:detail:";
    
    /**
     * 文章浏览量计数器（异步更新到数据库）
     * Key: article:view:count:{articleId}
     * Value: Long
     * 过期时间: 永久（定时同步到DB）
     */
    public static final String ARTICLE_VIEW_COUNT = "article:view:count:";
    
    /**
     * 文章浏览量计数器前缀（用于SCAN匹配）
     */
    public static final String ARTICLE_VIEW_COUNT_PATTERN = "article:view:count:*";
    
    /**
     * 文章点赞计数（高频操作，减少数据库压力）
     * Key: article:like:count:{articleId}
     * Value: Integer
     * 过期时间: 永久（定时同步到DB）
     */
    public static final String ARTICLE_LIKE_COUNT = "article:like:count:";
    
    /**
     * 用户是否点赞文章（Set集合）
     * Key: article:like:users:{articleId}
     * Value: Set<userId>
     * 过期时间: 1小时
     */
    public static final String ARTICLE_LIKE_USERS = "article:like:users:";
    
    /**
     * 文章AI智能摘要缓存
     * Key: abstract:{articleId}
     * Value: ArticleSummaryVO (JSON)
     * 过期时间: 14天
     */
    public static final String ARTICLE_SUMMARY = "abstract:";
    
    // ==================== 用户相关 ====================
    
    /**
     * 用户Token缓存
     * Key: user:token:{token}
     * Value: UserInfo (JSON)
     * 过期时间: 2小时（与JWT过期时间一致）
     */
    public static final String USER_TOKEN = "user:token:";
    
    /**
     * 用户信息缓存
     * Key: user:info:{userId}
     * Value: User (JSON)
     * 过期时间: 30分钟
     */
    public static final String USER_INFO = "user:info:";
    
    /**
     * 用户统计信息缓存
     * Key: user:stats:{userId}
     * Value: UserStatsVO (JSON)
     * 过期时间: 10分钟
     */
    public static final String USER_STATS = "user:stats:";
    
    // ==================== 分类/标签相关 ====================
    
    /**
     * 分类列表缓存
     * Key: category:list
     * Value: List<Category> (JSON)
     * 过期时间: 1小时
     */
    public static final String CATEGORY_LIST = "category:list";
    
    /**
     * 热门标签列表
     * Key: hot:tags
     * Value: List<Tag> (JSON)
     * 过期时间: 30分钟
     */
    public static final String HOT_TAGS = "hot:tags";
    
    // ==================== 接口限流 ====================
    
    /**
     * 接口访问次数限流
     * Key: rate:limit:{userId}:{api}
     * Value: Integer (访问次数)
     * 过期时间: 1分钟
     */
    public static final String RATE_LIMIT = "rate:limit:";
    
    // ==================== 验证码 ====================
    
    /**
     * 短信/邮箱验证码
     * Key: captcha:{phone/email}
     * Value: String (验证码)
     * 过期时间: 5分钟
     */
    public static final String CAPTCHA = "captcha:";
    
    // ==================== RAG 文章全文缓存 ====================

    /**
     * 文章Markdown全文缓存（深度检索下载后缓存，避免重复访问OSS）
     * Key: article:fulltext:{articleId}
     * Value: String (Markdown全文)
     * 过期时间: 2小时
     */
    public static final String ARTICLE_FULLTEXT = "article:fulltext:";

    // ==================== 私信聊天 ====================

    /**
     * 私信未读总数
     * Key: chat:unread:{userId}
     * Value: Integer
     */
    public static final String CHAT_UNREAD = "chat:unread:";

    // ==================== 私信跨实例投递（Pub/Sub + 在线态） ====================
    //
    // ⚠️ 这一族刻意不进 bn:cache: 前缀：bn:cache: 下全是「丢了就从 MySQL 重算」的派生数据，
    // 运维按 bn:cache:* 整体清理是无害的；而下面存的是「谁在线」「哪个实例持有连接」这类
    // 运行时事实，被清掉会让在线态瞬时失真（不过只是显示问题，不影响消息本身 —— 消息的事实来源
    // 始终是 MySQL 的 chat_message 表）。前缀分开，避免顺手清理时误伤。

    /**
     * 聊天消息跨实例投递频道（Pub/Sub 频道名，不是键，不带 TTL）。
     *
     * <p>{@code ChatWebSocketHandler} 在本地推送完成后，把同一帧原始 JSON 发到这个频道；
     * 每个实例都订阅它，收到后只推给自己本地的会话。发送方所在实例凭消息里的
     * {@code from} 字段认出自己发的并跳过，所以不会重复推送。</p>
     */
    public static final String WS_PUSH_CHANNEL = "bn:ws:push";

    /**
     * 用户在线态键前缀。
     * Key: bn:ws:online:{userId}
     * Value: ZSET，member = 节点ID，score = 该节点最后一次心跳/建连的毫秒时间戳
     *
     * <p>用 ZSET 而不是「SET 成员」或「INCR 计数器」的原因：</p>
     * <ul>
     *   <li><b>SET 成员</b>：某实例进程被杀（来不及 SREM）时，它的成员会一直留着，
     *       而 EXPIRE 会被其它存活节点的心跳不断续期，幽灵成员永远不消失；</li>
     *   <li><b>INCR 计数器</b>：崩溃漏掉的 DECR 会让计数永久虚高，且无法按节点回收；</li>
     *   <li><b>ZSET + score</b>：读路径用 {@code ZREMRANGEBYSCORE} 惰性剔除「score 过旧」的成员，
     *       每个节点只用自己的时间戳续期，崩溃节点的心跳一停就会在下次读取时被摘掉 ——
     *       不需要额外的回收线程（本项目零调度器，见归档文档）。</li>
     * </ul>
     *
     * <p>TTL 只是兜底（键整条消失时不至于永久占位），真正的过期判定靠 score。</p>
     */
    public static final String WS_ONLINE = "bn:ws:online:";

    /**
     * 在线态「多久没心跳算掉线」的秒数：180 秒。
     *
     * <p>取值依据：前端心跳是 25 秒一次，它是「定时发」而非「有操作才发」，
     * 所以后台标签页被浏览器节流到 1 分钟一次也仍有 3 倍余量。
     * 反过来，这一数值也是「实例崩溃后在线态最长失真时间」的上界。</p>
     */
    public static final long WS_ONLINE_TTL_SECONDS = 180;

    // ==================== 缓存（BookNest 论坛读热点） ====================
    //
    // 约定：所有缓存键统一以 bn:cache: 开头，与限流（rate:limit:）等非缓存键区分开，
    // 便于运维按前缀扫描与清理。下面的「Pattern」常量供 SCAN 使用。

    /** 缓存键统一前缀 */
    public static final String CACHE_PREFIX = "bn:cache:";

    /**
     * 标签全表缓存（含实时引用数，已排好序）。
     * Key: bn:cache:tag:all
     * Value: List&lt;Tag&gt; (JSON)
     * 标签增删改时整体删除。标签总量很小（几十到几百条），整表缓存收益最高、失效最简单。
     */
    public static final String TAG_ALL = CACHE_PREFIX + "tag:all";

    /**
     * 书吧全量列表缓存（已过审 + 未禁用，含吧主信息与帖数）。
     * Key: bn:cache:bar:list
     * Value: List&lt;BarVO&gt; (JSON)
     * 吧的创建 / 审核 / 改名 / 换图标 / 删帖都会影响它，统一整体删除。
     */
    public static final String BAR_LIST = CACHE_PREFIX + "bar:list";

    /**
     * 热门书吧榜缓存（按成员数 TopN）。
     * Key: bn:cache:bar:hot:{limit}
     * Value: List&lt;BarVO&gt; (JSON)
     * 原先靠进程内 volatile 缓存 24h —— 多实例部署下各存一份、且 synchronized 重算会串行化请求，
     * 现改为 Redis，多实例共享同一份。
     */
    public static final String BAR_HOT = CACHE_PREFIX + "bar:hot:";

    /**
     * 帖子列表页缓存。
     * Key: bn:cache:post:list:{categoryId|all}:{tagId|all}:{userId|all}:{sort}:{postType|all}:{page}:{pageSize}
     * Value: List&lt;PostVO&gt; (JSON)
     * ⚠️ 只缓存「无个性化状态」的列表。PostVO 里没有「当前用户是否点赞」字段，
     * 所以列表可以安全地被所有用户共享；个性化状态一律走下面的 SET 缓存单独查。
     */
    public static final String POST_LIST = CACHE_PREFIX + "post:list:";

    /** 帖子列表缓存前缀（发帖/删帖/审核后按前缀整体清理） */
    public static final String POST_LIST_PATTERN = POST_LIST + "*";

    /**
     * 帖子详情缓存（含正文全文 + 组装好的元数据 —— 详情接口里最贵的两块）。
     * Key: bn:cache:post:detail:{postId}
     * Value: PostDetailVO (JSON)
     *
     * <p>正文 Markdown 不在数据库里（{@code post.content_url} 存的是 OSS 对象名），
     * 每次打开详情都要去 OSS 下载一次 —— 一次几十到几百毫秒的网络往返，
     * 是详情接口的绝对大头；元数据还要 4 次批量关联查询（分类/书/作者/标签）。</p>
     *
     * <p><b>命中时依然会读一次 post 行</b>，这是刻意的：</p>
     * <ul>
     *   <li><b>鉴权必须用权威数据</b> —— 是否公开、作者是谁、吧主是谁，全部用当次查出的行判断。
     *       若用缓存里的 auditStatus/status 判断，一旦某次 evict 丢失，
     *       已下架 / 未过审的帖子会被漏给所有人；</li>
     *   <li><b>易变字段用行覆盖</b> —— 浏览 / 点赞 / 评论 / 收藏计数、置顶、上下架、审核状态，
     *       这些都是「随时在变」的，命中时一律用当次的行覆盖缓存值，所以它们永远不脏。</li>
     * </ul>
     *
     * <p>缓存真正负责的只有「正文 + 元数据（标题/摘要/封面/分类名/书名/作者/标签）」，
     * 而它们只在<b>编辑</b>与<b>删除</b>时才会变 —— 所以只有这两条写路径需要显式失效
     * （见 {@code PostServiceImpl.doUpdatePost} 与 {@code UserStateCacheServiceImpl.forgetPostEverywhere}）。
     * 审核 / 置顶 / 上下架 / 吧务隐藏都不需要失效，因为读路径会把当次的行值盖回去。</p>
     */
    public static final String POST_DETAIL = CACHE_PREFIX + "post:detail:";

    /**
     * 用户点赞的帖子集合。
     * Key: bn:cache:like:post:{userId}
     * Value: Set&lt;postId&gt;
     * 用于「列表里批量判断当前用户是否点赞」，替代逐条 selectByPostAndUser。
     */
    public static final String LIKE_POST_SET = CACHE_PREFIX + "like:post:";

    /**
     * 用户点赞的评论集合。
     * Key: bn:cache:like:comment:{userId}
     * Value: Set&lt;commentId&gt;
     */
    public static final String LIKE_COMMENT_SET = CACHE_PREFIX + "like:comment:";

    /**
     * 用户收藏的帖子集合。
     * Key: bn:cache:collect:post:{userId}
     * Value: Set&lt;postId&gt;
     */
    public static final String COLLECT_POST_SET = CACHE_PREFIX + "collect:post:";

    /** 集合类状态缓存的通用「已加载」标记后缀（避免用空集合误判为未加载） */
    public static final String SET_LOADED_SUFFIX = ":loaded";

    // ==================== 计数缓冲（先加 Redis，定时批量落库） ====================
    //
    // ⚠️ 这一族键**刻意不放进 bn:cache: 前缀**。bn:cache: 下全是「丢了就从 MySQL 重算」的
    // 派生数据，运维顺手按 bn:cache:* 清一次完全无害；而下面的键里装的是
    // 「已经发生、但还没写进 MySQL」的计数增量 —— 它被清掉就是真的丢数据了。
    // 前缀分开，等于把「可以随手清」和「不能随手清」在键空间上划开。

    /** 计数缓冲键统一前缀 */
    public static final String COUNT_PREFIX = "bn:count:";

    /**
     * 待落库的计数增量（唯一的写入热点键）。
     * Key: bn:count:pending
     * Value: HASH，field = {@code 类型名:业务ID}（如 {@code POST_VIEW:8f14e45f-...}），value = 待累加增量
     *
     * <p>由 {@code CountBuffer.addDelta} 用 HINCRBY 原子累加，由 {@code CountFlushJob}
     * 定时 RENAME 换出后批量落库。<b>不设 TTL</b> —— 它装的是未落库的数据，过期等于丢计数。</p>
     */
    public static final String COUNT_PENDING = COUNT_PREFIX + "pending";

    /**
     * 缓冲 HASH 里的「批次ID」元字段（不是计数项，值为 UUID）。
     *
     * <p>由 {@code CountBuffer} 在 RENAME <b>之前</b>补写进 pending，于是 RENAME 会把
     * 「批次身份」与「本批增量」<b>原子地</b>一起搬进 flushing —— 残留批次因此永远自带身份，
     * 重试时才知道该拿哪个ID 去查 MySQL 的幂等账本（{@code count_flush_ledger}）。</p>
     *
     * <p>写法上刻意与计数 field 区分：计数 field 形如 {@code 类型名:业务ID}（含一个冒号），
     * 而它不含冒号，{@code CountBuffer.typeOf} 认不出它，落库侧也会显式跳过。</p>
     */
    public static final String COUNT_PENDING_META_BATCH = "__batch";

    /**
     * 「已从 pending 换出、正在落库」的暂存区。
     * Key: bn:count:flushing
     * Value: HASH，结构同 COUNT_PENDING，另多一个 {@link #COUNT_PENDING_META_BATCH} 字段
     *
     * <p>存在的意义是崩溃恢复：换出是原子的（RENAME），落库成功才 DEL。
     * 若在两者之间进程挂掉（或删除失败），这批数据仍躺在这里，下一轮会被重新落一次。</p>
     *
     * <p><b>而「重落一次」不会再重复累加</b>：本批的身份随 HASH 一起被搬到这里，
     * 重试时先在 MySQL 的 {@code count_flush_ledger} 里按这个ID 登记 —— 登记不上（已存在）
     * 就只做确认、不碰计数列。这是把「写库成功但确认删除失败」那个窗口关掉的关键一环。</p>
     */
    public static final String COUNT_FLUSHING = COUNT_PREFIX + "flushing";

    /**
     * 落库令牌（保证同一时刻只有一个实例在落库）。
     * Key: bn:count:flush:lock
     * Value: 占位符 "1"
     */
    public static final String COUNT_FLUSH_LOCK = COUNT_PREFIX + "flush:lock";

    // ==================== 过期时间（单位：秒） ====================
    
    public static final long EXPIRE_30_MINUTES = 30 * 60;
    public static final long EXPIRE_1_HOUR = 60 * 60;
    public static final long EXPIRE_2_HOURS = 2 * 60 * 60;
    public static final long EXPIRE_1_DAY = 24 * 60 * 60;
    public static final long EXPIRE_7_DAYS = 7 * 24 * 60 * 60;
    public static final long EXPIRE_14_DAYS = 14 * 24 * 60 * 60;
    public static final long EXPIRE_5_MINUTES = 5 * 60;
    public static final long EXPIRE_10_MINUTES = 10 * 60;

    /**
     * 帖子详情缓存的过期时间：10 分钟。
     * 值里带正文全文，体积随文章长度线性增长（长文可达几十 KB），
     * 所以 TTL 不宜像标签/书吧那样放到小时级 —— 它同时是「元数据脏读」的上界。
     */
    public static final long POST_DETAIL_TTL = 10 * 60;
    public static final long EXPIRE_1_MINUTE = 60;

    /**
     * 计数落库令牌的过期时间：60 秒。
     * 落库本身是「几条 UPDATE」的量级，60 秒是三个数量级的余量；
     * 它的作用只是兜住「持锁进程被 kill」的情况 —— 否则锁不释放，落库就永久停摆了。
     */
    public static final long COUNT_FLUSH_LOCK_TTL = 60;
}
