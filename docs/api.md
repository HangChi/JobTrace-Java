# HTTP 接口约定

本页是接入与调试指南，不替代控制器和功能规格。逐个旧接口的兼容状态见[029 路由矩阵](../specs/029-code-hardening/contracts/route-matrix.md)；各阶段更细的输入/输出在 `specs/<NNN>-<slug>/contracts/` 与 `docs/migration-slices/`。

## 路由概览

| 路由组 | 常用入口 | 身份范围 |
| --- | --- | --- |
| 认证 | `/api/auth/login`、`/api/auth/register`、`/api/auth/session`、`/api/auth/logout`、`/api/auth/request-password-reset` | 部分匿名；会话读取允许匿名但未登录返回 401 |
| 个人资料 | `/api/profile`、`/api/profile/email`、`/api/profile/password`、`/api/profile/sessions` | 当前账号 |
| 投递和分析 | `/api/applications`、`/api/applications/{id}/stages`、`/api/analytics/summary`、`/api/analytics/report` | 当前账号 |
| 面经 | `/api/interviews`、`/api/interviews/public` | 私有记录按 owner；公开记录按发布状态及操作权限 |
| 提醒 | `/api/reminders`、`/api/reminder-settings` | 当前账号 |
| 数据导入导出 | `/api/imports/preview`、`/api/imports/{id}/confirm`、`/api/exports/*` | 当前账号 |
| 岗位市场 | `/api/job-market/campaigns`、`/api/job-market/campaigns/{id}/favorite` | 共享目录读取；收藏按 owner |
| 管理员 | `/api/admin/summary`、`/api/admin/users`、`/api/admin/audit-events`、`/api/admin/job-market/*` | 服务端管理员校验 |
| 健康检查 | `/api/health/live`、`/api/health/ready`、`/actuator/health` | 匿名 |

岗位市场管理员的 `POST /api/admin/job-market/sources/{sourceId}/sync` 是明确的兼容拒绝路径：返回 410 和 `ingestion_owned_externally`，不会在 Java 中采集。ATS/微信采集与同步运行写入由原服务负责。

## 会话与写请求

浏览器通过同源、HttpOnly、已签名的会话 Cookie 登录。前端请求使用 `credentials: same-origin`，不在浏览器存储身份令牌。不要向公开接口发送自定义 `X-User-Id`/`X-Role` 之类身份头；服务端不会把它们视为授权依据。

受保护的非 GET 请求需通过同源 Origin 检查，并按 Spring CSRF 机制携带令牌。前端 `frontend/src/shared/api/client.ts` 先读取 `GET /api/security/csrf`，再发送 `X-CSRF-TOKEN`。认证入口有单独的匿名/CSRF 规则，但注册验证码仍进行 Origin 校验。开发时浏览器源必须与 `BETTER_AUTH_URL` 配置一致。内部提醒投递入口不应从浏览器调用。

## 返回与错误

- JSON API 使用 `application/json`；失败通常使用 `application/problem+json`，包含 HTTP `status`、`code`、`detail` 和 `requestId`，字段错误可附带 `fieldErrors`。
- 401：未登录、会话过期或无有效 Cookie。匿名打开 `/api/auth/session` 得到 401 是正常登录态检测结果。
- 403：已登录但权限不足、Origin/CSRF 被拒绝；不要把 403 当成可重试的登录过期。
- 404：资源不存在，或私有资源不属于当前账号；不要借此枚举其他账号的数据。
- 409：版本/并发或业务冲突；应刷新状态后让用户确认，不要盲目重放写入。
- 503：数据库、认证密钥或外部依赖不可用；先看[故障排查](troubleshooting.md)，不要清除用户数据。

列表的分页、过滤和具体字段以相应控制器与契约测试为准；不要由本页推断所有组共用一套查询参数。
