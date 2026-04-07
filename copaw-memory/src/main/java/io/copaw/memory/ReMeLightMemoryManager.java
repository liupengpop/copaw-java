package io.copaw.memory;

import io.copaw.common.config.MemoryConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * ReMeLight memory manager implementation.
 * Provides sliding context window with automatic compaction.
 *
 * Maps to Python: ReMeLightMemoryManager in agents/memory/reme_light_memory_manager.py
 *
 * Key behaviors:
 * 1. Messages are stored in an ordered list
 * 2. When token count exceeds compactThreshold * contextTokenLimit, compaction is triggered
 * 3. Compaction summarizes the oldest N messages and replaces them with a system summary message
 * 4. getContextWindow() returns the most recent messages that fit within maxTokens
 */
@Slf4j
public class ReMeLightMemoryManager implements MemoryManager {

    private final MemoryConfig config;
    private final Function<List<Message>, String> summarizer; // LLM summarization function
    private final List<Message> messages = new CopyOnWriteArrayList<>();

    /**
     * @param config     memory configuration
     * @param summarizer function that takes a list of messages and returns a summary string
     *                   (calls the LLM); use a no-op lambda if summarization is not needed
     */
    public ReMeLightMemoryManager(MemoryConfig config,
                                   Function<List<Message>, String> summarizer) {
        this.config = config;
        this.summarizer = summarizer;
    }

    @Override
    public void addMessage(Message message) {
        messages.add(message);
        autoCompactIfNeeded();
    }

    @Override
    public List<Message> getContextWindow(int maxTokens) {
        if (messages.isEmpty()) return Collections.emptyList();

        // Walk backward from newest, accumulate until token budget exceeded
        List<Message> window = new ArrayList<>();
        int tokens = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            int msgTokens = estimateTokens(msg.content());
            if (tokens + msgTokens > maxTokens && !window.isEmpty()) break;
            window.add(0, msg);
            tokens += msgTokens;
        }
        return Collections.unmodifiableList(window);
    }

    @Override
    public String compact() {
        if (messages.size() < 3) return "";

        // Find messages to summarize (all but the last 2)
        int keepTail = Math.min(2, messages.size());
        List<Message> toSummarize = new ArrayList<>(messages.subList(0, messages.size() - keepTail));
        List<Message> toKeep = new ArrayList<>(messages.subList(messages.size() - keepTail, messages.size()));

        log.info("Compacting {} messages into summary...", toSummarize.size());
        String summary;
        try {
            summary = summarizer.apply(toSummarize);
        } catch (Exception e) {
            log.warn("Summarization failed during compact: {}", e.getMessage());
            summary = "[Memory compacted - " + toSummarize.size() + " messages summarized]";
        }

        messages.clear();
        messages.add(Message.system("## Conversation Summary\n" + summary));
        messages.addAll(toKeep);

        log.info("Memory compacted: {} messages → 1 summary + {} recent messages",
                toSummarize.size(), toKeep.size());
        return summary;
    }

    @Override
    public String summarize() {
        if (messages.isEmpty()) return "";
        try {
            return summarizer.apply(new ArrayList<>(messages));
        } catch (Exception e) {
            log.warn("Summarization failed: {}", e.getMessage());
            return "[Summarization unavailable]";
        }
    }

    @Override
    public List<Message> search(String query, int topK) {
        // Simple keyword search as fallback (no vector store)
        String lowerQuery = query.toLowerCase();
        return messages.stream()
                .filter(m -> m.content() != null &&
                        m.content().toLowerCase().contains(lowerQuery))
                .limit(topK)
                .toList();
    }

    @Override
    public void clear() {
        messages.clear();
        log.debug("Memory cleared");
    }

    @Override
    public int size() {
        return messages.size();
    }

    @Override
    public int estimateTokenCount() {
        return messages.stream()
                .mapToInt(m -> estimateTokens(m.content()))
                .sum();
    }

    @Override
    public void start() {
        log.debug("ReMeLightMemoryManager started");
    }

    @Override
    public void stop() {
        log.debug("ReMeLightMemoryManager stopped");
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private void autoCompactIfNeeded() {
        if (!config.isAutoCompact()) return;
        int limit = config.getContextTokenLimit();
        int threshold = (int) (limit * config.getCompactThreshold());
        if (estimateTokenCount() > threshold) {
            log.info("Auto-compacting memory: token count exceeded threshold ({}/{})",
                    estimateTokenCount(), threshold);
            compact();
        }
    }

    /**
     * Very rough token estimation: ~4 characters per token (GPT average).
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        return Math.max(1, text.length() / 4);
    }
}
