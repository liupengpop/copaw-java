package io.copaw.api.controller;

import io.copaw.common.config.AgentConfigLoader;
import io.copaw.common.config.AgentProfileConfig;
import io.copaw.common.config.CoPawRootConfig;
import io.copaw.workspace.MultiAgentManager;
import io.copaw.workspace.Workspace;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Workspace-level REST APIs for sessions, tools, env variables.
 *
 * Endpoints:
 *   GET    /sessions?agentId=      - List sessions (stub)
 *   GET    /tools?agentId=         - List available tools for agent
 *   GET    /env                    - Get environment variables (non-secret keys only)
 *   POST   /env                    - Upsert environment variable
 *   DELETE /env/{key}              - Delete environment variable
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WorkspaceController {

    private final MultiAgentManager multiAgentManager;
    private final CoPawRootConfig rootConfig;
    private final Path rootConfigPath;

    // ------------------------------------------------------------------
    // GET /sessions?agentId= - list persisted sessions
    // GET /sessions/{chatId}/messages?agentId= - get persisted messages
    // ------------------------------------------------------------------

    @GetMapping("/sessions")
    public Map<String, Object> listSessions(
            @RequestParam("agentId") String agentId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "channel", required = false) String channel) throws IOException {
        Workspace ws = multiAgentManager.getOrCreate(agentId);
        List<io.copaw.workspace.ChatSessionStore.ChatSpec> chats = ws.getChatSessionStore().listChats(userId, channel);

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.min(safePage * safeSize, chats.size());
        int toIndex = Math.min(fromIndex + safeSize, chats.size());

        List<Map<String, Object>> sessions = chats.subList(fromIndex, toIndex).stream()
                .map(chat -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", chat.getId());
                    item.put("name", chat.getName());
                    item.put("sessionId", chat.getSessionId());
                    item.put("userId", chat.getUserId());
                    item.put("channel", chat.getChannel());
                    item.put("createdAt", chat.getCreatedAt());
                    item.put("updatedAt", chat.getUpdatedAt());
                    item.put("preview", chat.getLastMessagePreview());
                    item.put("messageCount", chat.getMessageCount());
                    return item;
                })
                .toList();

        return Map.of(
                "sessions", sessions,
                "total", chats.size(),
                "page", safePage,
                "size", safeSize
        );
    }

    @GetMapping("/sessions/{chatId}/messages")
    public Map<String, Object> getSessionMessages(
            @PathVariable("chatId") String chatId,
            @RequestParam("agentId") String agentId) throws IOException {
        Workspace ws = multiAgentManager.getOrCreate(agentId);
        List<Map<String, Object>> messages = ws.getChatSessionStore().getMessages(chatId).stream()
                .map(msg -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("role", msg.getRole());
                    item.put("content", msg.getContent());
                    item.put("createdAt", msg.getCreatedAt());
                    return item;
                })
                .toList();
        return Map.of("messages", messages, "total", messages.size(), "chatId", chatId);
    }

    // ------------------------------------------------------------------
    // GET /tools?agentId= - list registered tools for an agent
    // ------------------------------------------------------------------

    @GetMapping("/tools")
    public Map<String, Object> listTools(@RequestParam("agentId") String agentId) {
        Workspace ws = multiAgentManager.getOrCreate(agentId);
        List<Map<String, Object>> tools = new ArrayList<>();

        // Built-in workspace tools
        tools.add(builtinTool("memory_store", "存储记忆片段到长期记忆"));
        tools.add(builtinTool("memory_search", "从长期记忆中搜索相关片段"));
        tools.add(builtinTool("file_read", "读取 workspace 文件内容"));
        tools.add(builtinTool("file_write", "写入内容到 workspace 文件"));
        tools.add(builtinTool("tool_guard_approve", "等待人工审批工具调用"));

        // MCP tools (listed by client)
        ws.getMcpClientManager().listConfigs().forEach(cfg -> {
            if (cfg.isEnabled()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", "mcp:" + cfg.getId());
                entry.put("description", "MCP client: " + cfg.getId() + " (" + cfg.getTransport() + ")");
                entry.put("source", "mcp");
                entry.put("enabled", true);
                tools.add(entry);
            }
        });

        return Map.of("tools", tools, "total", tools.size());
    }

    // ------------------------------------------------------------------
    // GET /env          - list env var keys (values redacted for secrets)
    // POST /env         - upsert env var
    // DELETE /env/{key} - delete env var
    // ------------------------------------------------------------------

    @GetMapping("/env")
    public Map<String, Object> listEnv() throws IOException {
        Map<String, String> envMap = loadEnvMap();
        List<Map<String, Object>> entries = new ArrayList<>();
        envMap.forEach((k, v) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", k);
            // Redact values that look like secrets
            entry.put("value", isSecretKey(k) ? "••••••••" : v);
            entry.put("secret", isSecretKey(k));
            entries.add(entry);
        });
        entries.sort(Comparator.comparing(e -> String.valueOf(e.get("key"))));
        return Map.of("env", entries, "total", entries.size());
    }

    @PostMapping("/env")
    public Map<String, Object> upsertEnv(@RequestBody EnvEntryRequest req) throws IOException {
        if (req.getKey() == null || req.getKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key is required");
        }
        String key = req.getKey().trim();
        String value = req.getValue() != null ? req.getValue() : "";
        Map<String, String> envMap = loadEnvMap();
        envMap.put(key, value);
        saveEnvMap(envMap);
        return Map.of("saved", true, "key", key);
    }

    @DeleteMapping("/env/{key}")
    public Map<String, Object> deleteEnv(@PathVariable("key") String key) throws IOException {
        Map<String, String> envMap = loadEnvMap();
        boolean removed = envMap.remove(key) != null;
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Env key not found: " + key);
        }
        saveEnvMap(envMap);
        return Map.of("deleted", true, "key", key);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private Map<String, Object> builtinTool(String name, String description) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("description", description);
        t.put("source", "builtin");
        t.put("enabled", true);
        return t;
    }

    private boolean isSecretKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("key") || lower.contains("secret") || lower.contains("token")
                || lower.contains("password") || lower.contains("passwd");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> loadEnvMap() throws IOException {
        Path envFile = getEnvFilePath();
        if (!Files.exists(envFile)) {
            return new LinkedHashMap<>();
        }
        String content = Files.readString(envFile).trim();
        if (content.isEmpty()) {
            return new LinkedHashMap<>();
        }
        // Simple .env style: KEY=VALUE lines (ignoring comments)
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            if (eq < 1) continue;
            String k = trimmed.substring(0, eq).trim();
            String v = trimmed.substring(eq + 1).trim();
            // Strip optional surrounding quotes
            if (v.length() >= 2 &&
                ((v.startsWith("\"") && v.endsWith("\"")) ||
                 (v.startsWith("'") && v.endsWith("'")))) {
                v = v.substring(1, v.length() - 1);
            }
            result.put(k, v);
        }
        return result;
    }

    private void saveEnvMap(Map<String, String> envMap) throws IOException {
        Path envFile = getEnvFilePath();
        Files.createDirectories(envFile.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# CoPaw environment variables\n");
        sb.append("# Managed by CoPaw console - do not edit manually\n\n");
        envMap.forEach((k, v) -> {
            // Quote value if it contains spaces or special chars
            String safeValue = v.contains(" ") || v.contains("#") || v.isEmpty()
                    ? "\"" + v.replace("\"", "\\\"") + "\""
                    : v;
            sb.append(k).append('=').append(safeValue).append('\n');
        });
        Files.writeString(envFile, sb.toString());
    }

    private Path getEnvFilePath() {
        return rootConfigPath.getParent().resolve(".env");
    }

    @Data
    public static class EnvEntryRequest {
        private String key;
        private String value;
    }
}
