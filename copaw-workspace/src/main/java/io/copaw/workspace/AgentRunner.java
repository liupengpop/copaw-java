package io.copaw.workspace;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OllamaChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.model.ollama.OllamaOptions;
import io.agentscope.core.tool.Toolkit;
import io.copaw.common.config.AgentProfileConfig;
import io.copaw.common.config.ModelSlotConfig;
import io.copaw.memory.MemoryManager;
import io.copaw.skills.SkillService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent runner - processes incoming messages and returns streaming SSE responses.
 *
 * Maps to Python: AgentRunner in app/runner/runner.py
 *
 * Key responsibilities:
 * - Accept incoming chat requests
 * - Pass them to the CoPawAgent (ReActAgent)
 * - Stream the agent's response as SSE events
 * - Handle /compact, /new system commands
 * - Track active tasks (for hot reload safety)
 *
 * Each active chat session gets a Sinks.Many<String> that SSE clients subscribe to.
 */
@Slf4j
public class AgentRunner {

    private static final Duration AGENT_CALL_TIMEOUT = Duration.ofMinutes(5);
    private static final int STREAM_CHUNK_SIZE = 24;
    private static final String DEFAULT_DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";

    private final String agentId;
    private final Path workspaceDir;
    private final AgentProfileConfig config;
    private final MemoryManager memoryManager;
    private final McpClientManager mcpClientManager;
    private final SkillService skillService;

    /** active chat_id -> SSE sink */
    private final Map<String, Sinks.Many<String>> activeSinks = new ConcurrentHashMap<>();
    /** active chat_id -> stop flag */
    private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();
    /** active chat_id -> live AgentScope agent instance */
    private final Map<String, ReActAgent> activeAgents = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    public AgentRunner(String agentId, Path workspaceDir,
                       AgentProfileConfig config,
                       MemoryManager memoryManager,
                       McpClientManager mcpClientManager,
                       SkillService skillService) {
        this.agentId = agentId;
        this.workspaceDir = workspaceDir;
        this.config = config;
        this.memoryManager = memoryManager;
        this.mcpClientManager = mcpClientManager;
        this.skillService = skillService;
    }

    public void start() {
        running = true;
        log.info("AgentRunner started for agent: {}", agentId);
    }

    public void stop() {
        running = false;
        // Signal all active streams to stop
        stopFlags.values().forEach(flag -> flag.set(true));
        activeAgents.values().forEach(agent -> {
            try {
                agent.interrupt();
            } catch (Exception e) {
                log.debug("Interrupting active agent failed: {}", e.getMessage());
            }
        });
        // Complete all active sinks
        activeSinks.values().forEach(sink -> sink.tryEmitComplete());
        activeSinks.clear();
        stopFlags.clear();
        activeAgents.clear();
        log.info("AgentRunner stopped for agent: {}", agentId);
    }

    // ------------------------------------------------------------------
    // Chat streaming
    // ------------------------------------------------------------------

    /**
     * Process a chat message and return a reactive Flux of SSE event strings.
     * Each SSE event is a JSON string: {"type":"...", "content":"..."}
     *
     * This is the main integration point with AgentScope-Java's ReActAgent.
     *
     * Maps to Python: console_channel.stream_one() + TaskTracker in app/runner/
     *
     * @param chatId   unique chat session ID
     * @param message  user message text
     * @param context  metadata (session_id, user_id, channel)
     * @return Flux of SSE event JSON strings
     */
    public Flux<String> streamChat(String chatId, String message, Map<String, String> context) {
        if (!running) {
            return Flux.error(new IllegalStateException("AgentRunner is not running"));
        }

        if (message == null || message.isBlank()) {
            return Flux.just(
                    formatSseEvent("error", "{\"error\":\"empty message\"}"),
                    formatSseEvent("done", "{}")
            );
        }

        int maxInputLength = config.getRunning() != null
                ? config.getRunning().getMaxInputLength()
                : 50_000;
        if (message.length() > maxInputLength) {
            return Flux.just(
                    formatSseEvent("error", "{\"error\":\"input too long\"}"),
                    formatSseEvent("done", "{}")
            );
        }

        // Handle system commands before sending to agent
        if (message.trim().startsWith("/")) {
            return handleSystemCommand(chatId, message.trim());
        }

        // Create a sink for this chat session
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        activeSinks.put(chatId, sink);
        stopFlags.put(chatId, stopFlag);

        // Add user message to memory
        memoryManager.addMessage(MemoryManager.Message.user(message));

        // Run agent in worker thread (non-blocking from caller's perspective)
        Thread worker = new Thread(() -> {
            try {
                runAgentAndEmit(chatId, message, context, sink, stopFlag);
            } finally {
                activeAgents.remove(chatId);
                activeSinks.remove(chatId);
                stopFlags.remove(chatId);
                sink.tryEmitComplete();
            }
        }, "agent-" + agentId + "-" + chatId);
        worker.setDaemon(true);
        worker.start();

        return sink.asFlux()
                .doOnCancel(() -> requestStop(chatId))
                .doFinally(signal -> {
                    activeAgents.remove(chatId);
                    activeSinks.remove(chatId);
                    stopFlags.remove(chatId);
                });
    }

    /**
     * Request stopping an active chat stream.
     */
    public boolean requestStop(String chatId) {
        AtomicBoolean flag = stopFlags.get(chatId);
        if (flag == null) return false;
        flag.set(true);

        ReActAgent agent = activeAgents.get(chatId);
        if (agent != null) {
            try {
                agent.interrupt();
            } catch (Exception e) {
                log.debug("Interrupt request failed for chat {}: {}", chatId, e.getMessage());
            }
        }

        log.info("Stop requested for chat: {}", chatId);
        return true;
    }

    public boolean hasActiveTasks() {
        return !activeSinks.isEmpty();
    }

    public int getActiveTaskCount() {
        return activeSinks.size();
    }

    // ------------------------------------------------------------------
    // Agent execution
    // ------------------------------------------------------------------

    /**
     * Run the ReActAgent and emit SSE events to the sink.
     *
     * P0 implementation uses the stable synchronous call path first, then
     * slices the final text into SSE deltas. This avoids depending on less
     * certain streaming APIs while still executing a real AgentScope agent.
     */
    private void runAgentAndEmit(String chatId, String message,
                                  Map<String, String> context,
                                  Sinks.Many<String> sink,
                                  AtomicBoolean stopFlag) {
        ReActAgent agent = null;
        try {
            log.debug("Agent {} processing chat {}: {}", agentId, chatId,
                    message.substring(0, Math.min(50, message.length())));

            sink.tryEmitNext(formatSseEvent("start", "{\"chat_id\":\"" + chatId + "\"}"));
            if (stopFlag.get()) {
                sink.tryEmitNext(formatSseEvent("stop", "{}"));
                return;
            }

            agent = buildAgent();
            activeAgents.put(chatId, agent);

            List<Msg> promptMessages = buildPromptMessages(context);
            Msg responseMsg = agent.call(promptMessages).block(AGENT_CALL_TIMEOUT);

            if (stopFlag.get()) {
                sink.tryEmitNext(formatSseEvent("stop", "{}"));
                return;
            }
            if (responseMsg == null) {
                throw new IllegalStateException("Agent returned null response");
            }

            String response = normalizeAgentText(responseMsg);
            emitResponseChunks(response, sink, stopFlag);

            if (stopFlag.get()) {
                sink.tryEmitNext(formatSseEvent("stop", "{}"));
                return;
            }

            memoryManager.addMessage(MemoryManager.Message.assistant(response));
            sink.tryEmitNext(formatSseEvent("done", "{}"));
            log.debug("Agent {} finished chat {}", agentId, chatId);

        } catch (Exception e) {
            log.error("Agent {} error in chat {}: {}", agentId, chatId, e.getMessage(), e);
            sink.tryEmitNext(formatSseEvent("error",
                    "{\"error\":" + escapeJson(e.getMessage()) + "}"));
        } finally {
            activeAgents.remove(chatId);
        }
    }

    private ReActAgent buildAgent() {
        Model model = buildModel();
        Toolkit toolkit = new Toolkit();

        return ReActAgent.builder()
                .name(firstNonBlank(config.getName(), agentId))
                .description(firstNonBlank(config.getDescription(), "CoPaw Java agent"))
                .sysPrompt(buildSystemPrompt())
                .model(model)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .maxIters(config.getRunning() != null ? config.getRunning().getMaxIters() : 30)
                .build();
    }

    private Model buildModel() {
        ModelSlotConfig modelConfig = config.getActiveModel();
        if (modelConfig == null) {
            throw new IllegalStateException("No active_model configured for agent '" + agentId + "'");
        }

        String providerId = normalizeProviderId(modelConfig.getProviderId());
        return switch (providerId) {
            case "openai", "openai-compatible", "openai_compatible",
                    "dashscope", "deepseek", "moonshot", "kimi",
                    "openrouter", "siliconflow", "vllm" -> buildOpenAiCompatibleModel(modelConfig, providerId);
            case "anthropic" -> buildAnthropicModel(modelConfig);
            case "ollama" -> buildOllamaModel(modelConfig);
            default -> throw new IllegalArgumentException(
                    "Unsupported provider_id for P0 Java runtime: " + providerId);
        };
    }

    private Model buildOpenAiCompatibleModel(ModelSlotConfig modelConfig, String providerId) {
        GenerateOptions options = buildGenerateOptions(modelConfig);
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .modelName(requireModelName(modelConfig))
                .stream(false)
                .generateOptions(options);

        String apiKey = resolveApiKey(modelConfig, providerId);
        if (hasText(apiKey)) {
            builder.apiKey(apiKey);
        }

        String baseUrl = firstNonBlank(modelConfig.getBaseUrl(), defaultBaseUrlFor(providerId));
        if (hasText(baseUrl)) {
            builder.baseUrl(baseUrl);
        }

        return builder.build();
    }

    private Model buildAnthropicModel(ModelSlotConfig modelConfig) {
        AnthropicChatModel.Builder builder = AnthropicChatModel.builder()
                .modelName(requireModelName(modelConfig))
                .stream(false)
                .defaultOptions(buildGenerateOptions(modelConfig));

        String apiKey = resolveApiKey(modelConfig, "anthropic");
        if (hasText(apiKey)) {
            builder.apiKey(apiKey);
        }
        if (hasText(modelConfig.getBaseUrl())) {
            builder.baseUrl(modelConfig.getBaseUrl());
        }
        return builder.build();
    }

    private Model buildOllamaModel(ModelSlotConfig modelConfig) {
        GenerateOptions options = buildGenerateOptions(modelConfig);
        OllamaOptions ollamaOptions = OllamaOptions.fromGenerateOptions(options);
        ollamaOptions.setModel(requireModelName(modelConfig));

        OllamaChatModel.Builder builder = OllamaChatModel.builder()
                .modelName(requireModelName(modelConfig))
                .defaultOptions(ollamaOptions)
                .baseUrl(firstNonBlank(modelConfig.getBaseUrl(), defaultBaseUrlFor("ollama")));
        return builder.build();
    }

    private GenerateOptions buildGenerateOptions(ModelSlotConfig modelConfig) {
        GenerateOptions.Builder builder = GenerateOptions.builder()
                .modelName(requireModelName(modelConfig))
                .stream(Boolean.FALSE);

        if (modelConfig.getTemperature() != null) {
            builder.temperature(modelConfig.getTemperature());
        }
        if (modelConfig.getMaxTokens() != null) {
            builder.maxTokens(modelConfig.getMaxTokens());
        }
        if (hasText(modelConfig.getApiKey())) {
            builder.apiKey(modelConfig.getApiKey());
        }
        if (hasText(modelConfig.getBaseUrl())) {
            builder.baseUrl(modelConfig.getBaseUrl());
        }
        return builder.build();
    }

    private List<Msg> buildPromptMessages(Map<String, String> context) {
        int contextTokenLimit = config.getMemory() != null
                ? config.getMemory().getContextTokenLimit()
                : 8_000;

        List<MemoryManager.Message> memoryWindow = memoryManager.getContextWindow(contextTokenLimit);
        List<Msg> messages = new ArrayList<>(memoryWindow.size());
        for (int i = 0; i < memoryWindow.size(); i++) {
            MemoryManager.Message memoryMessage = memoryWindow.get(i);
            boolean attachRuntimeContext = i == memoryWindow.size() - 1;
            messages.add(toAgentMessage(memoryMessage, attachRuntimeContext ? context : null));
        }
        return messages;
    }

    private Msg toAgentMessage(MemoryManager.Message memoryMessage, Map<String, String> runtimeContext) {
        Msg.Builder builder = Msg.builder()
                .id(UUID.randomUUID().toString())
                .role(toMsgRole(memoryMessage.role()))
                .textContent(memoryMessage.content() != null ? memoryMessage.content() : "");

        if (hasText(memoryMessage.name())) {
            builder.name(memoryMessage.name());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (memoryMessage.metadata() != null && !memoryMessage.metadata().isEmpty()) {
            metadata.putAll(memoryMessage.metadata());
        }
        if (runtimeContext != null && !runtimeContext.isEmpty()) {
            metadata.putAll(runtimeContext);
        }
        if (!metadata.isEmpty()) {
            builder.metadata(metadata);
        }

        return builder.build();
    }

    private MsgRole toMsgRole(String role) {
        if (role == null) return MsgRole.USER;
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "assistant" -> MsgRole.ASSISTANT;
            case "system" -> MsgRole.SYSTEM;
            case "tool" -> MsgRole.TOOL;
            default -> MsgRole.USER;
        };
    }

    private void emitResponseChunks(String response,
                                    Sinks.Many<String> sink,
                                    AtomicBoolean stopFlag) throws InterruptedException {
        for (int start = 0; start < response.length(); start += STREAM_CHUNK_SIZE) {
            if (stopFlag.get()) {
                return;
            }
            int end = Math.min(response.length(), start + STREAM_CHUNK_SIZE);
            String chunk = response.substring(start, end);
            sink.tryEmitNext(formatSseEvent("delta", escapeJson(chunk)));
            Thread.sleep(5L);
        }
    }

    private String normalizeAgentText(Msg responseMsg) {
        String text = responseMsg.getTextContent();
        if (hasText(text)) {
            return text;
        }
        return "[Agent returned empty response]";
    }

    private String buildSystemPrompt() {
        if (hasText(config.getDescription())) {
            return config.getDescription();
        }
        return "You are " + firstNonBlank(config.getName(), "CoPaw Agent")
                + ". Be helpful, accurate, concise, and execution-oriented.";
    }

    private String normalizeProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return "openai";
        }
        return providerId.trim().toLowerCase(Locale.ROOT);
    }

    private String requireModelName(ModelSlotConfig modelConfig) {
        if (!hasText(modelConfig.getModel())) {
            throw new IllegalStateException("active_model.model is required for agent '" + agentId + "'");
        }
        return modelConfig.getModel();
    }

    private String resolveApiKey(ModelSlotConfig modelConfig, String providerId) {
        if (hasText(modelConfig.getApiKey())) {
            return modelConfig.getApiKey();
        }
        return switch (providerId) {
            case "anthropic" -> System.getenv("ANTHROPIC_API_KEY");
            case "dashscope" -> System.getenv("DASHSCOPE_API_KEY");
            case "deepseek" -> firstNonBlank(System.getenv("DEEPSEEK_API_KEY"), System.getenv("OPENAI_API_KEY"));
            case "moonshot", "kimi" -> firstNonBlank(System.getenv("MOONSHOT_API_KEY"), System.getenv("OPENAI_API_KEY"));
            default -> System.getenv("OPENAI_API_KEY");
        };
    }

    private String defaultBaseUrlFor(String providerId) {
        return switch (providerId) {
            case "dashscope" -> firstNonBlank(System.getenv("DASHSCOPE_BASE_URL"), DEFAULT_DASHSCOPE_BASE_URL);
            case "deepseek" -> System.getenv("DEEPSEEK_BASE_URL");
            case "moonshot", "kimi" -> System.getenv("MOONSHOT_BASE_URL");
            case "ollama" -> firstNonBlank(System.getenv("OLLAMA_BASE_URL"), DEFAULT_OLLAMA_BASE_URL);
            default -> null;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Flux<String> handleSystemCommand(String chatId, String command) {
        return Flux.create(emitter -> {
            try {
                String response;
                if ("/compact".equals(command)) {
                    String summary = memoryManager.compact();
                    response = "Memory compacted. Summary: " + summary;
                } else if ("/new".equals(command)) {
                    memoryManager.clear();
                    response = "Conversation cleared.";
                } else {
                    response = "Unknown command: " + command;
                }
                emitter.next(formatSseEvent("delta", escapeJson(response)));
                emitter.next(formatSseEvent("done", "{}"));
                emitter.complete();
            } catch (Exception e) {
                emitter.error(e);
            }
        });
    }

    /**
     * Format a server-sent event (SSE) data line.
     * Format: data: {"type":"<type>","content":<content>}\n\n
     */
    private String formatSseEvent(String type, String content) {
        return "data: {\"type\":\"" + type + "\",\"content\":" + content + "}\n\n";
    }

    private String escapeJson(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
