# BookNest · 书籍交流社区

一个以「书」为纽带的交流社区：发书评、建书单、逛书吧、关注书友，并内置一个可自建模型的**评论区 AI 机器人**。

前后端分离，后端 Java 17 + Spring Boot 3 多模块，前端 Vue 3 + Vite，整套用 Docker Compose 一键拉起。

---

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.5.4 · Spring AI 1.0.9 |
| 持久层 | MyBatis 3.0.5 · MySQL 8 · Druid 1.2.21 · PageHelper 2.1.0 |
| 缓存 / 消息 / 实时 | Redis 7 · RabbitMQ 3 · WebSocket |
| 鉴权 | JWT（jjwt 0.12.6，用户端与管理端**双密钥隔离**） |
| 对象存储 | 阿里云 OSS（3.17.4，无 MinIO） |
| 接口文档 | SpringDoc OpenAPI 2.8.9（Swagger UI） |
| 前端 | Vue 3.5 · Vite 6 · Pinia 2.3 · vue-router 4.5 · Element Plus 2.9 · axios · marked + highlight.js |
| 部署 | Docker Compose · Nginx |

## 功能特性

- **内容**：帖子 / 书评（Markdown + 代码高亮）、书籍库、书单、标签
- **社区**：书吧（关注 / 等级与称号 / 吧务）、用户关注、全站搜索
- **互动**：点赞 / 收藏 / 评论，浏览计数（Redis 计数缓冲 + 定时批量落库）
- **私信与群聊**：WebSocket 实时收发，Redis pub/sub 支持多实例投递、ZSET 维护在线态
- **通知**：RabbitMQ 异步解耦 + 可靠投递（confirm / return / 死信），幂等去重
- **AI 机器人**：评论区机器人，厂商 / 模型 / API Key 由用户自填、管理员审核后动态生效（未使用平台级密钥）
- **管理后台**：帖子审核、用户管理、书籍库、分类 / 标签、机器人审核

## 目录结构

```
booknest/
├── booknest_backend/            # 后端 Maven 父工程
│   ├── booknest-pojo/           #   实体 / DTO / VO
│   ├── booknest-common/         #   常量、上下文、枚举、异常、工具
│   └── booknest-server/         #   启动入口、Controller / Service / Mapper
├── booknest_frontend/           # 前端工程（Vue 3 + Vite）
├── booknest_data/
│   └── sql/                     # 建表脚本 + upgrade/ 历史升级脚本
├── docs/                        # 技术方案与优化文档
├── drafts/                      # 内容草稿
├── docker-compose.yml           # 全栈编排
└── .env.example                 # 环境变量模板（复制为 .env 后填值）
```

## 快速开始

### 1. 准备环境变量

```bash
cp .env.example .env      # 然后按需修改：DB / REDIS / RABBITMQ / JWT / OSS
```

> `.env` 已被 `.gitignore` 忽略，**切勿提交真实密钥**。`application.yml` 与 `docker-compose.yml`
> 中的敏感项全部走 `${VAR}` 占位。

### 2. Docker Compose 一键启动

```bash
docker compose up -d --build
```

| 服务 | 地址 | 说明 |
|---|---|---|
| web | http://localhost | 前端（Nginx） |
| app | http://localhost:8080 | 后端 API |
| mysql | localhost:3306 | 数据库 `booknest`，首次启动自动执行 `booknest_data/sql/*.sql` |
| redis | localhost:6379 | 缓存 |
| rabbitmq | localhost:15672 | 管理界面（5672 为 AMQP 端口） |

> ⚠️ MySQL 官方镜像**仅在数据目录为空（首次启动）**时执行初始化脚本。之后改动 `booknest_data/sql`
> 再启动不会生效，需清空 `booknest_data/mysql/data` 重新初始化。

### 3. 本地开发

```bash
# 后端（需本机有 MySQL / Redis / RabbitMQ）
cd booknest_backend
mvn clean package -DskipTests
java -jar booknest-server/target/booknest-server-*.jar

# 前端
cd booknest_frontend
npm install
npm run dev          # http://127.0.0.1:5173，通过 Vite 代理转发 /api、/ws 到 :8080
```

### 接口文档

后端启动后访问 <http://localhost:8080/swagger-ui.html>。

## 默认账号

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin@booknest.com` | `admin123` | 管理员 |
| 其余种子用户 | `123456` | 普通用户 |

> 仅支持**邮箱登录**。默认账号仅供本地演示，上线前请务必改密。

## 鉴权约定

用户端与管理端**完全隔离**：密钥不同、请求头不同、有效期各自独立。

| 端 | 登录接口 | 请求头 | 签发密钥 |
|---|---|---|---|
| 用户端 | `POST /user/login` | `authentication` | `JWT_USER_SECRET` |
| 管理端 | `POST /admin/login` | `admin-authentication` | `JWT_ADMIN_SECRET` |

统一返回体 `{ code, msg, data }`，`code === 1` 表示成功。

## 部署

项目通过 Docker Compose **手动部署**，在服务器上拉取代码后重建即可：

```bash
git pull
docker compose up -d --build
```

后端以 `prod` 环境启动（`Dockerfile` 内已指定），前端由 Nginx 托管并反向代理 `/api`、`/ws` 到后端。
运行期敏感配置（DB / Redis / RabbitMQ / OSS / JWT）由服务器上的 `.env` 提供，不进镜像。

> 仓库未配置自动化部署，发布流程由人工执行。

## 相关文档

- [`docs/PLAN.md`](docs/PLAN.md) —— 技术方案与页面设计
- [`docs/技术要点-并发-Redis-MQ-查询优化.md`](docs/技术要点-并发-Redis-MQ-查询优化.md) —— 并发 / 缓存 / 消息 / 查询优化要点
- [`docs/并发与性能优化方案.md`](docs/并发与性能优化方案.md) —— 并发与性能优化方案
- [`booknest_frontend/README.md`](booknest_frontend/README.md) —— 前端说明
