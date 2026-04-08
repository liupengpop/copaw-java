package io.copaw.workspace;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.copaw.common.config.McpClientConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages MCP (Model Context Protocol) client connections.
 * Maps to Python: MCPClientManager in app/mcp/
 *
 * Supports three transport types:
 * - stdio:  launch a local subprocess
 * - sse:    connect to an SSE endpoint
 * - http:   connect to a streamable HTTP endpoint
 */
@Slf4j
public class McpClientManager {

    private final List<McpClientConfig> configs;
    private final Map<String, McpClientWrapper> clients = new ConcurrentHashMap<>();

    public McpClientManager(List<McpClientConfig> configs) {
        this.configs = configs != null ? new ArrayList<>(configs) : new ArrayList<>();
    }

    public void start() {
        for (McpClientConfig cfg : configs) {
            if (!cfg.isEnabled()) continue;
            try {
                connectClient(cfg);
            } catch (Exception e) {
                log.error("Failed to connect MCP client '{}': {}", cfg.getId(), e.getMessage(), e);
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
        configs.removeIf(existing -> existing.getId().equals(cfg.getId()));
        configs.add(cfg);
        if (cfg.isEnabled()) {
            connectClient(cfg);
        }
    }

    public boolean removeClient(String clientId) {
        McpClientWrapper client = clients.remove(clientId);
        if (client == null) return false;
        disconnectClient(clientId, client);
        configs.removeIf(c -> c.getId().equals(clientId));
        return true;
    }

    public List<McpClientConfig> listConfigs() {
        return List.copyOf(configs);
    }

    public List<McpClientWrapper> listClients() {
        return List.copyOf(clients.values());
    }

    public boolean hasClient(String clientId) {
        return clients.containsKey(clientId);
    }

    private void connectClient(McpClientConfig cfg) {
        if (cfg.getId() == null || cfg.getId().isBlank()) {
            throw new IllegalArgumentException("MCP client id is required");
        }

        log.info("Connecting MCP client '{}' via {} transport...", cfg.getId(), cfg.getTransport());
        McpClientBuilder builder = McpClientBuilder.create(cfg.getId());
        configureTransport(builder, cfg);
        applyHeaders(builder, cfg);

        McpClientWrapper client = builder.buildAsync().block();
        if (client == null) {
            throw new IllegalStateException("MCP client builder returned null for " + cfg.getId());
        }
        client.initialize().block();
        clients.put(cfg.getId(), client);
        log.info("MCP client '{}' connected", cfg.getId());
    }

    private void configureTransport(McpClientBuilder builder, McpClientConfig cfg) {
        String transport = normalizeTransport(cfg.getTransport());
        switch (transport) {
            case "stdio" -> {
                if (cfg.getCommand() == null || cfg.getCommand().isBlank()) {
                    throw new IllegalArgumentException("stdio MCP client requires command: " + cfg.getId());
                }
                List<String> command = new ArrayList<>();
                command.add(cfg.getCommand());
                if (cfg.getArgs() != null && !cfg.getArgs().isEmpty()) {
                    command.addAll(cfg.getArgs());
                }
                String executable = command.get(0);
                String[] args = command.size() > 1
                        ? command.subList(1, command.size()).toArray(new String[0])
                        : new String[0];
                builder.stdioTransport(executable, args);
            }
            case "sse" -> builder.sseTransport(requireUrl(cfg));
            case "http", "streamable-http", "streamable_http" ->
                    builder.streamableHttpTransport(requireUrl(cfg));
            default -> throw new IllegalArgumentException(
                    "Unsupported MCP transport for client '" + cfg.getId() + "': " + cfg.getTransport());
        }
    }

    private void applyHeaders(McpClientBuilder builder, McpClientConfig cfg) {
        if (cfg.getHeaders() == null || cfg.getHeaders().isEmpty()) {
            return;
        }
        cfg.getHeaders().forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                builder.header(key, value);
            }
        });
    }

    private String requireUrl(McpClientConfig cfg) {
        if (cfg.getUrl() == null || cfg.getUrl().isBlank()) {
            throw new IllegalArgumentException("MCP client requires url: " + cfg.getId());
        }
        return cfg.getUrl();
    }

    private String normalizeTransport(String transport) {
        if (transport == null || transport.isBlank()) {
            return "stdio";
        }
        return transport.trim().toLowerCase(Locale.ROOT);
    }

    private void disconnectClient(String id, McpClientWrapper client) {
        log.debug("Disconnecting MCP client: {}", id);
        client.close();
    }
}
