package io.copaw.api.controller;

import io.copaw.workspace.AgentRunner;
import io.copaw.workspace.MultiAgentManager;
import io.copaw.workspace.Workspace;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Console channel REST API.
 * Maps to Python: app/routers/console.py
 *
 * Endpoints:
 *   POST /console/chat           - SSE streaming chat
 *   POST /console/chat/stop      - Stop active chat
 *   POST /console/upload         - Upload file for chat
 *   GET  /console/push-messages  - Pending push messages
 */
@RestController
@RequestMapping("/console")
@RequiredArgsConstructor
@Slf4j
public class ConsoleController {

    private final MultiAgentManager multiAgentManager;
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final Pattern SAFE_FILENAME =
            Pattern.compile("[^\\w.\\-]");

    // ------------------------------------------------------------------
    // POST /console/chat - SSE streaming
    // ------------------------------------------------------------------

    /**
     * Start a streaming chat session with an agent.
     * Returns Server-Sent Events (SSE) stream.
     *
     * Query param: agentId (required)
     * Body: ChatRequest JSON
     *
     * SSE event format:
     *   data: {"type":"start","content":{"chat_id":"..."}}
     *   data: {"type":"delta","content":"chunk text..."}
     *   data: {"type":"done","content":{}}
     */
    @PostMapping(value = "/chat",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam String agentId,
            @RequestBody ChatRequest request) {

        Workspace workspace = multiAgentManager.getOrCreate(agentId);
        AgentRunner runner = workspace.getRunner();

        String chatId = request.getChatId() != null
                ? request.getChatId()
                : UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // Reconnect to existing stream if requested
        if (Boolean.TRUE.equals(request.getReconnect())) {
            // TODO: implement reconnect logic with stream store
            log.debug("Reconnect requested for chat: {}", chatId);
        }

        Map<String, String> context = Map.of(
                "session_id", request.getSessionId() != null ? request.getSessionId() : chatId,
                "user_id", request.getUserId() != null ? request.getUserId() : "default",
                "channel", "console",
                "agent_id", agentId
        );

        log.debug("Starting chat stream: agentId={}, chatId={}", agentId, chatId);
        return runner.streamChat(chatId, request.getMessage(), context);
    }

    // ------------------------------------------------------------------
    // POST /console/chat/stop
    // ------------------------------------------------------------------

    @PostMapping("/chat/stop")
    public Map<String, Object> stopChat(
            @RequestParam String agentId,
            @RequestParam String chatId) {

        Workspace workspace = multiAgentManager.getOrCreate(agentId);
        boolean stopped = workspace.getRunner().requestStop(chatId);
        return Map.of("stopped", stopped, "chat_id", chatId);
    }

    // ------------------------------------------------------------------
    // POST /console/upload
    // ------------------------------------------------------------------

    @PostMapping("/upload")
    public Map<String, Object> uploadFile(
            @RequestParam String agentId,
            @RequestParam("file") MultipartFile file) throws IOException {

        Workspace workspace = multiAgentManager.getOrCreate(agentId);
        Path mediaDir = workspace.getWorkspaceDir().resolve("media");
        Files.createDirectories(mediaDir);

        byte[] data = file.getBytes();
        if (data.length > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "File too large: " + data.length + " bytes (max 10 MB)");
        }

        String safeName = SAFE_FILENAME
                .matcher(Paths.get(file.getOriginalFilename() != null
                        ? file.getOriginalFilename() : "file").getFileName().toString())
                .replaceAll("_");
        if (safeName.isBlank()) safeName = "file";
        safeName = safeName.substring(0, Math.min(safeName.length(), 200));

        String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + safeName;
        Path storedPath = mediaDir.resolve(storedName);
        Files.write(storedPath, data);

        return Map.of(
                "url", storedPath.toString(),
                "file_name", safeName,
                "size", data.length
        );
    }

    // ------------------------------------------------------------------
    // GET /console/push-messages
    // ------------------------------------------------------------------

    @GetMapping("/push-messages")
    public Map<String, Object> getPushMessages(
            @RequestParam(required = false) String sessionId) {
        // TODO: wire to a push message store
        return Map.of("messages", java.util.Collections.emptyList());
    }

    // ------------------------------------------------------------------
    // Request / Response DTOs
    // ------------------------------------------------------------------

    @Data
    public static class ChatRequest {
        private String message;
        private String chatId;
        private String sessionId;
        private String userId;
        private Boolean reconnect;
        private String channel;
    }
}
