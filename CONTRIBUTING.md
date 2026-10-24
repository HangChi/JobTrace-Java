# 参与开发

JobTrace Java 是对原版 `JobTrace` 行为的渐进式迁移。开始修改前，先读 [README](README.md)、[架构](docs/architecture.md)、根目录 `CONTEXT.md`，以及相关 `specs/<NNN>-<slug>/` 中的 `spec.md` 和 `tasks.md`。原版的接口行为与数据库约束是对照来源；发现差异时先记录证据，不要凭印象改变业务口径。

## 工作边界

- 每项迁移阶段使用独立主题分支；不要把另一项未完成工作混入同一提交。
- 后端保持 `web → application → domain`，持久化放在 `infrastructure`。保持 owner 隔离、管理员服务端校验和显式并发/幂等处理。
- 前端位于 `frontend/`，复用同源 `/api` 客户端，并检查加载、空状态、失败、键盘与小屏表现。
- 不在 Java 端新增独立 ATS/微信爬虫或同步写入器；不擅自启用 Flyway、重放旧版数据库迁移或改变 schema 归属。
- 不提交密钥、本地环境文件、真实数据库数据、`target/`、`frontend/dist/` 或 `node_modules/`。

## 提交前验证

```bash
./mvnw verify
cd frontend
npm run lint
npm test
npm run build
```

修改文档时检查相对链接、命令与现有代码是否一致。对无法在本地验证的真实数据库、邮件、对象存储或浏览器流程，在交付说明中明确标记为待人工验证，而不是写成已通过。

需求与任务使用仓库内 `specs/` 工作流；[开发指南](docs/development.md)包含本地运行与测试数据边界，[接口约定](docs/api.md)包含会话、CSRF 与错误行为。
