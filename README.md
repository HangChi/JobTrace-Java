# JobTrace Java

![JobTrace 标识](frontend/public/jobtrace-icon.svg)

JobTrace 是求职投递与面试过程管理工具。本仓库将原版 Next.js 服务逐步迁移为 Spring Boot 模块化单体与 React 前端，沿用原有 PostgreSQL 数据和业务约束。前端与后端在开发时分别运行；打包后，前端静态资源也可由同一个 Java JAR 提供。

> [!IMPORTANT]
> 本仓库仍处于迁移与界面对齐阶段。Java 已实现主要业务接口，但上线流量切换、旧数据库迁移基线和全部页面的人工视觉验收不是本仓库当前已完成的事实。不要把本地启动等同于生产切换。

## 能做什么

| 领域 | 当前 Java 端能力 |
| --- | --- |
| 账号与认证 | 注册、验证码、登录/退出、找回密码、会话、资料、安全邮箱、密码与管理员权限 |
| 投递与分析 | 投递及阶段管理、统计报告、进度提醒 |
| 面经 | 私有记录、公开分享、评论、点赞与导出 |
| 提醒与数据 | 提醒和偏好、CSV/XLSX 导入预检、确认导入、导出 |
| 岗位市场 | 读取共享目录、收藏、管理员目录/来源/候选审核与运行记录 |

岗位市场的 ATS/微信采集、调度和同步运行仍由原服务负责；Java 不单独爬取。详细边界见[架构](docs/architecture.md)与[接口约定](docs/api.md)。前端按旧版页面与视觉语言持续对齐，进度以 [`specs/028-frontend-migration/tasks.md`](specs/028-frontend-migration/tasks.md) 为准。

## 技术与目录

- Java 21、Spring Boot 4.1.1、Spring JDBC/jOOQ、PostgreSQL 17；共享重放防护使用 Valkey/Redis。
- React 19、TypeScript 6、Vite 8；浏览器通过同源 `/api` 调用 Java。
- `src/main/java/com/jobtrace/`：按 `applications`、`interviews`、`analytics`、`reminders`、`datatransfer`、`jobmarket`、`identityaccess` 和 `shared` 分模块。
- `frontend/`：浏览器页面、API 客户端和前端测试。
- `docs/`：当前架构、配置、开发、运维与故障排查；`specs/`：各迁移阶段的需求、方案和任务。

## 本地开始

需要 Java 21、Node.js 24+、可访问的 PostgreSQL 17，以及 Maven Wrapper。首次安装前端依赖使用 `npm ci`。数据库必须具有与原版兼容的既有 schema；仓库中的 `compose.yaml` 只启动空数据库和 Valkey，**不会自动创建业务表**。

1. 按[配置说明](docs/configuration.md)提供 `JOBTRACE_DATABASE_URL`、`JOBTRACE_DATABASE_USERNAME`、`JOBTRACE_DATABASE_PASSWORD` 和 `BETTER_AUTH_SECRET`。不要提交本地环境文件或真实凭据；保持 `JOBTRACE_FLYWAY_ENABLED=false`。
2. 在仓库根目录启动后端：

   ```bash
   ./mvnw spring-boot:run
   ```

3. 在另一终端启动前端：

   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

4. 打开 [http://localhost:5173](http://localhost:5173)。Vite 把 `/api` 代理到 `http://localhost:8080`。`GET /api/health/ready` 返回 `{"status":"ok"}` 才表示数据库连接可用；未登录的 `/api/auth/session` 返回 401 是正常现象。

`http://localhost:8080` 也可显示最近一次 Maven 构建嵌入的页面，但不会像 Vite 一样热更新。两种运行方式和独立生产打包见[开发指南](docs/development.md)。

## 验证

```bash
./mvnw verify
cd frontend
npm run lint
npm test
npm run build
```

后端集成测试可能需要 Docker/Testcontainers；运行命令、测试数据边界和手工验收见[开发指南](docs/development.md)。

## 文档导航

| 文档 | 用途 |
| --- | --- |
| [开发指南](docs/development.md) | 环境、启动、测试、前后端运行模式 |
| [使用指南](docs/user-guide.md) | 登录、主要页面和管理员操作 |
| [配置说明](docs/configuration.md) | 环境变量、外部服务、密钥与数据库安全 |
| [架构](docs/architecture.md) | 模块边界、认证、数据与采集归属 |
| [接口约定](docs/api.md) | 路由分组、会话、CSRF、错误与兼容清单 |
| [运维](docs/operations.md) | 打包、健康检查、部署和回滚原则 |
| [故障排查](docs/troubleshooting.md) | 连接、登录、数据库、端口和页面问题 |
| [数据库基线](docs/database-baseline.md) | Flyway 启用前的审批门槛 |
| [迁移记录](docs/migration.md) | 各阶段的历史范围和决策 |
| [参与开发](CONTRIBUTING.md) | 分支、代码边界与提交前验证 |

需求与任务按 `specs/<NNN>-<slug>/` 管理；原版 `JobTrace` 仓库仍是行为对照来源。不要直接修改或重放原版迁移链。
