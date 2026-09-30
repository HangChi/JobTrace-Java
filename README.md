# JobTrace Java

JobTrace 的渐进式 Java 迁移仓库。目标架构是 Spring Boot 模块化单体后端加 React、TypeScript、Vite 前端；Node.js 只用于开发和 CI 构建，生产环境运行单个包含静态前端资源的 Java JAR。

## 当前状态

仓库目前提供迁移基础设施，不代表现有 JobTrace 功能已经迁移完成：

- Java 21 与 Spring Boot 4.1.1 工程骨架
- Spring MVC、Security、JDBC、jOOQ、Flyway、Actuator
- React 19、TypeScript 6、Vite 8 前端骨架
- 独立的存活与数据库就绪检查
- Gradle Wrapper 与前后端测试基础
- CI 工作流
- speckit 迁移规格、设计与任务清单

已有功能、数据库约束和安全行为仍以原 `JobTrace` 仓库为准。

## 架构

```text
Browser
  └── React + TypeScript static assets
          └── Spring Boot REST API
                  └── PostgreSQL 17
```

开发时 Vite 运行在独立端口并把 `/api` 代理到 Spring Boot。生产构建把 `frontend/dist` 嵌入可执行 JAR，因此服务器不需要 Node.js。

## 前置要求

- Java 21
- Node.js 24 或更新版本
- PostgreSQL 17（运行就绪检查和后续集成测试时需要）

Gradle 无需全局安装，使用仓库内 Wrapper。

## 快速验证

后端：

```bash
./gradlew test
```

前端：

```bash
cd frontend
npm ci
npm run lint
npm test
npm run build
```

完整生产构建：

```bash
./gradlew bootJar
```

详细说明见 [迁移基础 Quickstart](specs/001-java-migration/quickstart.md)。

## 本地配置

复制 `.env.example` 中的变量到本地环境。Spring 使用 JDBC 形式的数据库地址：

```dotenv
JOBTRACE_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/jobtrace
JOBTRACE_DATABASE_USERNAME=jobtrace
JOBTRACE_DATABASE_PASSWORD=change-me
JOBTRACE_FLYWAY_ENABLED=false
```

在旧数据库迁移基线获得批准前，必须保持 `JOBTRACE_FLYWAY_ENABLED=false`。

## 模块规划

Java 包沿用原项目的领域词汇：

```text
com.jobtrace
├── shared
├── applications
├── interviews
├── analytics
├── reminders
├── datatransfer
├── jobmarket
└── identityaccess
```

迁移顺序、约束和回滚原则见 [迁移指南](docs/migration.md)。

## 文档

- [架构说明](docs/architecture.md)
- [迁移指南](docs/migration.md)
- [远程仓库与分支保护](docs/repository-setup.md)
- [功能规格](specs/001-java-migration/spec.md)
- [实现计划](specs/001-java-migration/plan.md)
- [迁移任务](specs/001-java-migration/tasks.md)

## 尚待仓库所有者决定

- Git 远程仓库地址尚未设置。
- 分支保护需要在远程仓库创建后启用。
- License 尚未选择；在明确开源或授权策略前不添加许可证。

