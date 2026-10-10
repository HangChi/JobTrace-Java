# 配置说明

Spring Boot 从环境变量与命令行属性读取配置；**不会自动加载** `.env` 文件。`.env.example` 只列字段与示例，不包含真实凭据。把真实值交给本地未追踪环境、IDE 的运行配置或部署平台的密钥管理器，切勿提交、粘贴到日志或放进命令行历史。

## 基础配置

| 变量 | 作用 | 说明 |
| --- | --- | --- |
| `JOBTRACE_DATABASE_URL` | PostgreSQL JDBC 地址 | 形如 `jdbc:postgresql://127.0.0.1:5432/jobtrace`；不能直接使用旧版的 `postgresql://` URL |
| `JOBTRACE_DATABASE_USERNAME` / `JOBTRACE_DATABASE_PASSWORD` | 数据库账号 | 应使用最小权限的专用账号；密码属于密钥 |
| `BETTER_AUTH_SECRET` | 兼容会话 Cookie 的签名密钥 | 浏览器认证必需；与现有数据/会话兼容时必须使用被授权的相同密钥，不能生成新值假装兼容 |
| `BETTER_AUTH_URL` | 对外站点根地址 | 密码重置链接和注册 Origin 校验需要；须与浏览器实际打开的源一致：Vite 开发用 `http://localhost:5173`，直接访问嵌入页面用 `http://localhost:8080`。`https://` 会启用 Secure Cookie |
| `JOBTRACE_AUTH_SECURE_COOKIES` | 强制 Secure Cookie | HTTPS 部署建议显式设为 `true`；本地 HTTP 开发不要开启 |
| `JOBTRACE_FLYWAY_ENABLED` | Flyway 开关 | 保持 `false`；[数据库基线](database-baseline.md) 未批准前不可启用 |

默认配置在 `src/main/resources/application.yaml`，其中数据库用户名/密码的回退值仅适合本地示例，不可用于生产。即使端口能监听，缺少 `BETTER_AUTH_SECRET` 时会话接口也会返回 503；数据库不可用时 `/api/health/ready` 返回 503。

## 按功能启用的外部服务

| 变量 | 使用场景 |
| --- | --- |
| `AUTH_EMAIL_DELIVERY_URL` / `AUTH_EMAIL_DELIVERY_SECRET` | 注册/绑定验证码与密码重置邮件的现有投递接口；缺少时对应操作可能不可用 |
| `JOBTRACE_EMAIL_BINDING_VERIFICATION_SECRET` | 邮箱验证码签名/验证；应使用稳定的独立密钥，不能使用示例值 |
| `AUTH_CHALLENGE_VERIFY_URL` / `AUTH_CHALLENGE_SECRET` | 注册挑战验证的现有服务配置 |
| `COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_BUCKET`、`COS_REGION`、`COS_PUBLIC_BASE_URL` | 上传头像到腾讯云 COS；未配置时头像上传不可用，不影响普通资料读取 |
| `REMINDER_DELIVERY_SECRET` | 内部提醒邮件投递入口的认证密钥 |
| `REMINDER_DELIVERY_BATCH_SIZE`、`REMINDER_DELIVERY_MAX_ATTEMPTS`、`REMINDER_DELIVERY_LEASE_SECONDS` | 邮件投递批次、重试和租约参数；应用有默认值 |
| `JOBTRACE_REDIS_URL` | Valkey/Redis 地址；内部身份桥接的共享重放防护必需，Actuator 汇总健康检查也可能检查此连接 |

## 可选内部身份桥接

`JOBTRACE_AUTH_BRIDGE_ENABLED` 默认 `false`。只有经过独立审查的原版服务到 Java 内部调用才应开启。开启前配置 issuer、audience、当前签名密钥 ID/secret，以及所有 Java 实例共用的 `JOBTRACE_REDIS_URL`：

- `JOBTRACE_AUTH_BRIDGE_ISSUER`、`JOBTRACE_AUTH_BRIDGE_AUDIENCE`
- `JOBTRACE_AUTH_BRIDGE_MAX_LIFETIME_SECONDS`（1–30）、`JOBTRACE_AUTH_BRIDGE_CLOCK_SKEW_SECONDS`（0–5）
- `JOBTRACE_AUTH_BRIDGE_REPLAY_KEY_PREFIX`
- `JOBTRACE_AUTH_BRIDGE_KEYS_0_ID` / `_SECRET`；轮换期间可增加 `JOBTRACE_AUTH_BRIDGE_KEYS_1_ID` / `_SECRET`

签名 secret 是至少 32 个随机字节的 base64url 值。轮换时先让 Java 同时接受新旧密钥，再切换签发方，至少保留旧密钥 35 秒后移除。桥接断言、密钥、会话 Cookie 和数据库凭据都不能记录到日志。

## 数据库与环境隔离

本仓库不创建旧业务 schema。`compose.yaml` 提供 PostgreSQL 17 和 Valkey 的本地容器，但 PostgreSQL 是空库；需要授权的兼容 schema/测试夹具才能运行完整业务。不要将自动化测试连接到共享或生产数据库。旧版迁移链由原仓库拥有，不能因 Java 启动失败而盲目执行或启用 Flyway。

先检查连接与身份，再检查页面：

```bash
curl -i http://localhost:8080/api/health/ready
curl -i http://localhost:8080/api/auth/session
```

第一个应为 200；在没有登录 Cookie 时，第二个应为 401。详情见[故障排查](troubleshooting.md)。
