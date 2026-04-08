package io.copaw.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.copaw.common.json.CoPawObjectMapperFactory;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistent chat/session registry for UI session management.
 *
 * Stores session metadata + message history in <workspace>/chats/sessions.json.
 * This is intentionally simple and file-based, mirroring the Python side's
 * chat registry idea closely enough for the current P1 console/frontend needs.
 */
@Slf4j
public class ChatSessionStore {

    private static final String CHATS_DIR = "chats";
    private static final String SESSIONS_FILE = "sessions.json";

    private final Path storePath;
    private final ObjectMapper mapper;

    public ChatSessionStore(Path workspaceDir) {
        this.storePath = workspaceDir.resolve(CHATS_DIR).resolve(SESSIONS_FILE);
        this.mapper = CoPawObjectMapperFactory.create();
    }

    public synchronized List<ChatSpec> listChats(String userId, String channel) throws IOException {
        return load().getChats().stream()
                .map(ChatRecord::getSpec)
                .filter(spec -> userId == null || userId.isBlank() || Objects.equals(spec.getUserId(), userId))
                .filter(spec -> channel == null || channel.isBlank() || Objects.equals(spec.getChannel(), channel))
                .sorted(Comparator.comparing(ChatSpec::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public synchronized List<ChatMessage> getMessages(String chatId) throws IOException {
        if (chatId == null || chatId.isBlank()) {
            return List.of();
        }
        return load().getChats().stream()
                .filter(record -> chatId.equals(record.getSpec().getId()))
                .findFirst()
                .map(record -> List.copyOf(record.getMessages()))
                .orElseGet(List::of);
    }

    public synchronized ChatSpec appendMessage(
            String sessionId,
            String userId,
            String channel,
            String role,
            String content
    ) throws IOException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }

        ChatsFile file = load();
        ChatRecord record = findOrCreateRecord(file, sessionId, userId, channel, inferChatName(content));

        Instant now = Instant.now();
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content != null ? content : "");
        message.setCreatedAt(now);
        record.getMessages().add(message);

        ChatSpec spec = record.getSpec();
        if (isDefaultName(spec.getName()) && "user".equals(role) && hasText(content)) {
            spec.setName(inferChatName(content));
        }
        spec.setUpdatedAt(now);
        spec.setLastMessagePreview(preview(content));
        spec.setMessageCount(record.getMessages().size());

        save(file);
        return spec;
    }

    private ChatRecord findOrCreateRecord(
            ChatsFile file,
            String sessionId,
            String userId,
            String channel,
            String defaultName
    ) {
        for (ChatRecord record : file.getChats()) {
            ChatSpec spec = record.getSpec();
            if (Objects.equals(spec.getSessionId(), sessionId)
                    && Objects.equals(spec.getUserId(), userId)
                    && Objects.equals(spec.getChannel(), channel)) {
                return record;
            }
        }

        Instant now = Instant.now();
        ChatSpec spec = new ChatSpec();
        spec.setId(UUID.randomUUID().toString());
        spec.setName(hasText(defaultName) ? defaultName : "New Chat");
        spec.setSessionId(sessionId);
        spec.setUserId(userId);
        spec.setChannel(channel);
        spec.setCreatedAt(now);
        spec.setUpdatedAt(now);
        spec.setLastMessagePreview("");
        spec.setMessageCount(0);

        ChatRecord record = new ChatRecord();
        record.setSpec(spec);
        record.setMessages(new ArrayList<>());
        file.getChats().add(record);
        return record;
    }

    private ChatsFile load() throws IOException {
        if (!Files.exists(storePath)) {
            return new ChatsFile();
        }
        return mapper.readValue(storePath.toFile(), ChatsFile.class);
    }

    private void save(ChatsFile file) throws IOException {
        Files.createDirectories(storePath.getParent());
        Path tmp = storePath.resolveSibling(SESSIONS_FILE + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), file);
        Files.move(tmp, storePath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private String preview(String content) {
        if (!hasText(content)) {
            return "";
        }
        String normalized = content.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 117) + "...";
    }

    private String inferChatName(String content) {
        if (!hasText(content)) {
            return "New Chat";
        }
        String normalized = content.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= 24) {
            return normalized;
        }
        return normalized.substring(0, 24);
    }

    private boolean isDefaultName(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || "new chat".equals(normalized);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Data
    @NoArgsConstructor
    public static class ChatsFile {
        private int version = 1;
        private List<ChatRecord> chats = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class ChatRecord {
        private ChatSpec spec = new ChatSpec();
        private List<ChatMessage> messages = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class ChatSpec {
        private String id;
        private String name = "New Chat";
        private String sessionId;
        private String userId;
        private String channel = "console";
        private Instant createdAt;
        private Instant updatedAt;
        private String lastMessagePreview = "";
        private int messageCount = 0;
    }

    @Data
    @NoArgsConstructor
    public static class ChatMessage {
        private String role;
        private String content;
        private Instant createdAt;
    }
}
