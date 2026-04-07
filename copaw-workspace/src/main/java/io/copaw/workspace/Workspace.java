package io.copaw.workspace;

import io.copaw.common.config.AgentConfigLoader;
import io.copaw.common.config.AgentProfileConfig;
import io.copaw.cron.CronManager;
import io.copaw.memory.MemoryManager;
import io.copaw.memory.ReMeLightMemoryManager;
import io.copaw.skills.SkillService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Single agent workspace - encapsulates a complete independent agent runtime.
 *
 * Maps to Python: Workspace class in app/workspace/workspace.py
 *
 * Each Workspace contains:
 * - AgentProfileConfig  (loaded from agent.json)
 * - AgentRunner         (processes requests, returns SSE streams)
 * - MemoryManager       (conversation memory)
 * - McpClientManager    (MCP tool clients)
 * - CronManager         (scheduled tasks)
 * - SkillService        (workspace-level skills)
 *
 * Lifecycle: create → start() → [handle requests] → stop()
 */
@Slf4j
public class Workspace {

    @Getter private final String agentId;
    @Getter private final Path workspaceDir;
    private final TaskScheduler taskScheduler;

    @Getter private AgentProfileConfig config;
    @Getter private AgentRunner runner;
    @Getter private MemoryManager memoryManager;
    @Getter private McpClientManager mcpClientManager;
    @Getter private SkillService skillService;
    @Getter private CronManager cronManager;

    private volatile boolean started = false;

    public Workspace(String agentId, Path workspaceDir, TaskScheduler taskScheduler) {
        this.agentId = agentId;
        this.workspaceDir = workspaceDir.toAbsolutePath();
        this.taskScheduler = taskScheduler;
        log.debug("Workspace created: {} at {}", agentId, workspaceDir);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public synchronized void start() {
        if (started) {
            log.warn("Workspace {} already started", agentId);
            return;
        }

        log.info("Starting workspace: {}", agentId);

        // 1. Load configuration
        config = AgentConfigLoader.load(workspaceDir);
        config.setId(agentId);

        // 2. Initialize memory manager
        memoryManager = new ReMeLightMemoryManager(
                config.getMemory(),
                messages -> "[Summary placeholder - LLM summarizer not yet wired]"
        );
        memoryManager.start();

        // 3. Initialize MCP client manager
        mcpClientManager = new McpClientManager(config.getMcpClients());
        mcpClientManager.start();

        // 4. Initialize skill service (scan workspace skills)
        skillService = new SkillService();

        // 5. Initialize agent runner
        runner = new AgentRunner(agentId, workspaceDir, config, memoryManager,
                mcpClientManager, skillService);
        runner.start();

        // 6. Initialize cron manager after runner is ready
        cronManager = new CronManager(
                taskScheduler,
                workspaceDir,
                (scheduledAgentId, scheduledMessage) -> runner.streamChat(
                                UUID.randomUUID().toString(),
                                scheduledMessage,
                                Map.of("channel", "_cron", "agent_id", scheduledAgentId))
                        .subscribe(
                                ignored -> { },
                                error -> log.error("Cron-triggered chat failed for {}: {}",
                                        scheduledAgentId, error.getMessage())
                        )
        );
        cronManager.start();

        started = true;
        log.info("Workspace started: {}", agentId);
    }

    public synchronized void stop() {
        if (!started) return;
        log.info("Stopping workspace: {}", agentId);

        if (cronManager != null) cronManager.stop();
        if (runner != null) runner.stop();
        if (mcpClientManager != null) mcpClientManager.stop();
        if (memoryManager != null) memoryManager.stop();

        started = false;
        log.info("Workspace stopped: {}", agentId);
    }

    public boolean isStarted() {
        return started;
    }
}
