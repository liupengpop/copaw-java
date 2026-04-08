package io.copaw.workspace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory push message store for console channel side-band events.
 * Used for tool approval prompts, denials, and other out-of-band notifications.
 */
public class ConsolePushMessageStore {

    private final Map<String, Deque<PushMessage>> messagesBySession = new ConcurrentHashMap<>();
    private final int maxMessagesPerSession;

    public ConsolePushMessageStore() {
        this(200);
    }

    public ConsolePushMessageStore(int maxMessagesPerSession) {
        this.maxMessagesPerSession = Math.max(20, maxMessagesPerSession);
    }

    public void publish(String sessionId, String chatId, String type, Map<String, Object> payload) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        Deque<PushMessage> queue = messagesBySession.computeIfAbsent(
                normalizedSessionId, ignored -> new ConcurrentLinkedDeque<>());
        queue.addLast(new PushMessage(
                UUID.randomUUID().toString(),
                normalizedSessionId,
                chatId,
                type,
                Instant.now(),
                payload != null ? payload : Map.of()
        ));
        while (queue.size() > maxMessagesPerSession) {
            queue.pollFirst();
        }
    }

    public List<PushMessage> drain(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            List<PushMessage> drained = new ArrayList<>();
            for (String existingSessionId : List.copyOf(messagesBySession.keySet())) {
                drained.addAll(drain(existingSessionId));
            }
            drained.sort(Comparator.comparing(PushMessage::createdAt));
            return drained;
        }

        String normalizedSessionId = normalizeSessionId(sessionId);
        Deque<PushMessage> queue = messagesBySession.get(normalizedSessionId);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }

        List<PushMessage> drained = new ArrayList<>();
        PushMessage message;
        while ((message = queue.pollFirst()) != null) {
            drained.add(message);
        }
        if (queue.isEmpty()) {
            messagesBySession.remove(normalizedSessionId, queue);
        }
        return drained;
    }

    public int pendingCount(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return messagesBySession.values().stream().mapToInt(Deque::size).sum();
        }
        Deque<PushMessage> queue = messagesBySession.get(normalizeSessionId(sessionId));
        return queue != null ? queue.size() : 0;
    }

    private String normalizeSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? "_default" : sessionId;
    }

    public record PushMessage(
            String id,
            String sessionId,
            String chatId,
            String type,
            Instant createdAt,
            Map<String, Object> payload
    ) {}
}
