package io.copaw.workspace;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request-scoped context injected into tool methods via ToolExecutionContext.
 */
public record RuntimeToolContext(
        String agentId,
        String chatId,
        String sessionId,
        String userId,
        String channel,
        Path workspaceDir
) {
    public Map<String, String> asMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("agent_id", agentId);
        metadata.put("chat_id", chatId);
        metadata.put("session_id", sessionId);
        metadata.put("user_id", userId);
        metadata.put("channel", channel);
        metadata.put("workspace_dir", workspaceDir != null ? workspaceDir.toString() : "");
        return metadata;
    }
}
