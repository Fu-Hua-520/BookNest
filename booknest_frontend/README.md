# BookNest 前端（booknest_frontend）

BookNest 书籍交流社区的前端应用，对应 [`docs/plan.md`](../docs/plan.md) 第 7 节的页面与路由设计。

## 技术栈

| 层 | 选型 |
|---|---|
| 框架 | Vue 3（`<script setup>` 组合式 API） |
| 构建 | Vite 6 |
| 状态 | Pinia |
| 路由 | vue-router 4 |
| 请求 | axios（统一 `Result` 解包 + JWT 注入 + 401 处理） |
| UI | Element Plus（中文语言包 + 全量图标） |
| Markdown | marked + highlight.js |
| 流式 | 原生 `fetch` + `ReadableStream` 解析 SSE |
| 实时 | 原生 `WebSocket` |

## 快速开始

```bash
npm install
npm run dev        # 开发服务器 http://127.0.0.1:5173
npm run build      # 产物输出到 dist/
npm run preview    # 预览构建产物
```

开发服务器通过 Vite 代理把 `/api`、`/ws` 转发到后端（默认 `http://localhost:8080`），
因此**无需后端开启 CORS**。若后端不在默认地址：

```bash
# .env.local
VITE_API_TARGET=http://192.168.1.10:8080
```

## 后端对接约定

### 统一返回体

后端所有接口返回 `{ code, msg, data }`，`code === 1` 表示成功。
`src/utils/request.js` 的响应拦截器会自动解包：**业务代码直接拿到 `data`**，
失败时统一弹出 `ElMessage` 并 reject。

### 鉴权

| 端 | 登录接口 | 请求头 | 令牌存储 key | 签发密钥 |
|---|---|---|---|---|
| 用户端 | `POST /user/login` | `authentication` | `bn_user_token` | `JWT_USER_SECRET` |
| 管理端 | `POST /admin/login` | `admin-authentication` | `bn_admin_token` | `JWT_ADMIN_SECRET` |

**两套令牌完全隔离**：密钥不同、请求头不同、有效期各自独立（`JWT_USER_TTL` / `JWT_ADMIN_TTL`）。
用户端令牌无法通过管理端拦截器的验签，反之亦然；因此前台登录 ≠ 后台登录，管理员进后台需单独登录一次。

拦截器会同时挂载两套头，因此同一个 axios 实例可服务两端。
`401` 时用户端跳 `/login`、管理端跳 `/admin/login`。

**前后台会话互不覆盖**：管理端资料存 `bn_admin_profile`，与用户端 `bn_user_profile` 分开，
同一浏览器可同时保持前台与后台登录态，退出其中一端不影响另一端。

后台登录接口 `POST /admin/login` 由 `AdminAuthController` 提供，
并已在 `WebMvcConfiguration` 中排除于管理端拦截器之外，避免"登录接口自身要求先登录"。

**后端配套改动（与本前端同步完成）**：

1. 新增 `POST /admin/login`（`AdminAuthController` + `AdminAuthService`），
   用 `adminSecretKey` + `adminTtl` 签发令牌，并校验 `role === 'ADMIN'`；
   非管理员与账号不存在统一返回"账号或密码错误"，避免枚举邮箱。
2. `JwtTokenAdminInterceptor` 改为读取 `adminTokenName` 并用 `adminSecretKey` 验签。
   此前它读的是 `userTokenName` / `userSecretKey`，导致 `application.yml` 里的
   `admin-secret-key`、`admin-token-name` 配置项形同虚设、两套鉴权实际共用一套密钥。
3. `WebMvcConfiguration`：
   - ✅ 管理端拦截器排除 `/admin/login`（否则登录接口自身要求先登录，死循环）；
   - ✅ `/common/**` 纳入用户端拦截器 —— 修复**匿名可上传文件到 OSS**（此前该行
     `excludePathPatterns("/common/**")` 是空操作，因为 `/common/**` 从未注册进 `addPathPatterns`）。
4. 移除用户端 `POST /book`（原本匿名即可创建书籍）—— 书籍写操作统一归 `/admin/book`，
   用户端 `/book/**` 只保留读接口（搜索 / 详情 / ISBN 补全）。

### SSE 契约

`POST /ai/chat/stream`、`POST /ai/chat/rag`，事件类型：

`THINKING` · `TOOL_CALLING` · `TOOL_RESULT` · `MESSAGE` · `DONE` · `ERROR`

`DONE` 事件可能携带 `tokenUsage` 与 `recommendations`（帖子/书籍/书单/网页四类推荐卡片）。

### WebSocket

握手地址 `/ws/chat?token={JWT}`，`token` 走查询参数。
客户端内置 25 秒心跳与指数退避重连（上限 30 秒）。

## 路由一览

| 路径 | 页面 | 需登录 |
|---|---|---|
| `/` | 首页信息流 | |
| `/post/:id` | 帖子详情（评论区 + 楼中楼） | |
| `/post/edit/:id?` | 写书评 / 编辑（Markdown 分栏预览） | ✓ |
| `/book/:id` | 书籍详情 + 相关书评 | |
| `/booklist` | 书单广场（公开 / 我的） | |
| `/booklist/create` · `/booklist/:id/edit` | 创建 / 编辑书单 | ✓ |
| `/booklist/:id` | 书单详情 + 条目增删 | |
| `/category/:id` | 分类页 | |
| `/tag/:id` | 标签页 | |
| `/search` | 搜索（书评 / 书籍 / 书单） | |
| `/assistant` | AI 助手（SSE 流式 + 工具卡片） | ✓ |
| `/chat` | 私信（WebSocket） | ✓ |
| `/user/:id` | 个人主页（书评 / 书单 / 关注 / 粉丝） | |
| `/user/:id/following` · `/user/:id/followers` | 关注 / 粉丝列表 | |
| `/notification` | 通知中心 | ✓ |
| `/login` · `/register` | 登录 / 注册 | |
| `/admin/login` | 管理后台登录 | |
| `/admin/post` | 帖子审核 | 管理员 |
| `/admin/book` | 书籍管理（含 ISBN 补全） | 管理员 |
| `/admin/category` | 分类管理（两级分类树，增删改 + 启停） | 管理员 |
| `/admin/tag` | 标签管理（分页 / 搜索 / 重命名 / 启停） | 管理员 |
| `/admin/user` | 用户管理（状态 / 角色） | 管理员 |
| `/admin/quota` | AI 额度管理 | 管理员 |
| `/admin/embedding` | 向量库全量重嵌入 | 管理员 |

### 分类与标签的约束（由后端 Service 强制）

- 分类树**固定两级**：子分类只能挂在一级分类下；已有子分类的分类不能改为子分类。
- 分类同一层级下**不允许重名**；不能把分类挂到自身之下。
- **分类删除**：存在子分类、或已被帖子引用时会被拒绝（返回具体数量）。
- **标签名称全局唯一**；**标签删除**：已被帖子引用时会被拒绝。
- 分类与标签的「禁用」状态**对前台生效**：禁用项不出现在前台分类树 / 标签列表 / 热门标签，
  但在管理端可见，可随时恢复。
- 标签的 `useCount` 由发帖流程维护，管理端只读展示、不可修改。

## 目录结构

```
src/
├── api/            # 按后端控制器分组的接口封装
├── components/     # 通用组件（卡片、编辑器、AI 工具卡、头像等）
├── router/         # 路由表 + 登录/管理员守卫
├── stores/         # Pinia：user / admin / badge / taxonomy
├── utils/          # request / sse / websocket / markdown / format
├── views/          # 页面
│   └── admin/      # 管理后台页面
└── assets/main.css # 全局样式与 Element Plus 主题变量
```

## 已知限制

这些限制源于**后端当前尚未提供对应接口**，前端已用可行方案降级，待后端补齐后替换即可：

1. **帖子搜索**：`/post/list` 不支持关键词参数，搜索页「书评」Tab 改为拉取最新 100 条后在
   前端按标题/摘要/作者/分类/标签匹配，页面已明确提示结果可能不完整。
   建议后端为 `/post/list` 增加 `keyword` 参数。
2. **按作者查帖子**：无该接口，个人主页同样采用拉取帖子流后按 `authorId` 本地筛选。
3. **按书籍查帖子**：无该接口，书籍详情页按 `bookId` 本地筛选。
4. **用户资料**：无公开的用户详情接口（`GET /user/{id}` 未实现），
   个人主页的资料卡从帖子数据或关注列表中反推。建议后端补 `/user/{id}`。
5. **头像上传**：无修改个人资料的接口，故未提供头像设置入口。
6. **密码修改**：`WebMvcConfiguration` 放行了 `/user/password/**`，但控制器尚未实现。
7. **书单收藏/点赞**：`booklist` 表有 `like_count` / `collect_count` 字段，但无对应接口，
   页面仅做只读展示。
8. **会话命名**：AI 会话标题由后端生成，前端仅提供重命名与删除。

## 生产部署

同目录下已提供 `Dockerfile`（多阶段构建）与 `nginx.conf`：
Nginx 托管静态资源、反代 `/api` 到后端并关闭 SSE 缓冲、反代 `/ws` 升级 WebSocket、
SPA history 路由回退到 `index.html`。

启用方式：在项目根目录 `docker-compose.yml` 中取消 `web` 服务的注释后
`docker compose up -d --build`。
