package io.copaw.api.controller;

import io.copaw.common.config.McpClientConfig;
import io.copaw.workspace.McpClientManager;
import io.copaw.workspace.MultiAgentManager;
import io.copaw.workspace.Workspace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * MCP client management REST API.
 * Maps to Python: app/routers/mcp.py
 *
 * Endpoints:
 *   GET    /mcp?agentId=          - List MCP clients
 *   POST   /mcp?agentId=          - Add MCP client
 *   PUT    /mcp/{id}?agentId=     - Update MCP client
 *   DELETE /mcp/{id}?agentId=     - Remove MCP client
 *   POST   /mcp/{id}/enable?agentId=  - Enable client
 *   POST   /mcp/{id}/disable?agentId= - Disable client
 */
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpController {

    private final MultiAgentManager multiAgentManager;

    @GetMapping
    public Map<String, Object> listClients(@RequestParam String agentId) {
        McpClientManager mgr = getManager(agentId);
        return Map.of("clients", mgr.listConfigs());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addClient(@RequestParam String agentId,
                                          @RequestBody McpClientConfig config) throws Exception {
        McpClientManager mgr = getManager(agentId);
        mgr.addClient(config);
        return Map.of("added", true, "id", config.getId());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> removeClient(@RequestParam String agentId,
                                             @PathVariable("id") String clientId) {
        McpClientManager mgr = getManager(agentId);
        boolean removed = mgr.removeClient(clientId);
        if (!removed) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP client not found: " + clientId);
        return Map.of("removed", true, "id", clientId);
    }

    @PostMapping("/{id}/enable")
    public Map<String, Object> enableClient(@RequestParam String agentId,
                                             @PathVariable("id") String clientId) throws Exception {
        McpClientManager mgr = getManager(agentId);
        List<McpClientConfig> configs = mgr.listConfigs();
        McpClientConfig cfg = configs.stream()
                .filter(c -> c.getId().equals(clientId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        cfg.setEnabled(true);
        mgr.addClient(cfg);
        return Map.of("enabled", true, "id", clientId);
    }

    @PostMapping("/{id}/disable")
    public Map<String, Object> disableClient(@RequestParam String agentId,
                                              @PathVariable("id") String clientId) {
        McpClientManager mgr = getManager(agentId);
        mgr.removeClient(clientId);
        return Map.of("disabled", true, "id", clientId);
    }

    private McpClientManager getManager(String agentId) {
        Workspace ws = multiAgentManager.getOrCreate(agentId);
        return ws.getMcpClientManager();
    }
}
