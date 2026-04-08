package io.copaw.memory;

import java.util.List;
import java.util.Map;

/**
 * Base interface for all memory managers.
 * Maps to Python: BaseMemoryManager in agents/memory/base_memory_manager.py
 *
 * Implementations:
 * - ReMeLightMemoryManager (default)
 */
public interface MemoryManager {

    /** Add a message to memory */
    void addMessage(Message message);

    /**
     * Get the messages that fit within the context window.
     *
     * @param maxTokens token budget for the context window
     * @return list of messages that fit
     */
    List<Message> getContextWindow(int maxTokens);

    /**
     * Get ALL messages in memory (for UI display / session history).
     * Returns an unmodifiable snapshot.
     */
    List<Message> getAll();

    /**
     * Compact the context: summarize old messages and replace with summary.
     * Maps to Python: /compact command handling.
     *
     * @return the summary that was generated
     */
    String compact();

    /**
     * Generate a summary of the current conversation without modifying state.
     */
    String summarize();

    /**
     * Semantic search over memory.
     *
     * @param query  natural language query
     * @param topK   number of results to return
     * @return relevant messages
     */
    List<Message> search(String query, int topK);

    /** Clear all messages */
    void clear();

    /** Total number of messages in memory */
    int size();

    /** Estimated token count for all messages */
    int estimateTokenCount();

    /** Start the memory manager (e.g., load from disk) */
    void start();

    /** Stop the memory manager (e.g., flush to disk) */
    void stop();

    // -----------------------------------------------------------------------
    // Message record
    // -----------------------------------------------------------------------

    /** A single conversation message */
    record Message(
            String role,        // "system" | "user" | "assistant" | "tool"
            String content,
            String name,        // optional: tool name for role="tool"
            String toolCallId,  // optional: tool call ID
            Map<String, Object> metadata
    ) {
        public static Message system(String content) {
            return new Message("system", content, null, null, null);
        }
        public static Message user(String content) {
            return new Message("user", content, null, null, null);
        }
        public static Message assistant(String content) {
            return new Message("assistant", content, null, null, null);
        }
        public static Message tool(String name, String toolCallId, String content) {
            return new Message("tool", content, name, toolCallId, null);
        }
    }
}
