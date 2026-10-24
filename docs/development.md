# 本地开发

## 前置条件

- Java 21（`java -version`）、Node.js 24+（`node --version`）、可用的 Docker（运行部分集成测试时需要）。
- PostgreSQL 17：本地或经授权可连接的开发数据库，且已具备原版兼容 schema。不要用共享/生产数据库执行自动化测试。
- 如需身份桥接或完整 Actuator Redis 健康检查，准备 Valkey/Redis；普通浏览器认证仍需要 `BETTER_AUTH_SECRET`。

Maven 使用仓库内的 `./mvnw`，无需安装全局 Maven。`compose.yaml` 可以启动本地 PostgreSQL 与 Valkey：

```bash
docker compose up -d postgres valkey
```

它不会创建 JobTrace 业务表。Flyway 尚未获准接管旧 schema，不能用 `JOBTRACE_FLYWAY_ENABLED=true` 来“自动修复”空库；应使用经过批准的开发用 schema/夹具，或运行 Testcontainers 测试。

## 配置

参考根目录 `.env.example` 和[配置说明](configuration.md)，在终端或 IDE 中提供环境变量。Spring Boot **不会**自动读取 `.env` 文件。最少需要 JDBC 地址、数据库账号和 `BETTER_AUTH_SECRET`。如果从浏览器访问 Vite 的 `http://localhost:5173`，请将 `BETTER_AUTH_URL` 设为这个完全相同的源；直接访问 8080 时相应改为 `http://localhost:8080`。注册验证码会检查浏览器 Origin，密码重置链接也使用此地址。

旧版的 `DATABASE_URL=postgresql://...` 不可直接交给 Java。转换为 JDBC 地址并分开设置用户名、密码；不要把真实值写进仓库文件、提交记录或命令输出。使用现有数据时，先确认所用账号和数据库是本次开发被授权访问的目标。

## 启动与访问

先在仓库根目录启动 Spring Boot：

```bash
./mvnw spring-boot:run
```

Maven 的 `generate-resources` 阶段会运行 `npm ci`、构建前端并复制静态资源，因此首次启动稍慢。等后端启动后，在第二个终端运行：

```bash
cd frontend
npm run dev
```

| 地址 | 用途 | 更新方式 |
| --- | --- | --- |
| `http://localhost:5173` | Vite 开发页面；`/api` 代理到 8080 | 前端热更新 |
| `http://localhost:8080` | Spring Boot API 与最近一次 Maven 构建的静态页面 | 修改前端后需重新构建/启动 |

两处显示页面是有意的双运行模式，不是两套后端。浏览器应始终使用同一个源完成登录；不要在 5173 和 8080 间来回切换并期待 Cookie 或 Origin 设置自动同步。

验证服务：

```bash
curl -i http://localhost:8080/api/health/live
curl -i http://localhost:8080/api/health/ready
curl -i http://localhost:5173/api/auth/session
```

`live` 200 表示进程可响应，`ready` 200 表示数据库查询成功。第三条在未登录时应为 401，而不是 503；401 会让前端进入登录页。

## 测试与打包

```bash
./mvnw verify
cd frontend
npm run lint
npm test
npm run build
npm run test:coverage
```

后端集成测试使用隔离的 Testcontainers 资源，需要 Docker daemon。不要通过改变测试数据库 URL 指向远端真实数据。`npm run test:coverage` 按前端配置检查覆盖率；若当前迁移阶段未达到阈值，应如实记录，不要修改门槛掩盖缺口。

构建可运行 JAR：

```bash
./mvnw package
java -jar target/jobtrace-0.1.0-SNAPSHOT.jar
```

JAR 内包含当次构建的前端资源；生产镜像与运行说明见[运维](operations.md)。

## 修改功能时

1. 先读根目录 `CONTEXT.md`、相应 `specs/<NNN>-<slug>/spec.md` 与 `tasks.md`，再检查原版 `JobTrace` 的契约和页面行为。
2. 保持 `web → application → domain` 方向，数据库访问放在 `infrastructure`；每个读写都检查身份与 owner 边界。
3. 行为变化配套测试；UI 变化检查 375/768/1024/1440 px、键盘与焦点、错误和空状态。
4. 不修改旧数据库迁移链，不启用 Flyway，也不添加 Java 端独立岗位爬虫。

当前前端视觉对齐工作仍应以 [`specs/028-frontend-migration`](../specs/028-frontend-migration/) 的状态为准。历史迁移切片文档是阶段记录，不等同于当前功能说明。
