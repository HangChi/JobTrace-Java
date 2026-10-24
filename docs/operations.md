# 运行与运维

生产形态为一个 Java 21 可执行 JAR/OCI 镜像。Maven 构建时把 `frontend/dist` 复制进静态资源；运行时不需要 Node.js。开发时的 5173 是 Vite 服务，不应视为生产入口。

## 构建产物

```bash
./mvnw clean package
java -jar target/jobtrace-0.1.0-SNAPSHOT.jar
```

JAR 的默认监听端口为 8080。`Dockerfile` 将该 JAR 放入 Java 21 JRE 镜像并以非 root 用户运行。`./scripts/test-production-artifact.sh` 可在有 Docker 的隔离环境中验证 JAR、镜像、静态页面和健康端点；它不是连接真实业务库的迁移工具。

构建 manifest 记录 `Build-Revision` 与 `Frontend-Asset-SHA256`，可以用下列命令确认正在发布的前端资源来自哪次构建：

```bash
unzip -p target/jobtrace-0.1.0-SNAPSHOT.jar META-INF/MANIFEST.MF
```

## 部署前检查

1. 确认目标数据库是被授权的环境，schema 与原版兼容，已备份并有可执行回滚方案。`JOBTRACE_FLYWAY_ENABLED=false`；[基线审批](database-baseline.md)未完成前不得打开。
2. 从密钥管理器提供 JDBC 账号、`BETTER_AUTH_SECRET`、正确的 `BETTER_AUTH_URL`；按启用功能提供邮件、头像、提醒和桥接密钥。不要把密钥构建进镜像或记录在部署日志。
3. 确认新旧服务的写入路径有明确的单一归属。岗位市场采集、调度与同步运行仍只由原服务执行；Java 不能接管这些任务。
4. 先在非生产环境验证登录、受保护页面刷新、写请求 Origin/CSRF、跨账号隔离、管理员权限和深链接，再考虑路由切换。

部署平台应只暴露预期的浏览器/API 入口。若启用签名内部身份桥接，桥接调用必须限定在可信内部网络并使用共享 Valkey/Redis 重放防护，不得将内部路由公开给浏览器。

## 健康与监测

| 路径 | 语义 | 期望 |
| --- | --- | --- |
| `GET /api/health/live` | Java 进程可响应 | 200 `{"status":"ok"}` |
| `GET /api/health/ready` | 对配置的 PostgreSQL 执行 `select 1` | 200 `{"status":"ok"}`；连接失败为 503 |
| `GET /actuator/health` | Spring Actuator 汇总状态 | 可能包含 Redis 等附加指标；不等同于数据库就绪 |
| `GET /api/auth/session` | 浏览器会话服务 | 匿名 401；有有效 Cookie 时 200；签名密钥/依赖不可用时可能 503 |

探针按部署平台的需要分别使用 `live` 和 `ready`。不能只检查 `/` 返回 200：静态页面可显示时数据库或认证仍可能失败。Actuator 汇总显示 DOWN 时应排查具体依赖；不要为了“变绿”直接关掉健康指标。

涉及写入或管理员操作的失败应按 `requestId`、时间、状态码和错误 `code` 排查，不记录 Cookie、令牌、密码、邮箱验证码或完整请求体。

## 回滚

保留上一个可运行的不可变 JAR/镜像与对应配置。若新版本出现错误：

1. 停止把新流量导向故障版本；必要时先关闭新路由的写入入口。
2. 恢复上一个经验证的版本，检查 `live`、`ready` 和受保护业务路径。
3. 核查故障期间已写入的业务记录。回滚应用**不会**撤销 PostgreSQL 中已经提交的投递、提醒、面经或账号更改。
4. 记录版本、前端资源摘要、错误码与请求 ID，再决定是否重试发布。

旧版仍拥有 DDL；Java 版本回滚不能替代数据库备份恢复。未来若批准 schema 变更，必须为该变更单独提供迁移和数据恢复方案。历史的分析灰度路由仅是迁移切片记录，不能直接当作当前全站部署手册。
