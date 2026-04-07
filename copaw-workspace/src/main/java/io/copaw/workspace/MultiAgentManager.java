package io.copaw.workspace;

import io.copaw.common.config.CoPawRootConfig;
import io.copaw.common.config.CoPawRootConfig.AgentProfileRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages multiple agent workspaces with lazy loading and zero-downtime hot reload.
 *
 * Maps to Python: MultiAgentManager in app/multi_agent_manager.py
 *
 * Key behaviors:
 * 1. Lazy loading: workspaces are created on first request
 * 2. Zero-downtime hot reload: new instance starts → atomic swap → old instance stops
 * 3. Thread-safe: ReadWriteLock per agent ID
 * 4. Concurrent startup: all configured agents can start in parallel
 */
@Service
@Slf4j
public class MultiAgentManager {

    private final ConcurrentHashMap<String, Workspace> agents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "workspace-cleanup");
                t.setDaemon(true);
                return t;
            });

    private volatile CoPawRootConfig rootConfig;
    private final Path configPath;
    private final TaskScheduler taskScheduler;

    public MultiAgentManager(Path configPath, TaskScheduler taskScheduler) {
        this.configPath = configPath;
        this.taskScheduler = taskScheduler;
        reloadRootConfig();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Get or create a workspace (lazy loading).
     * Thread-safe via per-agent write lock.
     */
    public Workspace getOrCreate(String agentId) {
        // Fast path: already loaded
        Workspace existing = agents.get(agentId);
        if (existing != null && existing.isStarted()) return existing;

        ReentrantReadWriteLock lock = locks.computeIfAbsent(agentId,
                id -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            // Double-check after acquiring lock
            existing = agents.get(agentId);
            if (existing != null && existing.isStarted()) return existing;

            log.info("Creating workspace (lazy load): {}", agentId);
            Workspace workspace = createWorkspace(agentId);
            workspace.start();
            agents.put(agentId, workspace);
            return workspace;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Hot reload an agent workspace with zero downtime.
     *
     * Steps:
     * 1. Create and start new instance (outside lock)
     * 2. Atomic swap (with lock, minimal hold time)
     * 3. Gracefully stop old instance (outside lock, may be delayed)
     */
    public boolean reloadAgent(String agentId) {
        Workspace oldInstance = agents.get(agentId);
        if (oldInstance == null) {
            log.debug("Agent not loaded, will be created on next request: {}", agentId);
            return false;
        }

        log.info("Hot reloading agent: {}", agentId);
        reloadRootConfig();

        // Step 1: Create and start new instance (slow, outside lock)
        Workspace newInstance;
        try {
            newInstance = createWorkspace(agentId);
            newInstance.start();
            log.info("New workspace instance started: {}", agentId);
        } catch (Exception e) {
            log.error("Failed to start new workspace instance for {}: {}", agentId, e.getMessage());
            return false;
        }

        // Step 2: Atomic swap (minimal lock time)
        ReentrantReadWriteLock lock = locks.computeIfAbsent(agentId,
                id -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            if (!agents.containsKey(agentId)) {
                log.warn("Agent {} was removed during reload, stopping new instance", agentId);
                newInstance.stop();
                return false;
            }
            oldInstance = agents.put(agentId, newInstance);
            log.info("Workspace instance swapped atomically: {}", agentId);
        } finally {
            lock.writeLock().unlock();
        }

        // Step 3: Gracefully stop old instance (outside lock)
        final Workspace finalOld = oldInstance;
        cleanupExecutor.schedule(() -> {
            try {
                finalOld.stop();
                log.info("Old workspace instance stopped after hot reload: {}", agentId);
            } catch (Exception e) {
                log.warn("Error stopping old workspace instance for {}: {}", agentId, e.getMessage());
            }
        }, 5, TimeUnit.SECONDS);

        return true;
    }

    /**
     * Stop a specific agent.
     */
    public boolean stopAgent(String agentId) {
        ReentrantReadWriteLock lock = locks.computeIfAbsent(agentId,
                id -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            Workspace ws = agents.remove(agentId);
            if (ws == null) return false;
            ws.stop();
            log.info("Agent stopped: {}", agentId);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Stop all agents (called on application shutdown).
     */
    public void stopAll() {
        log.info("Stopping all agents ({} running)...", agents.size());
        cleanupExecutor.shutdownNow();

        List<String> agentIds = new ArrayList<>(agents.keySet());
        for (String agentId : agentIds) {
            try {
                Workspace ws = agents.remove(agentId);
                if (ws != null) ws.stop();
            } catch (Exception e) {
                log.error("Error stopping agent {}: {}", agentId, e.getMessage());
            }
        }
        log.info("All agents stopped");
    }

    /**
     * Start all enabled agents from configuration (concurrent startup).
     */
    public Map<String, Boolean> startAllConfiguredAgents() {
        reloadRootConfig();
        Map<String, AgentProfileRef> profiles = rootConfig.getAgents().getProfiles();

        List<String> enabledIds = profiles.entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .toList();

        if (enabledIds.isEmpty()) {
            log.warn("No enabled agents configured");
            return Collections.emptyMap();
        }

        log.info("Starting {} configured agents...", enabledIds.size());
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(enabledIds.size(), 4));

        Map<String, Future<Boolean>> futures = new HashMap<>();
        for (String agentId : enabledIds) {
            futures.put(agentId, executor.submit(() -> {
                try {
                    getOrCreate(agentId);
                    return true;
                } catch (Exception e) {
                    log.error("Failed to start agent {}: {}", agentId, e.getMessage());
                    return false;
                }
            }));
        }

        executor.shutdown();
        Map<String, Boolean> results = new LinkedHashMap<>();
        futures.forEach((id, future) -> {
            try {
                results.put(id, future.get(60, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("Agent {} startup timeout/error: {}", id, e.getMessage());
                results.put(id, false);
            }
        });

        long succeeded = results.values().stream().filter(Boolean::booleanValue).count();
        log.info("Agent startup complete: {}/{} succeeded", succeeded, enabledIds.size());
        return results;
    }

    public List<String> listLoadedAgentIds() {
        return new ArrayList<>(agents.keySet());
    }

    public boolean isLoaded(String agentId) {
        return agents.containsKey(agentId);
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private Workspace createWorkspace(String agentId) {
        AgentProfileRef ref = rootConfig.getAgents().getProfiles().get(agentId);
        if (ref == null) {
            throw new IllegalArgumentException(
                    "Agent '" + agentId + "' not found in config. Available: "
                            + rootConfig.getAgents().getProfiles().keySet());
        }
        Path wsDir = Paths.get(ref.getWorkspaceDir()).toAbsolutePath();
        return new Workspace(agentId, wsDir, taskScheduler);
    }

    private void reloadRootConfig() {
        rootConfig = CoPawRootConfig.load(configPath);
    }
}
