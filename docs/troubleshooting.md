# 故障排查

先判断是页面、API、数据库还是认证问题。下面的命令只请求本机地址，不会修改业务数据：

```bash
curl -i http://localhost:5173/
curl -i http://localhost:8080/api/health/live
curl -i http://localhost:8080/api/health/ready
curl -i http://localhost:5173/api/auth/session
```

## 页面打不开

- 5173 无法连接：Vite 未启动，或被占用后自动改用了别的端口；看 `npm run dev` 的终端输出。
- 8080 无法连接：Spring Boot 未启动或启动失败；先看后端终端的第一条异常。macOS 可用 `lsof -nP -iTCP:8080 -sTCP:LISTEN` 检查监听进程。
- 5173 页面能开但 `/api` 报代理错误：后端 8080 未就绪。先检查 `live` 和 `ready`，不要仅刷新浏览器。
- 8080 页面与最新代码不同：它提供 Maven 上次构建嵌入的静态文件；开发时打开 5173，或重新构建并启动 Java。

## 登录页显示“暂时无法连接职迹”

前端启动时先请求 `/api/auth/session`：

- **401**：正常的未登录响应，应显示登录表单。
- **503 `session_unavailable`**：优先检查 `BETTER_AUTH_SECRET` 是否已提供且与当前数据库/旧版会话匹配；也检查数据库可达性。不要在真实共享数据库上随意更换密钥，否则现有会话无法验证。
- **网络错误/代理 502**：确认 Java 8080 正在监听，Vite 的 `/api` 代理指向 8080。

如果能显示登录页但提交后失败，先看 HTTP 状态和响应 `code`，再核对数据库 schema、账号状态与密码；不要把密码、Cookie 或完整响应保存到排查笔记。

## 数据库就绪失败

`/api/health/ready` 返回 503 表示 Java 的 `select 1` 未成功。确认 `JOBTRACE_DATABASE_URL` 是 `jdbc:postgresql://...`，账号/密码正确，数据库在监听。如果通过 SSH 隧道连接，隧道必须在 Java 进程整个运行期间保持连接；隧道掉线后只启动前端不能恢复数据库。

本地 `docker compose` 新建的是空数据库：即使 `ready` 返回 200，业务表仍可能不存在。此时应使用授权的开发 schema/夹具，不应启用 Flyway 或对生产库运行测试。后端集成测试使用隔离的 Testcontainers。

## 注册、写入与外部能力失败

- 注册验证码 403：检查浏览器地址的 scheme、host、port 是否与 `BETTER_AUTH_URL` 完全一致。例如从 `localhost:5173` 打开页面时，不要配置成 `127.0.0.1:5173` 或 `localhost:8080`。
- 已登录写请求 403：确认请求来自同源页面，并按 `/api/security/csrf` 的令牌协议发送 `X-CSRF-TOKEN`；不要绕过 Origin/CSRF 检查。
- 注册码/密码重置邮件失败：核对邮件投递配置与地址；密码重置还要求有效的 `BETTER_AUTH_URL`。
- 头像上传失败：检查 COS 配置和对象存储权限；普通资料功能不依赖头像上传。
- 岗位市场“同步”返回 410：这是设计边界。Java 不运行 ATS/微信采集；需要到原版服务处理同步。
- `/actuator/health` 为 DOWN 但 `/api/health/ready` 为 200：数据库可达，但 Actuator 还可能检查未运行的 Redis/Valkey 等依赖。按配置和实际启用的功能排查，不要把两个健康端点混为一谈。

## 报告问题时附上

版本/分支、访问地址、操作路径、HTTP 状态、响应 `code` 与 `requestId`、两个健康端点结果、是否使用 Vite 或嵌入页面。请删除数据库地址中的账号密码及任何 Cookie、令牌和验证码。
