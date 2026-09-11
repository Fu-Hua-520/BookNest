# 书籍论坛项目技术方案（BookNest）

> 版本 v1.0（定稿）｜状态：执行中

## 1. 项目概述

- **项目名**：booknest（软件包名 `com.fuhua.booknest`）
- **定位**：普通书籍交流社区——发书评、聊书、建书单、关注书友。不做"猜你喜欢"推荐系统，AI 是通用论坛智能助手。
- **目标用户**：读者、写书评的深度用户、书单爱好者、共读书友。
- **核心能力**：以「书」为纽带的内容社区 + 通用 AI 助手（查帖/找书/总结/答疑，RAG 检索论坛内容）。

## 2. 技术选型（定稿）

| 层 | 选型 | 说明 |
|---|---|---|
| 后端 | Java 17 + Spring Boot 3.5.x（最新 GA） | 匹配 Spring AI |
| AI 框架 | Spring AI 1.0.x（最新 GA） | 替换 LangChain4j |
| 对话模型 | DeepSeek `deepseek-chat` | OpenAI 兼容端点 `https://api.deepseek.com`，支持流式 + Function Calling；`deepseek-reasoner` 可选 |
| Embedding | 阿里云 DashScope `text-embedding-v3` | 端点 `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| 向量库 | Qdrant（本地容器，gRPC 6334） | 官方 starter `spring-ai-starter-vector-store-qdrant` 自动装配 |
| 对象存储 | 阿里云 OSS（无 MinIO） | 帖子正文/封面/书籍封面 |
| 持久层 | MyBatis + MySQL 8 + Druid + PageHelper | |
| 缓存 | Redis | 缓存/额度/未读数/去重标记 |
| 消息 | RabbitMQ | 通知异步解耦 |
| 实时 | WebSocket | 用户私信 |
| 前端 | Vue 3 + Vite + Pinia + vue-router + axios + marked + highlight.js | SSE 用原生 fetch + ReadableStream |
| 部署 | Docker Compose + Nginx + GitHub Actions CI/CD | 环境变量脱敏（.env） |

**Spring AI 依赖（1.0.x）**：
- `spring-ai-starter-model-openai`：Chat 指向 DeepSeek，Embedding 指向 DashScope（OpenAI 兼容协议，两个 Bean 分别注入 key/base-url/model）。
- Qdrant 向量库：有官方 starter，`spring.ai.vectorstore.type=qdrant` 即自动装配 `QdrantClient` 与 `VectorStore`，无需自写实现。

## 3. 系统架构

```
booknest-backend (父，artifactId=booknest-backend，packaging=pom)
├── booknest-pojo      # 实体/DTO/VO
├── booknest-common    # constant/context/enum/exception/properties/result/utils
└── booknest-server    # controller(user/admin)/service/mapper/agent/config/.../mq/task/websocket
```

> 命名约定：父工程与三个子模块统一用连字符（`booknest-` 前缀）。这样 IDEA 的模块树会归到同一个
> `booknest` 组节点下；若父工程用下划线（旧名 `booknest_backend`），它不会被前缀分组，在模块树里会落单。

基础包名：`com.fuhua.booknest`，子模块包：
- `com.fuhua.booknest.pojo`
- `com.fuhua.booknest.common`
- `com.fuhua.booknest.server`

## 4. 数据模型

| 表 | 说明 | 关键字段 |
|---|---|---|
| `user` | 用户 | id(UUID)、account、username、password(MD5)、avatar、user_level、role(USER/ADMIN)、status |
| `book` | 书籍元数据 | id(UUID)、title、author、isbn(唯一)、cover_url、description、publisher、publish_date、rating、rating_count、source |
| `post` | 帖子/书评 | id(UUID)、book_id(可空)、title、summary、content_url(OSS)、cover_image、category_id、author_id、view/like/comment/collect_count、audit_status、is_top |
| `post_comment` | 评论 | id、post_id、user_id、parent_id、content、like_count、status |
| `post_like` | 点赞 | id、post_id、user_id、唯一(post_id,user_id) |
| `booklist` | 书单 | id(UUID)、user_id、title、summary、cover_image、visibility、like/collect_count、book_count |
| `booklist_item` | 书单条目 | id、booklist_id、book_id、sort_order、note |
| `category` | 两级分类 | id、name、parent_id、sort |
| `tag` | 标签 | id、name(unique) |
| `post_tag` | 帖子-标签 | post_id、tag_id |
| `user_follow` | 关注 | id、follower_id、followee_id、唯一 |
| `notification` | 通知 | id(自增)、receiver_id、type、content、source_id、is_read、created_at |
| `ai_conversation` | AI 会话 | id(UUID)、user_id、title、model、message_count、is_pinned、status |
| `ai_message` | AI 消息 | id(UUID)、conversation_id、role、content、token_count、tool_calls(JSON)、recommendations(JSON)、model、is_error |
| `chat_conversation`/`chat_msg` | 私信 | user1_id/user2_id / sender_id、receiver_id、content、is_read |

书籍元数据来源：手动录入为主 + ISBN 走 Google Books / Open Library 自动补全。

## 5. 功能模块

- 用户体系（注册/登录/JWT/关注/个人主页）
- 帖子/书评（Markdown + 审核 + 置顶 + 分类/标签）
- 书单（自建收藏，公开/私密）
- 书籍库（book 元数据 + ISBN 补全 + 书籍详情聚合页）
- 评论/回复/点赞/收藏/关注
- 通知（RabbitMQ 异步解耦）
- 私信（WebSocket）
- AI 助手（通用问答 + 论坛内容 RAG）
- 管理后台

## 6. AI 助手设计（Spring AI）

### 6.1 双模型 Bean
- ChatModel/StreamingChatModel → DeepSeek（base-url https://api.deepseek.com, model deepseek-chat, key DEEPSEEK_API_KEY）
- EmbeddingModel → 阿里云 DashScope（base-url https://dashscope.aliyuncs.com/compatible-mode/v1, model text-embedding-v3, key DASHSCOPE_API_KEY）

### 6.2 工具调用
`@Tool`/`@ToolParam` 暴露 searchPosts / getBookInfo / listBooklists / getPostContent，经 `ChatClient.defaultTools(...)` 注册。

### 6.3 RAG 链路
定期全量重建（默认 30 天）：清空 Qdrant 集合 → 取点赞量前 N（默认 100）的已发布且已过审帖子 → 分块(512字/50重叠) → EmbeddingModel 向量化 → 写入 Qdrant。
检索：在当前热门帖范围内向量召回(topK×8) → 按帖子去重 → 取 Top-5 注入 System Prompt。

**为什么是全量重建而不是逐条增量**：不用判重、不用在审核/上下架时挂钩子增删向量，热度排行变动下一次刷新自动生效；代价是新帖子最长延迟一个周期才能被检索到。

### 6.4 流式 SSE
`StreamingChatModel.stream()` → Flux<ChatResponse> → 适配器 → Flux<ChatStreamEvent> → SSE。ChatStreamEvent 契约：THINKING/TOOL_CALLING/TOOL_RESULT/MESSAGE/DONE/ERROR + tokenUsage + toolCall + recommendations。

### 6.5 沿用
额度管控（Redis ai:quota:{userId}，免费 10 次）、帖子 AI 摘要（Redis 缓存 14 天）、联网搜索（Tavily 封装 @Tool）。

### 6.6 风险点
- zvector 无官方 VectorStore starter → P4 冒烟验证自写实现
- Chat/Embedding 分属两家 → 两个独立 Bean
- DeepSeek 工具调用时序 → 适配层统一
- embedding 维度一致性 → 固定 text-embedding-v3

## 7. 前端设计

页面：首页、帖子详情/编辑、书籍详情页、书单列表/详情/创建、分类/标签页、搜索页、AI 助手页、私信页、个人主页、通知页、登录/注册、管理后台。

路由：`/`、`/book/:id`、`/post/:id`、`/post/edit`、`/booklist/:id`、`/booklist/create`、`/assistant`、`/chat`、`/user/:id`、`/notification`、`/search`、`/category/:id`、`/login`、`/register`、`/admin/**`。

## 8. 基础设施与部署

- docker-compose：mysql:8.0、redis:7、rabbitmq:3-management、app、web（无 minio，OSS 外接；zvector 走云服务）。
- .env：DB_PASSWORD、REDIS_*、RABBITMQ_USER/PASS、OSS_*、DEEPSEEK_API_KEY、DASHSCOPE_API_KEY、JWT_SECRET、TAVILY_API_KEY(可选)。
- CI/CD：GitHub Actions → Maven 打包 → docker build & push → docker compose up -d。

## 9. 实施路线图

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| P1 | 多模块骨架、JWT 用户体系、基础配置、.env | 可注册登录、JWT 拦截器生效 |
| P2 | Book 元数据 + ISBN 补全、帖子/书单 CRUD、OSS 接入 | 发帖→审核→展示；书单含条目 |
| P3 | 评论/点赞/收藏/关注、RabbitMQ 通知、WebSocket 私信 | 互动触发异步通知；私信收发 |
| P4 | Spring AI 助手：双模型 Bean、zvector 冒烟、RAG、@Tool、SSE | 流式输出、工具卡片、RAG 命中 |
| P5 | 管理后台、docker-compose、CI/CD | 后台审核/管书；一键部署 |

P4 前置冒烟：① Spring AI 1.0.x + Boot 3.5.x 依赖树；② DeepSeek 流式 + 阿里云 embedding 打通；③ 自写 zvector VectorStore 检索。
