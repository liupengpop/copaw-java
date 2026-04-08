# CoPaw Java

CoPaw Java 是 CoPaw 的 Java / Spring Boot 重写版本，目标是在 Java 技术栈下重建多 Workspace、多 Agent 的个人 AI 助手运行时平台，并逐步补齐 Console、工具审批、MCP、定时任务、记忆、技能与渠道能力。

当前这个项目不是空壳迁移，而是已经打通了 P0 最小闭环，并完成了一批 P1 能力的接线与验证。它的定位很明确：一边对照 Python 版 CoPaw 的成熟能力，一边把核心运行时能力稳定地落到 Java 工程里。

## 当前状态

目前 `copaw-java` 已确认完成的关键进展包括：

- 多模块工程可以通过 `mvn -DskipTests compile`
- 真实 `ReActAgent` 已接入运行链路，不再是 stub
- Console 聊天支持 SSE 输出、push messages、工具审批流
- `CronManager` 生命周期已挂到应用层调度链路
- WorkspaceTools、ToolGuard、MCP 真连接与 toolkit 注册已接通
- `memory_search` 已接入 AgentScope runtime，但底层仍是关键词回退实现
- `copaw-frontend` 可作为验证前端，覆盖聊天、会话、频道、文件、技能、工具、MCP、定时任务、模型配置、环境变量等页面
- 会话消息已持久化到 `<workspace>/chats/sessions.json`，`ChatPage` 与 `SessionsPage` 已接真实接口

当前仍需明确的边界包括：

- Java 侧尚未达到 Python 版全部功能覆盖，尤其是 13+ 渠道适配器仍未完整迁移
- `memory_search` 还不是向量 / 语义检索，当前仍是 `ReMeLightMemoryManager.search()` 的关键词回退
- `AgentRunner` 当前采用同步 `call(List<Msg>)` 再由 CoPaw 自己切 SSE delta，暂未依赖逐 token API
- `maven-compiler-plugin` 尚未开启 `-parameters`，所以所有 WebFlux 控制器中的 `@RequestParam` / `@PathVariable` 必须显式写参数名

## 技术栈

| 维度 | 现状 |
| --- | --- |
| Java | 17 |
| 构建工具 | Maven |
| 应用框架 | Spring Boot 3.3.5 |
| Web 层 | Spring WebFlux |
| Agent Runtime | AgentScope-Java 1.0.11 |
| AI 相关依赖 | Spring AI 1.0.0 |
| 持久化 | JSON 文件 + SQLite JDBC 依赖 |
| Markdown / YAML | Flexmark、SnakeYAML |
| 默认服务端口 | `8080` |
| API Base Path | `/api` |
| 当前版本 | `1.0.0-SNAPSHOT` |

当前已接通的模型 provider 映射包括 OpenAI-compatible、Anthropic、Ollama。

## 模块结构

`copaw-java/pom.xml` 当前定义了 8 个 Maven 模块：

| 模块 | 主要职责 |
| --- | --- |
| `copaw-app` | Spring Boot 启动入口、应用装配、调度接线 |
| `copaw-api` | Console 与管理接口，包括聊天、会话、Cron、MCP、Skills 等 API |
| `copaw-workspace` | Workspace 生命周期、`AgentRunner`、会话持久化、运行态接线 |
| `copaw-core` | 核心配置、domain、基础运行能力 |
| `copaw-memory` | 记忆管理与 `ReMeLightMemoryManager` |
| `copaw-skills` | 技能加载、解析、启停与相关管理能力 |
| `copaw-cron` | 定时任务与 `CronManager` |
| `copaw-common` | 通用工具、共享对象与基础设施 |

项目目录大致如下：

```text
copaw-java/
├── README.md
├── DEVELOPMENT.md
├── pom.xml
├── copaw-app/
├── copaw-api/
├── copaw-common/
├── copaw-core/
├── copaw-cron/
├── copaw-memory/
├── copaw-skills/
├── copaw-workspace/
├── copaw-frontend/
└── test/
```

## 已打通的能力

从当前开发验证结果看，`copaw-java` 已经具备一条可以手工验收的最小产品链：

- 聊天：支持 console SSE 聊天，响应由 `AgentRunner` 按 delta 形式切片输出
- 会话：按 `session_id + user_id + channel` 维度持久化消息，存储于 `<workspace>/chats/sessions.json`
- 工具审批：支持待审批工具调用的展示与通过 / 拒绝
- Push messages：前端可轮询并展示运行中的 push message 事件
- Agent 管理：支持列表、创建、编辑、启停、reload、删除
- 文件管理：支持 workspace 相对路径浏览、读取、保存、删除文本文件
- Cron：支持创建、编辑、暂停、恢复、立即执行、删除
- MCP：支持 stdio / SSE / streamable-http 三类客户端基础接入
- Skills：支持列表、ZIP 导入、启停、渠道路由更新、删除
- Runtime / Env / Models：支持运行参数、全局环境变量、模型矩阵与当前 Agent 模型配置编辑

## 快速开始

### 环境要求

建议先准备：

- Java 17
- Maven
- Node.js 与 npm

### 编译后端

```bash
cd copaw/copaw-java
mvn -DskipTests compile
```

如果要完整打包：

```bash
cd copaw/copaw-java
mvn clean package -DskipTests
```

### 启动后端

开发模式常用启动方式：

```bash
cd copaw/copaw-java
mvn spring-boot:run -pl copaw-app -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

如果你要显式指定工作目录：

```bash
cd copaw/copaw-java
mvn spring-boot:run -pl copaw-app -Dspring-boot.run.arguments="--copaw.working-dir=/path/to/your/copaw/workspace"
```

默认配置如下：

- 服务端口：`8080`
- API 前缀：`/api`
- 默认工作目录：`${user.home}/.copaw`
- `dev` profile 下工作目录：`${user.home}/.copaw-dev`

## 验证前端

`copaw-frontend` 是一个 Vue 2.7.16 + Element UI 2.15.14 的轻量验证前端，用于手工验收当前 Java 侧已经打通的 P0/P1 能力。

启动方式：

```bash
cd copaw/copaw-java/copaw-frontend
npm install
npm run serve
```

浏览器打开：

```text
http://localhost:18081
```

默认情况下，开发模式会把 `/api` 代理到 `http://localhost:8080`。如果后端端口不同，可以这样运行：

```bash
cd copaw/copaw-java/copaw-frontend
VUE_APP_API_TARGET=http://localhost:8081 npm run serve
```

前端构建验证命令：

```bash
cd copaw/copaw-java/copaw-frontend
npm run build
```

前端当前主要页面包括：

| 路由 | 作用 |
| --- | --- |
| `/chat` | SSE 聊天、附件上传、push messages、审批卡片 |
| `/sessions` | 真实持久化会话列表与消息详情 |
| `/channels` | 频道启停 |
| `/cron` | 定时任务管理 |
| `/heartbeat` | 心跳配置 CRUD |
| `/files` | Workspace 文件浏览与文本文件编辑 |
| `/skills` | 技能管理 |
| `/tools` | 工具列表 |
| `/mcp` | MCP 客户端管理 |
| `/runtime` | 运行参数管理 |
| `/agents` | Agent / Workspace 管理 |
| `/models` | 全局 Agent 模型矩阵与当前 Agent 模型编辑 |
| `/skillpool` | 技能池管理 |
| `/env` | 全局环境变量 KV 管理 |

## 当前稳定实现约束

为了避免后续继续踩同一类坑，下面这些结论可以直接视为当前阶段的工程约束：

1. `AgentRunner` 当前优先调用 AgentScope-Java 的 `call(List<Msg>)`，再由 CoPaw 自己转换成 SSE delta；不要默认假设已有稳定逐 token streaming API 可以直接替换。
2. `Workspace` 的 cron 链路已经稳定为 `AppConfig.TaskScheduler -> MultiAgentManager -> Workspace -> CronManager -> CronController`。
3. 配置与持久化 JSON 统一通过 `CoPawObjectMapperFactory` 输出 snake_case，当前已覆盖 `config.json`、`agent.json`、`crons/jobs.json` 等关键文件。
4. `AgentRunner.normalizeAgentText()` 不能只依赖 `Msg.getTextContent()`；AgentScope-Java 的返回可能包含非 `TextBlock` 内容块，当前实现会尽量保留非工具块信息，避免被固定空响应占位掩盖真实问题。
5. `FilesPage` 只对 `AGENTS.md`、`SOUL.md`、`PROFILE.md` 显示启用 / 禁用开关，且排序固定优先于其他 Markdown 文件。
6. `/chat` 与 `/sessions` 走 full-height 页面模式，页面内部自己管理滚动，不再依赖整个右侧容器滚动。

## 与 Python 版的关系

`copaw-java` 的目标是重建 CoPaw 的核心运行时闭环，而不是简单做语法层面的端到端翻译。当前最成熟的参考实现仍然是 `copaw-python`，尤其是在渠道系统、技能管理复杂逻辑和整体功能覆盖率上。

如果你需要对照原始行为或补齐迁移细节，建议优先查看这些 Python 侧文件：

- `copaw-python/src/copaw/app/multi_agent_manager.py`
- `copaw-python/src/copaw/app/workspace/workspace.py`
- `copaw-python/src/copaw/agents/react_agent.py`
- `copaw-python/src/copaw/agents/skills_manager.py`

## 下一步方向

接下来最值得继续推进的部分主要有：

- 把 `memory_search` 从关键词检索升级为向量 / 语义检索
- 补齐 Console 断线续流、reconnect、stream store 等运行时细节
- 继续迁移 Python 版的渠道系统，尤其是 13+ 渠道适配器
- 继续提高 Java 侧对 Python 版 Workspace / Skills / Runtime 细节的覆盖率
- 在保证可运行闭环的前提下，再考虑更完整的控制台体验和更稳的流式能力

## 参考入口

如果你准备继续推进这个项目，建议优先从下面这些文件开始：

- `pom.xml`
- `DEVELOPMENT.md`
- `copaw-workspace/src/main/java/io/copaw/workspace/Workspace.java`
- `copaw-workspace/src/main/java/io/copaw/workspace/AgentRunner.java`
- `copaw-api/src/main/java/io/copaw/api/controller/WorkspaceController.java`
- `copaw-frontend/src/router/index.js`
- `copaw-frontend/README.md`

这份 README 的目标只有一个：让刚进 `copaw-java` 的人能迅速知道这项目是什么、现在做到哪里、怎么跑、边界在哪、接下来该从哪里继续干。
