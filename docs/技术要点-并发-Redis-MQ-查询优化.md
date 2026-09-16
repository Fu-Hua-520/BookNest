# BookNest 技术要点盘点：并发 / Redis / MQ / 查询优化

> 逐处标注文件与行号，路径相对仓库根 `D:\FuHua520\booknest`。
> 行号基于当前代码快照，改动后请以实际为准。

---

## 一、并发性

### 1.1 异步线程池：AI 机器人回复

| 位置 | 内容 |
| --- | --- |
| `booknest_backend/booknest-server/src/main/java/com/fuhua/booknest/server/ai/AiBotExecutorConfig.java` L27 | 线程池 Bean 名常量 `aiBotReplyExecutor` |
| 同上 L34-49 | `ThreadPoolTaskExecutor`：core=2 / max=4 / queue=100 / keepAlive=60s / 线程名前缀 `ai-bot-reply-` |
| 同上 L42 | 拒绝策略 `ThreadPoolExecutor.AbortPolicy`（队列满即丢弃，不阻塞发评论线程） |
| 同上 L44-45 | 优雅停机 `setWaitForTasksToCompleteOnShutdown(true)` + `awaitTerminationSeconds(20)` |
| `.../service/impl/AiBotReplyServiceImpl.java` L70-72 | `@Qualifier(AiBotExecutorConfig.EXECUTOR_BEAN_NAME)` 注入该池 |
| 同上 L121-129 | `aiBotReplyExecutor.execute(() -> replyOne(...))` 投递；L125-128 捕获 `RejectedExecutionException` 只记日志 |
| 同上 L157-178 | `replyOne` 实际执行体（幂等判定 + 下载正文 + 调模型 + 落库） |

### 1.2 “事务提交后执行”（afterCommit）——本项目并发的核心模式

| 位置 | 内容 |
| --- | --- |
| `.../mq/NotificationProducer.java` L51-66 | `sendNotification(...)`：`isSynchronizationActive()` 为真则注册 `TransactionSynchronization.afterCommit`，否则同步发 |
| `.../service/impl/PostInteractionServiceImpl.java` L162-173 | 私有助手 `runAfterCommit(Runnable)` —— 本类“慢活出事务”的统一入口 |
| 同上 L129-136 | 评论后：通知（MQ）+ 回复通知 + 吧内经验，全部挪到 afterCommit |
| 同上 L203-224 | `triggerAiBotsAfterCommit(post, comment)` —— AI 触发必须等评论提交 |
| 同上 L548-555 | 点赞后：点赞通知 + 吧内经验挪到 afterCommit |
| `.../service/impl/PostServiceImpl.java` L511-522 | 同类助手 `runAfterCommit(Runnable)` |
| 同上 L449-453 | 编辑帖子：旧正文 OSS 删除注册在 afterCommit |
| 同上 L493-497 | 删除帖子：正文 OSS 删除注册在 afterCommit |
| `.../service/impl/AiBotReplyServiceImpl.java` L346-347 | 通知走 MQ 放在事务外 |

### 1.3 显式事务模板（规避自调用 `@Transactional` 静默失效）

| 位置 | 内容 |
| --- | --- |
| `.../service/impl/PostServiceImpl.java` L100-115 | 字段 `TransactionTemplate txTemplate` + `@PostConstruct initTxTemplate()` |
| 同上 L148-153 | 先 `uploadContentOutsideTransaction`（事务外）→ 再 `txTemplate.execute(doPublishPost)` |
| 同上 L414 | `txTemplate.executeWithoutResult(status -> doUpdatePost(...))` |
| 同上 L469 | `deletePost` 仍是 `@Transactional`（经接口代理调用，无自调用问题） |
| `.../service/impl/AiBotReplyServiceImpl.java` L74-84 | 同类写法：`txTemplate` + `@PostConstruct` |
| 同上 L340-344 | `txTemplate.executeWithoutResult(...)` 三条写操作（评论 + 计数 + 机器人计数）同一事务 |

### 1.4 ThreadLocal 请求上下文与跨线程传递

| 位置 | 内容 |
| --- | --- |
| `booknest-common/.../common/context/BaseContext.java` | `ThreadLocal<String>` 当前登录用户 ID |
| `.../interceptor/JwtTokenUserInterceptor.java` L86 | 验签通过后写入 ThreadLocal |
| 同上 L119 | `afterCompletion` 清理，防内存泄漏 |
| `.../interceptor/JwtTokenAdminInterceptor.java` L63 / L77 | 管理端同样一套写入 / 清理 |
| `.../ai/AiBotTriggerContext.java` L11-21、L30-37 | 异步任务上下文快照对象；类注释明确“异步线程只认它、不再碰任何 ThreadLocal” |

### 1.5 并发写安全：唯一键 + 幂等 + 影响行数

| 位置 | 内容 |
| --- | --- |
| `.../service/impl/PostInteractionServiceImpl.java` L466-468 | 取消评论点赞：`deleteByCommentAndUser(...) > 0` 才扣计数 |
| 同上 L471-481 | 评论点赞 insert 捕获 `DuplicateKeyException` 当幂等成功 |
| 同上 L518-520 | 取消帖子点赞：按影响行数扣减，防并发减成负数 |
| 同上 L526-536 | 帖子点赞 insert 兜 `DuplicateKeyException` |
| 同上 L589-591、L595-604 | 收藏的取消 / 新增同样处理 |
| `.../mq/NotificationConsumer.java` L65-70 | MQ 重复投递：`insertIgnore` 返回 0 行 → debug 日志正常返回 |
| `.../service/impl/PostServiceImpl.java` L829-841 | `rebuildPostTags` 用 `LinkedHashSet` 去重，防撞 `uk_post_tag` 整篇回滚 |
| SQL：`booknest_data/sql/booknest_interaction_tables.sql` L50 / L64 / L78 | `uk_comment_user` / `uk_post_user`（点赞、收藏）唯一键 = 并发仲裁者 |

### 1.6 并发容器与进程内缓存

| 位置 | 内容 |
| --- | --- |
| `.../websocket/ChatWebSocketHandler.java` L39 | `static final Map<String, Set<WebSocketSession>> SESSIONS = new ConcurrentHashMap<>()` |
| 同上 L78 | `SESSIONS.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session)` |
| 同上 L90-97 | 断连时 `sessions.remove`，仅当集合空才 `SESSIONS.remove(userId, sessions)`（防并发误删新连接） |
| 同上 L222-236 | `isUserOnline` 遍历会话判 `isOpen()` |
| `.../ai/AiBotChatClientFactory.java` L43 | `ConcurrentHashMap<String, ChatClient> cache` |
| 同上 L64-68 | 缓存上限 `CHAT_CLIENT_CACHE_LIMIT`，超限整体 `clear()` |
| 同上 L80-85 | `evict(botId)`：前缀匹配清除该机器人的实例 |
| `.../service/impl/AiBotServiceImpl.java` L51-52 | `volatile Map<String,String> triggerIndex` + `volatile long triggerIndexLoadedAt` |
| 同上 L333-358 | `triggerIndexOf()` 双检锁（`synchronized(this)` L339）+ 60s TTL 重建触发索引 |
| 同上 L361-364 | `invalidateTriggerIndex()` 审核/启停/增删改后立即失效 |
| `.../service/impl/CategoryServiceImpl.java` L74-78 | 注释记录：原 `volatile + synchronized` 进程内缓存已替换为 Redis（多实例 / 串行化问题） |

### 1.7 并发消费与连接池

| 位置 | 内容 |
| --- | --- |
| `booknest-server/src/main/resources/application.yml` L58-62 | `listener.simple.concurrency=2` / `max-concurrency=4` / `prefetch=10`（均可用 `RABBITMQ_*` 覆盖） |
| 同上 L63-67 | 消费重试 3 次、指数退避 3s→6s→12s |
| `.../config/RabbitMQConfig.java` L169-185 | `setConcurrentConsumers` / `setMaxConcurrentConsumers` / `setPrefetchCount` 显式兜底 |
| `application.yml` L18-23 | Druid 数据源（业务与 MQ 消费者共用同一连接池） |
| 同上 L31-36 | Lettuce 连接池 `max-active: 8` / `max-idle: 8` / `max-wait: 100ms` |

### 1.8 事务边界（`@Transactional` 分布）

- `.../service/impl/PostInteractionServiceImpl.java` L75 `publishComment`、L375 `deleteComment`、L452 `toggleCommentLike`、L498 `togglePostLike`、L573 `togglePostCollect`
- `.../service/impl/PostServiceImpl.java` L469 `deletePost`
- `.../service/impl/BarServiceImpl.java` L109 `follow`、L131 `unfollow`、L154 `addModerator`、L180 `setTitles`、L242 `setPostVisible`、L265 `addExp`、L337 `adminSetOwner`、L362 `adminAddModerator`、L369 `adminRemoveModerator`、L376 `adminSetTitles`
- 递归遍历防环（并发/脏数据下的死循环保护）：`PostInteractionServiceImpl.java` L423-449 `collectReplyIds` / `collectReplyIdsRecursive`（`visited` 集合）

### 1.9 前端并发

| 位置 | 内容 |
| --- | --- |
| `booknest_frontend/src/utils/websocket.js` L73-78 | 25s 心跳 `setInterval` 发 `ping` |
| 同上 L87-96 | `scheduleReconnect`：指数退避 `1000 * 2^attempts`，上限 30s；L88 防重复定时器 |
| 同上 L55-59 | `onclose` 触发重连（`onerror` 只 `close()`，避免双份重连） |
| `booknest_frontend/src/stores/badge.js` L8 | `POLL_INTERVAL = 60000` 轮询未读角标 |
| 同上 L29-33 | `Promise.allSettled` 并发聚合三个角标接口（单个失败不影响其它） |
| 同上 L45-56 | `startPolling` / `stopPolling` 生命周期管理 |
| `booknest_frontend/src/views/PostDetailView.vue` L412 | `aiPollTimer = setInterval(...)` 轮询 AI 回复；L580 `focusTimer = setTimeout(...)` |
| `booknest_frontend/src/utils/format.js` L94 | `debounce`（`setTimeout` 实现） |

---

## 二、Redis

### 2.1 基础设施

| 位置 | 内容 |
| --- | --- |
| `.../config/RedisConfig.java` L53-74 | `redisObjectMapper`：`activateDefaultTyping(NON_FINAL)`（L62-65）+ `JavaTimeModule`（L67）；不覆盖容器主 ObjectMapper |
| 同上 L82-99 | `@Primary RedisTemplate<String,Object>`：key 用 String、value 用 `GenericJackson2JsonRedisSerializer` |
| `.../common/RedisCacheHelper.java` | 唯一入口门面，**全方法 fail-open**（异常降级为未命中，不抛业务） |
| 同上 L47 | `get` / L59 `set` / L68 `evict` |
| 同上 L84 | `setContains` / L104-142 `setContainsBatch`（pipeline 打包 N 个 SISMEMBER → 1 次往返） |
| 同上 L145 `setAdd` / L159 `setRemove` / L171 `setReplaceAll` / L185 `setMembers` |
| 同上 L213 `isLoaded` / L222 `markLoaded` / L227 `clearLoaded` |
| 同上 L242-269 | `evictByPattern` —— 用 **SCAN 而非 KEYS**，每 200 个一批 DEL |
| 同上 L282-292 | `getOrLoad`：先查缓存 → 未命中回源 → 空集合不写缓存（L294-303） |
| 同上 L314 `key(...)` / L325 `loadedKey(...)` | 键拼装统一入口 |
| `booknest-common/.../common/constant/RedisConstant.java` | 键常量集中定义 |
| 同上 L115 | `RATE_LIMIT = "rate:limit:"`（限流族） |
| 同上 L152 | `CACHE_PREFIX = "bn:cache:"`（缓存族） |
| 同上 L160 `TAG_ALL` / L168 `BAR_LIST` / L177 `BAR_HOT` / L186 `POST_LIST` / L189 `POST_LIST_PATTERN` |
| 同上 L197 `LIKE_POST_SET` / L204 `LIKE_COMMENT_SET` / L211 `COLLECT_POST_SET` |
| 同上 L218-226 | 过期时间常量（5 分钟 ~ 14 天） |

### 2.2 限流

| 位置 | 内容 |
| --- | --- |
| `.../controller/CommonController.java` L129-145 | `checkUploadRate`：`increment` + 首次 `expire` 1 分钟 |
| 同上 L138-142 | Redis 异常 → 记 WARN 并**放行**（fail-open） |
| 同上 L64-67 | 超限返回 `UPLOAD_TOO_FREQUENT` |
| `application.yml` L144-145 | `booknest.upload.rate-limit-per-minute: 20` |

### 2.3 业务读缓存（`bn:cache:`）

| 位置 | 内容 |
| --- | --- |
| `.../service/impl/PostServiceImpl.java` L302-308 | 帖子列表缓存键（含全部筛选维度 + 页码）+ `cache.get` |
| 同上 L310-321 | 回源后 **只缓存第 1 页**（`page == 1`），TTL 5 分钟 |
| 同上 L753-755 | `evictPostListCache()` → 按前缀整体清 |
| `.../service/impl/CategoryServiceImpl.java` L212-222 | `listBars()`：`BAR_LIST` 缓存，TTL 1 小时 |
| 同上 L305-316 | `listHotBars()`：`BAR_HOT:{limit}` 缓存，TTL 1 小时 |
| 同上 L523-527 | `evictBarCache()`：`evict(BAR_LIST)` + `evictByPattern(BAR_HOT + "*")` |
| `.../service/impl/TagServiceImpl.java` L246 / L252 | `TAG_ALL` 整表缓存读取 / 写入（TTL 1 小时） |

### 2.4 用户点赞 / 收藏状态 Set 缓存

| 位置 | 内容 |
| --- | --- |
| `.../service/UserStateCacheService.java` | 接口定义 |
| `.../service/impl/UserStateCacheServiceImpl.java` L36-37 | `STATE_TTL_SECONDS = EXPIRE_7_DAYS` |
| 同上 L52-82 | 单条判定：`isPostLiked` / `isCommentLiked` / `isPostCollected` |
| 同上 L87-114 | 批量判定：`filterLikedPosts` / `filterLikedComments` / `filterCollectedPosts` |
| 同上 L118-131 | 写路径：`markPostLiked` / `markCommentLiked` / `markPostCollected` |
| 同上 L152-165 | `containsWithFallback`：快路径 → 回源加载 → 降级直查库 |
| 同上 L172-184 | `filterWithFallback`：Redis 不可用时返回空集合交由调用方降级 |
| 同上 L191-204 | `tryLoadAll`：DB 全量加载 + `setReplaceAll` + `markLoaded` |
| 同上 L220-232 | `writeState`：**仅在 `loaded` 标记存在时**才 SADD / SREM |
| 同上 L234-236 | `loadedKey` = `bn:cache:loaded:` + setKey |
| 消费点：`PostInteractionServiceImpl.java` L295、L461、L485、L511、L541、L569、L584、L608、L622 |

### 2.5 缓存失效点清单（写路径必须显式 evict）

| 位置 | 触发的失效 |
| --- | --- |
| `PostServiceImpl.java` L203（发帖）、L447（编辑）、L489（删帖） | 帖子列表前缀 |
| `PostAdminServiceImpl.java` L36-38 定义，L67（审核）、L80（置顶）、L95（上下架）调用 | 帖子列表前缀 |
| `BarServiceImpl.java` L259 `setPostVisible` | 帖子列表前缀 |
| `CategoryServiceImpl.java` L147（建吧）、L187（改吧）、L206（删吧）、L286（申请）、L468（审核） | `BAR_LIST` + `BAR_HOT:*` |
| `TagServiceImpl.java` L265 | `TAG_ALL`（用户端 `getOrCreateByName` 也走这里） |
| `UserStateCacheServiceImpl.java` L138 `forgetPostEverywhere` | 帖子列表前缀 |

---

## 三、MQ（RabbitMQ）—— 用于异步通知落库

### 3.1 拓扑与配置

| 位置 | 内容 |
| --- | --- |
| `.../config/RabbitMQConfig.java` L32-36 | 交换机 `booknest.notification.topic` / 队列 `booknest.notification.queue` / 路由键 `booknest.notification` |
| 同上 L39-43 | 死信交换机 `...dlx` / 死信队列 `...dlx.queue` / 死信路由键 |
| 同上 L48-94 | `TopicExchange` / `Queue` / `Binding` 声明；业务队列 L59-60 绑定死信参数 |
| 同上 L99-103 | `Jackson2JsonMessageConverter("com.fuhua.booknest.server.mq")` 白名单 |
| 同上 L134-157 | 自定义 `RabbitTemplate` + `setConfirmCallback`（L142-149）+ `setReturnsCallback`（L152-154） |
| 同上 L135-139 | ⚠️ 注入 `RabbitTemplateConfigurer` 并先 `configurer.configure(template, cf)`，否则 yml 的 `mandatory` / retry **静默失效** |
| 同上 L168-185 | `SimpleRabbitListenerContainerFactory` 同理用 `SimpleRabbitListenerContainerFactoryConfigurer`（L178） |
| `application.yml` L37-43 | 连接配置（host/port/user/password/vhost） |
| 同上 L48-51 | `publisher-confirm-type: correlated`、`publisher-returns: true`、`template.mandatory: true` |

### 3.2 生产端

| 位置 | 内容 |
| --- | --- |
| `.../mq/NotificationProducer.java` L39-41 | `sendNotification(receiverId, type, content, sourceId)` 四参重载 |
| 同上 L51-66 | 五参重载（带 `anchorId`）；L56-66 `afterCommit` 投递逻辑 |
| 同上 L73-83 | `doSend` 真正投递，异常只记日志 |
| 同上 L75 | `rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message)` |
| `.../mq/NotificationMessage.java` | 消息体（receiverId / type / content / sourceId / anchorId） |

**生产者调用点：**

| 位置 | 场景 |
| --- | --- |
| `PostInteractionServiceImpl.java` L651 | 点赞通知（`TYPE_LIKE`） |
| 同上 L776-777 | 回复通知（`TYPE_REPLY`，带 anchorId = 新评论 ID） |
| 同上 L131 | 评论通知（`TYPE_COMMENT`） |
| `FollowServiceImpl.java` L65-66 | 关注通知（`TYPE_FOLLOW`，sourceId = 关注者 userId） |
| `AiBotReplyServiceImpl.java` L363-364 | AI 回复通知（`TYPE_AI_REPLY`，sourceId=帖子、anchorId=AI 回复 ID） |

### 3.3 消费端与幂等

| 位置 | 内容 |
| --- | --- |
| `.../mq/NotificationConsumer.java` L53-54 | `@RabbitListener(queues = RabbitMQConfig.QUEUE)` |
| 同上 L65-70 | `notificationMapper.insertIgnore(...)` 返回 0 行 → debug 日志正常返回 |
| 同上 L71-75 | 真异常才抛出 → 交给 Spring AMQP 重试 + DLX |
| `.../mapper/NotificationMapper.java` | `insertIgnore` 方法声明 |
| `.../resources/mapper/NotificationMapper.xml` L31-34 | `insert ignore into ... notification` |
| `booknest_data/sql/booknest_social_tables.sql` L53-54 | 生成列 `dedup_key` = `CONCAT_WS(':', receiver_id, type, COALESCE(source_id,''), COALESCE(anchor_id,''))` STORED |
| 同上 L63 | `UNIQUE KEY uk_notification_dedup (dedup_key)` |
| `booknest_data/sql/upgrade/2026-09-14-notification-dedup-upgrade.sql` L136-152 | 生成列 + 唯一键的升级脚本（含幂等判断） |

> ⚠️ 唯一键必须建在**生成列**上：MySQL 唯一索引逐列判等、NULL 之间互不相等；LIKE/COMMENT/FOLLOW 的 `anchor_id` 恒为 NULL，直接建四列键等于没建。

---

## 四、查询优化

### 4.1 批量预取，消除 N+1

| 位置 | 内容 |
| --- | --- |
| `.../service/impl/PostServiceImpl.java` L552-569 | `toPostVOList` 批量组装（注释：原一页 10 篇约 **70 次查询**） |
| 同上 L579-592 | `PostAssembly` 快照（categories / books / users / tagMap） |
| 同上 L595-686 | `prefetchForPosts`：分类 / 书 / 作者各一次 IN 查询；标签走两层批量（L646-683） |
| 同上 L689-743 | `toPostVO(post, ctx)` 循环内**零查询** |
| 同上 L547-549 | 单条路径 `toPostVO` 复用同一快照（保证字段语义不分叉） |
| `.../service/impl/PostInteractionServiceImpl.java` L253-262 | 注释：原 1 + 5N 次查询 → 常数次 |
| 同上 L268-277 | `CommentAssembly` 快照（likedCommentIds / users / bots / parentComments） |
| 同上 L285-345 | `prefetchForComments`：点赞态走 Redis 批量 + 人/机器人/父评论批量捞 |
| 同上 L681-715 | `buildCommentVO(comment, currentId, ctx)` 循环内零查询 |
| `.../service/impl/CategoryServiceImpl.java` L542-559 | `loadPostCounts`：一次 `countPostsGroupByCategory` 替代循环 count（注释直接点明“避免 N+1”） |
| 同上 L324-336 | `buildHotBars` 用 `countGroupByBar` 一次取回成员数 |
| `.../service/impl/ChatServiceImpl.java` L141-144 | 同页消息群名只取一次（避免逐条回查） |

### 4.2 批量 SQL

| 位置 | 内容 |
| --- | --- |
| `.../resources/mapper/PostMapper.xml` L101-106 | `selectByIds`：`id in (...)` |
| 同上 L30-71 | `list`：条件拼接 + 排序白名单分支 |
| `.../resources/mapper/PostTagMapper.xml` | `listTagRowsByPostIds`（扁平行，供内存分组）、`batchInsert` |
| `.../resources/mapper/TagMapper.xml` L67-75 | `listByIds`：一次 IN 替代循环 `selectById` |
| `.../resources/mapper/PostCommentMapper.xml` L36-38 | `listByPostId` 单次取全部评论（前端两层组装） |

### 4.3 索引清单

| 表 | 索引 | 位置 |
| --- | --- | --- |
| `post` | `idx_user_id`、`idx_status_publish_time`、`idx_category_publish_time`、`idx_publish_time`、`idx_view_count`、`idx_post_type_publish_time` | `booknest_data/sql/booknest_content_tables.sql` L111-116 |
| `post_tag` | `uk_post_tag(post_id, tag_id)`、`idx_tag_id` | 同上 L130-131 |
| `tag` | `uk_name`、`idx_use_count` | 同上 L79-80 |
| `category` | `idx_parent_id`、`idx_sort_order`、`idx_owner_id`、`idx_audit_status` | 同上 L60-63 |
| `book` | `idx_title`、`uk_isbn` | 同上 L37-38 |
| `booklist` / `booklist_item` | `idx_user_id`、`idx_visibility` / `idx_booklist_id`、`idx_book_id` | 同上 L152-153、L169-170 |
| `user` | `idx_account`、`idx_username`、`idx_phone`、`idx_email`、`idx_user_level`、`idx_status` | `booknest_data/sql/user_table.sql` L33-38 |
| `post_comment` | `idx_post_id`、`idx_reply_id`、`idx_comment_bot` | `booknest_data/sql/booknest_interaction_tables.sql` L34-36 |
| `post_comment_like` / `post_like` / `post_collect` | `uk_comment_user` / `uk_post_user` / `uk_post_user` | 同上 L50、L64、L78 |
| `notification` | `idx_receiver`、`idx_receiver_read`、`uk_notification_dedup(dedup_key)` | `booknest_data/sql/booknest_social_tables.sql` L57-58、L63 |
| `user_follow` | `uk_follower_followee` | 同上 L28 |
| `user_browse_history` | `uk_user_post`、`idx_user_time`、`idx_post` | 同上 L81-83 |
| `ai_bot` | `uk_bot_name`、`idx_bot_owner`、`idx_bot_audit` | `booknest_data/sql/booknest_ai_bot_tables.sql` L49-51 |
| `chat_conversation` | `uk_user_pair` | `booknest_data/sql/booknest_chat_tables.sql` L29 |
| `chat_group` | `idx_owner`、`idx_last_msg_at` | 同上 L46-47 |
| `chat_group_member` | `uk_group_user`、`idx_user` | 同上 L64-65 |
| `chat_message` | `idx_conversation`、`idx_group_time` | 同上 L85-86 |
| `chat_group_invitation` | `idx_group_type_status`、`idx_invitee_type_status` | 同上 L108-109 |
| `bar_member` | `uk_bar_user`、`idx_user`、`idx_bar_exp(bar_id, exp)` | `booknest_data/sql/upgrade/2026-09-13-bar-community-upgrade.sql` L66-69 |
| `bar_moderator` | `uk_bar_user`、`idx_user` | 同上 L44-46 |
| `bar_level_title` | `uk_bar_level` | 同上 L84-85 |

### 4.4 冗余列的取舍：实时子查询替代计数维护

| 位置 | 内容 |
| --- | --- |
| `.../resources/mapper/TagMapper.xml` L9-24 | 大段注释说明：`tag.use_count` 降级为遗留列，所有读路径用 `post_tag` 实时统计 |
| 同上 L33-38、L41-46、L49-54、L57-63、L67-75、L78-84 | 6 处 select 统一带相关子查询 `(select count(*) from post_tag pt where pt.tag_id = t.id)` |
| 同上 L21-22 | 说明该子查询走 `idx_tag_id`，标签量级小无压力 |

### 4.5 其它

| 位置 | 内容 |
| --- | --- |
| `.../resources/mapper/NotificationMapper.xml` L14-16 | `Base_Column_List` 显式列清单替代 `select *`（生成列无实体属性） |
| `application.yml` L99-103 | `map-underscore-to-camel-case: true` 结果映射 |
| 同上 L105-110 | PageHelper：`helper-dialect: mysql`、`reasonable: true`、`count=countSql` |
| `.../service/impl/PostServiceImpl.java` L341-366 | 收藏列表：两次查询（收藏 ID 分页 + `selectByIds` 回捞）+ 按收藏顺序还原 |
| `.../controller/CommonController.java` L93-101 | 内容寻址存储：objectName = `{type}/{MD5}{ext}`，同文件天然去重 |
| `.../utils/AliOssUtil.java` | `doesObjectExist()` 先判存在再上传（复用） |
| `.../service/impl/AiBotServiceImpl.java` L286 | `aiBotMapper.selectByIds` 批量取机器人 |
| `.../resources/mapper/PostMapper.xml` L148-155 | `searchByKeyword`：`limit` 限制召回量 |

---

## 五、已知隐患（可继续优化）

| 位置 | 问题 |
| --- | --- |
| `.../service/impl/BarServiceImpl.java` L266-296 | `addExp` 是**读改写**（`selectByBarAndUser` → 内存累加 → `updateExp` 全量覆盖），并发点赞/评论会丢经验、绕过每日上限 |
| `.../websocket/ChatWebSocketHandler.java` L39 | `SESSIONS` 是**进程内 static Map**，多实例部署下推送 / 在线查询失效 |
| `.../service/impl/ChatServiceImpl.java` L40-63 | `getConversations` N+1：每个会话 2 次查询（对方用户 + 未读数） |
| `.../mapper/ChatMapper.xml` | `countUnread` 全表扫（未走覆盖索引） |
| `.../resources/mapper/PostMapper.xml` L55-57 | `hot` 排序是表达式排序 `(view_count + like_count*5 + comment_count*3)`，**走不了索引** |
| `.../service/impl/PostInteractionServiceImpl.java` L310-321 | 父评论按 `parentCommentIds` 逐个 `selectById`（循环内查询，量小可接受） |
| `.../service/impl/CategoryServiceImpl.java` L561-570、L674-682 | `toBarVO` / `toAdminVO` 内逐个 `userMapper.getUserById(ownerId)`（未批量） |
| `.../service/impl/FollowServiceImpl.java` L86-91、L102-107 | 关注/粉丝列表逐个 `getUserById`（N+1） |

---

## 附：一图看懂「写路径」的并发编排

```
HTTP 请求线程
  │
  ├─ ① 事务外慢活：OSS 上传正文（PostServiceImpl.uploadContentOutsideTransaction）
  │
  ├─ ② 开事务（txTemplate / @Transactional）
  │     ├─ 写主表（post / post_comment）
  │     ├─ 写计数（incrementXxxCount，DB 自增，非读改写）
  │     └─ 唯一键兜并发（uk_post_user / uk_comment_user，DuplicateKeyException 当幂等）
  │
  └─ ③ 注册 afterCommit（仍在本线程，事务已提交、连接已归还）
        ├─ MQ 投递通知 ──→ RabbitMQ ──→ NotificationConsumer ──→ INSERT IGNORE（幂等）
        ├─ 吧内经验 addExpQuietly（吞异常）
        ├─ OSS 删除旧正文（不可逆副作用）
        └─ AI 机器人触发 ──→ aiBotReplyExecutor（core2/max4/queue100/AbortPolicy）
                               └─ 线程池线程：下载正文 → 调模型（10s/超时）→ txTemplate 落库 → MQ 通知
```
