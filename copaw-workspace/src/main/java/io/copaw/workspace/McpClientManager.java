package io.copaw.workspace;

import io.copaw.common.config.McpClientConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages MCP (Model Context Protocol) client connections.
 * Maps to Python: MCPClientManager in app/mcp/
 *
 * Supports three transport types:
 * - stdio:  launch a local subprocess
 * - sse:    connect to an SSE endpoint
 * - http:   connect to an HTTP endpoint
 *
 * NOTE: Full AgentScope-Java McpClientBuilder integration should be wired here
 * once the dependency is confirmed available. For now this is a clean abstraction.
 */
@Slf4j
public class McpClientManager {

    private final List<McpClientConfig> configs;
    private final Map<String, Object> clients = new ConcurrentHashMap<>(); // id -> McpClient

    public McpClientManager(List<McpClientConfig> configs) {
        this.configs = configs != null ? configs : new ArrayList<>();
    }

    public void start() {
        for (McpClientConfig cfg : configs) {
            if (!cfg.isEnabled()) continue;
            try {
                connectClient(cfg);
            } catch (Exception e) {
                log.error("Failed to connect MCP client '{}': {}", cfg.getId(), e.getMessage());
            }
        }
        log.info("McpClientManager started with {} client(s)", clients.size());
    }

    public void stop() {
        clients.forEach((id, client) -> {
            try {
                disconnectClient(id, client);
            } catch (Exception e) {
                log.warn("Error disconnecting MCP client '{}': {}", id, e.getMessage());
            }
        });
        clients.clear();
        log.info("McpClientManager stopped");
    }

    public void addClient(McpClientConfig cfg) throws Exception {
        if (clients.containsKey(cfg.getId())) {
            removeClient(cfg.getId());
        }
        configs.add(cfg);
        connectClient(cfg);
    }

    public boolean removeClient(String clientId) {
        Object client = clients.remove(clientId);
        if (client == null) return false;
        disconnectClient(clientId, client);
        configs.removeIf(c -> c.getId().equals(clientId));
        return true;
    }

    public List<McpClientConfig> listConfigs() {
        return List.copyOf(configs);
    }

    public boolean hasClient(String clientId) {
        return clients.containsKey(clientId);
    }

    // ------------------------------------------------------------------
    // TODO: Wire actual AgentScope-Java McpClientBuilder here
    // ------------------------------------------------------------------

    /**
     * Connect to an MCP server.
     *
     * Replace this stub with actual AgentScope-Java MCP client creation:
     *
     *   McpClientBuilder builder = McpClientBuilder.builder();
     *   switch (cfg.getTransport()) {
     *     case "stdio" -> builder.stdioTransport(cfg.getCommand(), cfg.getArgs(), cfg.getEnv());
     *     case "sse"   -> builder.sseTransport(cfg.getUrl(), cfg.getHeaders());
     *     case "http"  -> builder.httpTransport(cfg.getUrl(), cfg.getHeaders());
     *   }
     *   McpClient client = builder.build();
     *   client.initialize();
     *   clients.put(cfg.getId(), client);
     */
    private void connectClient(McpClientConfig cfg) {
        // Stub: log and record as "connected"
        log.info("Connecting MCP client '{}' via {} transport...",
                cfg.getId(), cfg.getTransport());
        clients.put(cfg.getId(), new Object()); // placeholder
        log.info("MCP client '{}' connected (stub)", cfg.getId());
    }

    private void disconnectClient(String id, Object client) {
        log.debug("Disconnecting MCP client: {}", id);
        // Actual: client.close() or client.disconnect()
    }
}
