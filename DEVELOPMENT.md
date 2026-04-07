# CoPaw Java - 开发者指南

> 最后更新：2026-04-07  
> 项目路径：`/Users/lmax/WorkBuddy/workbuddy/copaw/copaw-java/`  
> 原始 Python 项目：`/Users/lmax/WorkBuddy/workbuddy/copaw/copaw-python/src/copaw/`

---

## 一、项目结构

```
copaw-java/
├── pom.xml                     ← 根 POM，多模块聚合
├── copaw-common/               ← 共享领域模型、配置类
│   └── src/main/java/io/copaw/common/
│       └── config/             ← 所有配置 POJO（对应 Python config.py）
├── copaw-core/                 ← Agent 核心：内置工具、ToolGuard
│   └── src/main/java/io/copaw/core/
│       ├── tools/              ← BuiltinTool 接口 + impl/（read/write/edit/shell/glob/grep/time）
│       └── security/           ← ToolGuardEngine、FilePathToolGuardian、RuleBasedToolGuardian
├── copaw-memory/               ← 记忆管理
│   └── src/main/java/io/copaw/memory/
│       ├── MemoryManager.java  ← 接口（含 Message record）
│       └── ReMeLightMemoryManager.java ← 默认实现（滑动窗口 + 自动压缩）
├── copaw-skills/               ← 技能管理
│   └── src/main/java/io/copaw/skills/
│       ├── SkillMeta.java      ← 技能元数据（从 SKILL.md frontmatter 解析）
│       ├── SkillMdParser.java  ← SKILL.md 解析器（YAML frontmatter + SHA256）
│       └── SkillService.java   ← 技能 CRUD、ZIP 导入、渠道路由
├── copaw-workspace/            ← 工作区 + 运行时管理
│   └── src/main/java/io/copaw/workspace/
│       ├── Workspace.java      ← 单个 Agent 运行时容器
│       ├── MultiAgentManager.java ← 多 Workspace 生命周期（懒加载、热重载）
│       ├── AgentRunner.java    ← 消息处理 + SSE 流式输出（Reactor Flux）
│       └── McpClientManager.java ← MCP 客户端连接管理
├── copaw-cron/                 ← 定时任务
│   └── src/main/java/io/copaw/cron/
│       ├── CronJobSpec.java    ← Cron 任务定义
│       ├── JsonJobRepository.java ← JSON 文件持久化
│       └── CronManager.java   ← Spring TaskScheduler 动态调度
├── copaw-api/                  ← REST API 控制器（WebFlux）
│   └── src/main/java/io/copaw/api/controller/
│       ├── ConsoleController.java  ← /console/chat SSE、/upload
│       ├── AgentsController.java   ← /agents CRUD
│       ├── McpController.java      ← /mcp CRUD
│       ├── SkillsController.java   ← /skills CRUD + ZIP
│       └── CronController.java     ← /cron CRUD
└── copaw-app/                  ← Spring Boot 启动入口
    ├── src/main/java/io/copaw/app/
    │   ├── CoPawApplication.java   ← main()
    │   └── config/
    │       ├── AppConfig.java      ← Bean 配置、CORS
    │       └── GracefulShutdownHook.java ← 优雅关闭
    └── src/main/resources/
        └── application.yml     ← 服务器端口、日志、copaw.working-dir
```

---

## 二、模块依赖图

```
copaw-app
  └── copaw-api
        ├── copaw-workspace
        │     ├── copaw-core
        │     │     ├── copaw-common
        │     │     └── copaw-memory
        │     ├── copaw-skills
        │     └── copaw-cron
        └── copaw-common
```

---

## 三、Python → Java 映射速查表

| Python 文件/类 | Java 等价 | 状态 |
|---|---|---|
| `config/config.py` → `BaseChannelConfig` | `copaw-common/.../config/BaseChannelConfig.java` | ✅ 已实现 |
| `config/config.py` → `AgentProfileConfig` | `copaw-common/.../config/AgentProfileConfig.java` | ✅ 已实现 |
| `config/config.py` → `MCPClientConfig` | `copaw-common/.../config/McpClientConfig.java` | ✅ 已实现 |
| `config/config.py` → `ToolGuardConfig` | `copaw-common/.../config/ToolGuardConfig.java` | ✅ 已实现 |
| `config/config.py` → `MemoryConfig` | `copaw-common/.../config/MemoryConfig.java` | ✅ 已实现 |
| `app/multi_agent_manager.py` | `copaw-workspace/.../MultiAgentManager.java` | ✅ 已实现 |
| `app/workspace/workspace.py` | `copaw-workspace/.../Workspace.java` | ✅ 已实现 |
| `app/runner/runner.py` | `copaw-workspace/.../AgentRunner.java` | ✅ 骨架完成，**需接入 AS-Java** |
| `agents/react_agent.py` → `CoPawAgent` | **TODO** - 需接入 `io.agentscope:agentscope-spring-boot-starter` | ⏳ 待接入 |
| `agents/memory/base_memory_manager.py` | `copaw-memory/.../MemoryManager.java` | ✅ 已实现 |
| `agents/memory/reme_light_memory_manager.py` | `copaw-memory/.../ReMeLightMemoryManager.java` | ✅ 已实现 |
| `agents/skills_manager.py` → `SkillService` | `copaw-skills/.../SkillService.java` | ✅ 已实现 |
| `agents/skills_manager.py` → SKILL.md 解析 | `copaw-skills/.../SkillMdParser.java` | ✅ 已实现 |
| `security/tool_guard/engine.py` | `copaw-core/.../security/ToolGuardEngine.java` | ✅ 已实现 |
| `security/tool_guard/guardians/file_guardian.py` | `copaw-core/.../security/FilePathToolGuardian.java` | ✅ 已实现 |
| `security/tool_guard/guardians/rule_guardian.py` | `copaw-core/.../security/RuleBasedToolGuardian.java` | ✅ 已实现 |
| `agents/tools/` → read_file | `copaw-core/.../tools/impl/ReadFileTool.java` | ✅ |
| `agents/tools/` → write_file | `copaw-core/.../tools/impl/WriteFileTool.java` | ✅ |
| `agents/tools/` → edit_file | `copaw-core/.../tools/impl/EditFileTool.java` | ✅ |
| `agents/tools/` → execute_shell_command | `copaw-core/.../tools/impl/ExecuteShellCommandTool.java` | ✅ |
| `agents/tools/` → glob_search | `copaw-core/.../tools/impl/GlobSearchTool.java` | ✅ |
| `agents/tools/` → grep_search | `copaw-core/.../tools/impl/GrepSearchTool.java` | ✅ |
| `agents/tools/` → get_current_time | `copaw-core/.../tools/impl/GetCurrentTimeTool.java` | ✅ |
| `agents/tools/` → browser_use | **TODO** - Playwright Java | ⏳ |
| `agents/tools/` → desktop_screenshot | **TODO** | ⏳ |
| `app/crons/manager.py` | `copaw-cron/.../CronManager.java` | ✅ 已实现 |
| `app/crons/repo/json_repo.py` | `copaw-cron/.../JsonJobRepository.java` | ✅ 已实现 |
| `app/routers/console.py` | `copaw-api/.../ConsoleController.java` | ✅ 已实现 |
| `app/routers/agents.py` | `copaw-api/.../AgentsController.java` | ✅ 已实现 |
| `app/routers/mcp.py` | `copaw-api/.../McpController.java` | ✅ 已实现 |
| `app/routers/skills.py` | `copaw-api/.../SkillsController.java` | ✅ 已实现 |
| `app/routers/cron.py` | `copaw-api/.../CronController.java` | ✅ 骨架完成 |
| `app/channels/` | **TODO** - 渠道系统（Phase 3） | ⏳ |
| `config/config.py` → `FeishuConfig` | `copaw-common/.../config/FeishuChannelConfig.java` | ✅ |
| `config/config.py` → `DingTalkConfig` | `copaw-common/.../config/DingTalkChannelConfig.java` | ✅ |
| `config/config.py` → `WecomConfig` | `copaw-common/.../config/WecomChannelConfig.java` | ✅ |

---

## 四、当前 TODO 与接入指引

### 4.1 已完成：接入真实 AgentScope-Java ReActAgent

**文件**：`copaw-workspace/src/main/java/io/copaw/workspace/AgentRunner.java`

当前已经不再使用占位响应，而是走真实 AgentScope-Java 1.0.11 调用链。实现策略采用保守版 P0 路径：优先使用稳定的同步 `call(List<Msg>)`，拿到完整回复后再在 CoPaw 自己的 SSE 层按 24 字符切片输出，避免提前绑定不够确定的逐 token API。

当前关键实现点：

```java
ReActAgent agent = ReActAgent.builder()
        .name(firstNonBlank(config.getName(), agentId))
        .sysPrompt(buildSystemPrompt())
        .model(buildModel())
        .toolkit(new Toolkit())
        .memory(new InMemoryMemory())
        .maxIters(config.getRunning() != null ? config.getRunning().getMaxIters() : 30)
        .build();

Msg responseMsg = agent.call(promptMessages).block(AGENT_CALL_TIMEOUT);
```

目前已支持的 provider 映射：`openai/openai-compatible/dashscope/deepseek/moonshot/kimi/openrouter/siliconflow/vllm` → `OpenAIChatModel`，`anthropic` → `AnthropicChatModel`，`ollama` → `OllamaChatModel`。

### 4.2 已完成：CronController ↔ Workspace ↔ CronManager 接线

**文件**：`copaw-workspace/src/main/java/io/copaw/workspace/Workspace.java`、`copaw-workspace/src/main/java/io/copaw/workspace/MultiAgentManager.java`、`copaw-api/src/main/java/io/copaw/api/controller/CronController.java`

当前链路已经打通：`AppConfig` 提供 `TaskScheduler` → `MultiAgentManager` 构造传递 → `Workspace.start()` 初始化 `CronManager` → `CronController` 通过 `ws.getCronManager()` 直接拿实例。

核心代码：

```java
cronManager = new CronManager(
        taskScheduler,
        workspaceDir,
        (scheduledAgentId, scheduledMessage) -> runner.streamChat(
                        UUID.randomUUID().toString(),
                        scheduledMessage,
                        Map.of("channel", "_cron", "agent_id", scheduledAgentId))
                .subscribe()
);
cronManager.start();
```

### 4.3 MemoryManager 需要接入实际 LLM 做摘要

**文件**：`copaw-workspace/src/main/java/io/copaw/workspace/Workspace.java`

在 `start()` 方法中，替换 summarizer lambda：
```java
memoryManager = new ReMeLightMemoryManager(
    config.getMemory(),
    messages -> {
        // 调用 LLM 生成摘要
        // 使用 Spring AI ChatClient 或 AgentScope-Java model
        return llmClient.call(buildSummarizationPrompt(messages));
    }
);
```

### 4.4 安全配置需要每个 Workspace 独立的 ToolGuardEngine

当前 `ToolGuardEngine` 是 Spring singleton。需要改为每个 Workspace 独立实例：
- `Workspace.start()` 中读取 `config.getToolGuard()` 并创建独立的 `ToolGuardEngine`
- `CoPawAgent` 在每次工具调用前调用 `toolGuardEngine.guard(toolName, params)`

---

## 五、关键设计决策

### 5.1 配置文件格式（与 Python 兼容）

- 根配置：`~/.copaw/config.json` → `CoPawRootConfig`
- 每个 Agent 配置：`<workspaceDir>/agent.json` → `AgentProfileConfig`
- `agent.json`、根 `config.json`、`crons/jobs.json` 已统一走 `CoPawObjectMapperFactory`
- 统一策略：`PropertyNamingStrategies.SNAKE_CASE` + `findAndRegisterModules()`，确保 Java camelCase 字段和 Python snake_case 文件双向兼容

### 5.2 SSE 流式响应

使用 WebFlux `Flux<String>` 实现 SSE：
- `AgentRunner.streamChat()` 返回 `Flux<String>`
- `ConsoleController.chat()` 用 `produces = MediaType.TEXT_EVENT_STREAM_VALUE` 发送
- Agent 在守护工作线程中运行，不阻塞 WebFlux 调用线程
- 当前 P0 采用“真实 AgentScope 同步调用 + CoPaw 自己分段输出”的折中方案，先保证链路可跑

### 5.3 热重载流程

```
reloadAgent(agentId)
  ├─ createWorkspace(agentId)  ← 耗时，在锁外
  ├─ newInstance.start()       ← 耗时，在锁外
  ├─ [获取写锁]
  │     agents.put(agentId, newInstance)  ← 原子替换
  └─ [释放写锁]
      └─ cleanupExecutor.schedule(oldInstance.stop(), 5s)  ← 延迟清理
```

### 5.4 技能系统

- SKILL.md 在 `---` 之间的 YAML frontmatter 通过 SnakeYAML 解析
- 技能清单保存在 `<workspaceDir>/skills/skills-manifest.json`
- 使用 Java NIO `FileLock` 做跨进程并发安全（对应 Python 的 `fcntl`）
- ZIP 安全检查：路径穿越（`..`）、绝对路径、超大文件（200MB）

---

## 六、API 端点速查

所有 API 以 `/api` 为前缀（`spring.webflux.base-path=/api`）。

| 方法 | 路径 | 功能 | 控制器 |
|---|---|---|---|
| POST | `/api/console/chat?agentId=` | SSE 聊天 | ConsoleController |
| POST | `/api/console/chat/stop?agentId=&chatId=` | 停止聊天 | ConsoleController |
| POST | `/api/console/upload?agentId=` | 上传文件 | ConsoleController |
| GET  | `/api/agents` | 列出所有 Agent | AgentsController |
| POST | `/api/agents` | 创建 Agent | AgentsController |
| PUT  | `/api/agents/{id}` | 更新 Agent | AgentsController |
| DELETE | `/api/agents/{id}` | 删除 Agent | AgentsController |
| POST | `/api/agents/{id}/reload` | 热重载 Agent | AgentsController |
| GET  | `/api/mcp?agentId=` | 列出 MCP 客户端 | McpController |
| POST | `/api/mcp?agentId=` | 添加 MCP 客户端 | McpController |
| DELETE | `/api/mcp/{id}?agentId=` | 删除 MCP 客户端 | McpController |
| GET  | `/api/skills?agentId=` | 列出技能 | SkillsController |
| POST | `/api/skills/import-zip?agentId=` | 导入 ZIP 技能 | SkillsController |
| POST | `/api/skills/{dir}/enable?agentId=` | 启用技能 | SkillsController |
| POST | `/api/skills/{dir}/disable?agentId=` | 禁用技能 | SkillsController |
| PUT  | `/api/skills/{dir}/channels?agentId=` | 更新渠道路由 | SkillsController |
| DELETE | `/api/skills/{dir}?agentId=` | 删除技能 | SkillsController |
| GET  | `/api/cron?agentId=` | 列出定时任务 | CronController |
| POST | `/api/cron?agentId=` | 创建/更新任务 | CronController |
| DELETE | `/api/cron/{id}?agentId=` | 删除任务 | CronController |
| POST | `/api/cron/{id}/run?agentId=` | 立即触发 | CronController |

---

## 七、构建与运行

```bash
# 进入项目目录
cd /Users/lmax/WorkBuddy/workbuddy/copaw/copaw-java

# 编译（跳过测试）
mvn clean package -DskipTests

# 运行
java -jar copaw-app/target/copaw-app-1.0.0-SNAPSHOT.jar \
  --copaw.working-dir=/path/to/your/copaw/workspace

# 开发模式
mvn spring-boot:run -pl copaw-app \
  -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

---

## 八、下一步开发优先级

| 优先级 | 任务 | 涉及文件 |
|---|---|---|
| P0 | 接入 AgentScope-Java ReActAgent | `AgentRunner.runAgentAndEmit()` |
| P0 | 接入实际 LLM 做记忆摘要 | `Workspace.start()` - summarizer lambda |
| P0 | CronManager 接入 Workspace | `Workspace.java` + `CronController.java` |
| P0 | JSON 字段名 snake_case 兼容 Python | `ObjectMapper` 配置 |
| P1 | 完善 ToolGuardEngine 接入 Agent | `AgentRunner` + `ToolGuardEngine` |
| P1 | Console 渠道补全（push messages store） | `ConsoleController.getPushMessages()` |
| P1 | 完善 AgentRunner 的工具调用审批流 | `AgentRunner.java` |
| P1 | 接入 AgentScope-Java McpClientBuilder | `McpClientManager.connectClient()` |
| P1 | 语义记忆搜索（向量数据库） | `ReMeLightMemoryManager.search()` |
| P2 | 飞书渠道 | 新建 `copaw-channel-feishu` 模块 |
| P2 | 钉钉渠道 | 新建 `copaw-channel-dingtalk` 模块 |
| P2 | Telegram 渠道 | 新建 `copaw-channel-telegram` 模块 |
