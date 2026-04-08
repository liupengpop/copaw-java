package io.copaw.workspace;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OllamaChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.model.exception.RateLimitException;
import io.agentscope.core.model.ollama.OllamaOptions;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.copaw.common.config.AgentProfileConfig;
import io.copaw.common.config.ModelSlotConfig;
import io.copaw.memory.MemoryManager;
import io.copaw.skills.SkillService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private static final List<String> DEFAULT_SYSTEM_PROMPT_FILES = List.of("AGENTS.md", "SOUL.md", "PROFILE.md");
    private static final Pattern YAML_FRONTMATTER_PATTERN = Pattern.compile("(?s)^---\\s*\\R.*?\\R---\\s*\\R?");
    private static final Pattern HEARTBEAT_SECTION_PATTERN = Pattern.compile("(?s)<!-- heartbeat:start -->.*?<!-- heartbeat:end -->");

    private final String agentId;
    private final Path workspaceDir;
    private final AgentProfileConfig config;
    private final MemoryManager memoryManager;
    private final McpClientManager mcpClientManager;
    private final SkillService skillService;
    private final WorkspaceTools workspaceTools;
    private final ChatSessionStore chatSessionStore;

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
                       SkillService skillService,
                       WorkspaceTools workspaceTools,
                       ChatSessionStore chatSessionStore) {
        this.agentId = agentId;
        this.workspaceDir = workspaceDir;
        this.config = config;
        this.memoryManager = memoryManager;
        this.mcpClientManager = mcpClientManager;
        this.skillService = skillService;
        this.workspaceTools = workspaceTools;
        this.chatSessionStore = chatSessionStore;
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

        String sessionId = firstNonBlank(context != null ? context.get("session_id") : null, chatId);
        String userId = firstNonBlank(context != null ? context.get("user_id") : null, "default");
        String channel = firstNonBlank(context != null ? context.get("channel") : null, "console");

        persistChatMessage(sessionId, userId, channel, "user", message);

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
     * Get all messages currently in memory for this agent (for UI session history display).
     * Filters out system messages; returns only user/assistant turns.
     */
    public List<io.copaw.memory.MemoryManager.Message> getMessages() {
        return memoryManager.getAll().stream()
                .filter(m -> "user".equals(m.role()) || "assistant".equals(m.role()))
                .collect(Collectors.toList());
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
            log.debug("Agent {} prompt window for chat {}: {}",
                    agentId, chatId, summarizePromptMessages(promptMessages));
            Msg responseMsg = agent.call(promptMessages).block(AGENT_CALL_TIMEOUT);

            if (stopFlag.get()) {
                sink.tryEmitNext(formatSseEvent("stop", "{}"));
                return;
            }
            if (responseMsg == null) {
                throw new IllegalStateException("Agent returned null response");
            }

            log.debug("Agent {} raw response for chat {}: role={}, name={}, text={}, blockTypes={}",
                    agentId,
                    chatId,
                    responseMsg.getRole(),
                    responseMsg.getName(),
                    abbreviateForLog(responseMsg.getTextContent(), 120),
                    describeContentBlocks(responseMsg));

            String response = normalizeAgentText(chatId, responseMsg);
            if (hasText(response)) {
                emitResponseChunks(response, sink, stopFlag);
            } else {
                // Empty response (e.g. thinking-only turn) — emit a placeholder so the
                // frontend doesn't spin waiting for content.
                log.debug("Agent {} produced empty text for chat {} — emitting placeholder", agentId, chatId);
                sink.tryEmitNext(formatSseEvent("delta", escapeJson("（Agent 思考完毕，无文本输出）")));
            }

            if (stopFlag.get()) {
                sink.tryEmitNext(formatSseEvent("stop", "{}"));
                return;
            }

            String finalResponse = hasText(response) ? response : "（Agent 思考完毕，无文本输出）";
            persistChatMessage(
                    firstNonBlank(context != null ? context.get("session_id") : null, chatId),
                    firstNonBlank(context != null ? context.get("user_id") : null, "default"),
                    firstNonBlank(context != null ? context.get("channel") : null, "console"),
                    "assistant",
                    finalResponse
            );
            if (hasText(response)) {
                memoryManager.addMessage(MemoryManager.Message.assistant(response));
            }
            sink.tryEmitNext(formatSseEvent("done", "{}"));
            log.debug("Agent {} finished chat {}", agentId, chatId);

        } catch (RateLimitException e) {
            // 429 from upstream — transient, no need for full stacktrace
            log.warn("Agent {} rate-limited in chat {} (provider: {}): {}",
                    agentId, chatId, extractProvider(e.getMessage()), e.getMessage());
            sink.tryEmitNext(formatSseEvent("error",
                    "{\"error\":" + escapeJson("模型调用被限速（429），请稍后重试") + "}"));
        } catch (Exception e) {
            log.error("Agent {} error in chat {}: {}", agentId, chatId, e.getMessage(), e);
            sink.tryEmitNext(formatSseEvent("error",
                    "{\"error\":" + escapeJson(e.getMessage()) + "}"));
        } finally {
            activeAgents.remove(chatId);
        }
    }

    /** Extract provider name from a rate-limit error message for cleaner logging. */
    private String extractProvider(String message) {
        if (message == null) return "unknown";
        // message contains "provider_name":"XYZ"
        int idx = message.indexOf("\"provider_name\":\"");
        if (idx < 0) return "unknown";
        int start = idx + 17;
        int end = message.indexOf('"', start);
        return end > start ? message.substring(start, end) : "unknown";
    }

    private ReActAgent buildAgent() {
        Model model = buildModel();
        Toolkit toolkit = new Toolkit();
        registerToolkitResources(toolkit);

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

    private void registerToolkitResources(Toolkit toolkit) {
        if (workspaceTools != null) {
            toolkit.registerTool(workspaceTools);
        }
        if (mcpClientManager == null) {
            return;
        }
        for (McpClientWrapper client : mcpClientManager.listClients()) {
            toolkit.registerMcpClient(client).block();
        }
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
                .stream(true)
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
                .stream(true)
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
        Integer normalizedMaxTokens = normalizeConfiguredMaxTokens(modelConfig.getMaxTokens());
        if (normalizedMaxTokens != null) {
            builder.maxTokens(normalizedMaxTokens);
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
        List<MemoryManager.Message> sanitizedWindow = memoryWindow.stream()
                .filter(this::shouldKeepInPrompt)
                .collect(Collectors.toList());
        List<MemoryManager.Message> promptWindow = sanitizedWindow.isEmpty() ? memoryWindow : sanitizedWindow;

        String systemPrompt = buildSystemPrompt();
        int initialCapacity = promptWindow.size() + (hasText(systemPrompt) ? 1 : 0);
        List<Msg> messages = new ArrayList<>(initialCapacity);
        if (hasText(systemPrompt)) {
            messages.add(Msg.builder()
                    .id(UUID.randomUUID().toString())
                    .role(MsgRole.SYSTEM)
                    .textContent(systemPrompt)
                    .build());
        }
        for (int i = 0; i < promptWindow.size(); i++) {
            MemoryManager.Message memoryMessage = promptWindow.get(i);
            boolean attachRuntimeContext = i == promptWindow.size() - 1;
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

    private String normalizeAgentText(String chatId, Msg responseMsg) {
        // 1. Best case: getTextContent() already extracts TextBlock text.
        //    But do not trust bare role labels like `用户` / `assistant` — those are invalid UI payloads.
        String text = responseMsg.getTextContent();
        if (hasText(text) && !looksLikeBareRoleLabel(text)) {
            return text;
        }
        if (looksLikeBareRoleLabel(text)) {
            log.warn("Agent {} produced suspicious role-label text for chat {}: role={}, text={}, blockTypes={}",
                    agentId,
                    chatId,
                    responseMsg.getRole(),
                    abbreviateForLog(text, 80),
                    describeContentBlocks(responseMsg));
        }

        // 2. Fallback: collect displayable content from non-tool, non-thinking blocks.
        //    ThinkingBlock = internal monologue, never show to users.
        //    ToolUseBlock  = tool invocation, not displayable text.
        String fallback = responseMsg.getContent().stream()
                .filter(block -> !(block instanceof ToolUseBlock))
                .filter(block -> !(block instanceof ThinkingBlock))
                .map(String::valueOf)
                .filter(this::hasText)
                .collect(Collectors.joining("\n"));
        if (hasText(fallback) && !looksLikeBareRoleLabel(fallback)) {
            log.debug("Agent {} resolved text from non-TextBlock content for chat {}: blockTypes={}",
                    agentId, chatId, describeContentBlocks(responseMsg));
            return fallback;
        }
        if (looksLikeBareRoleLabel(fallback)) {
            log.warn("Agent {} fallback content also collapsed to role label for chat {}: text={}, blockTypes={}",
                    agentId,
                    chatId,
                    abbreviateForLog(fallback, 80),
                    describeContentBlocks(responseMsg));
            return "";
        }

        // 3. Only thinking blocks and/or tool-use blocks — agent is mid-ReAct loop or thinking-only turn.
        //    This is normal for extended-thinking models; the caller (runAgentAndEmit) should just
        //    get an empty/stub response. Log at DEBUG to avoid noise.
        boolean hasOnlyThinkingOrTool = responseMsg.getContent().stream()
                .allMatch(block -> block instanceof ThinkingBlock || block instanceof ToolUseBlock);
        if (hasOnlyThinkingOrTool && !responseMsg.getContent().isEmpty()) {
            log.debug("Agent {} produced only thinking/tool blocks for chat {} (blockTypes={}); "
                    + "returning empty — agent likely completed via tool loop.",
                    agentId, chatId, describeContentBlocks(responseMsg));
            return "";
        }

        log.warn("Agent {} returned empty content for chat {}: blockTypes={}",
                agentId, chatId, describeContentBlocks(responseMsg));
        return "[Agent returned empty response]";
    }

    private String describeContentBlocks(Msg responseMsg) {
        return responseMsg.getContent().stream()
                .map(block -> block.getClass().getSimpleName())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private boolean shouldKeepInPrompt(MemoryManager.Message memoryMessage) {
        if (memoryMessage == null || !hasText(memoryMessage.content())) {
            return false;
        }
        String role = memoryMessage.role() != null
                ? memoryMessage.role().trim().toLowerCase(Locale.ROOT)
                : "";
        if (!"assistant".equals(role)) {
            return true;
        }
        String content = memoryMessage.content().trim();
        return !looksLikeBareRoleLabel(content)
                && !"[Agent returned empty response]".equals(content)
                && !"[Agent produced tool calls but no final text response]".equals(content)
                && !"（Agent 思考完毕，无文本输出）".equals(content);
    }

    private boolean looksLikeBareRoleLabel(String value) {
        if (!hasText(value)) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "user", "assistant", "system", "tool", "用户", "助手", "系统", "工具" -> true;
            default -> false;
        };
    }

    private String summarizePromptMessages(List<Msg> messages) {
        return messages.stream()
                .map(msg -> {
                    String text = abbreviateForLog(msg.getTextContent(), 40);
                    return msg.getRole() + ":" + (text != null ? text : "<empty>");
                })
                .collect(Collectors.joining(" | "));
    }

    private String abbreviateForLog(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\n", "\\n");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String buildSystemPrompt() {
        String basePrompt = buildWorkspaceSystemPrompt();
        if (!hasText(basePrompt) && hasText(config.getDescription())) {
            basePrompt = config.getDescription();
        }
        if (!hasText(basePrompt)) {
            basePrompt = "You are " + firstNonBlank(config.getName(), "CoPaw Agent")
                    + ". Be helpful, accurate, concise, and execution-oriented.";
        }
        String identityHeader = buildAgentIdentityHeader();
        if (hasText(identityHeader) && hasText(basePrompt)) {
            return identityHeader + "\n\n" + basePrompt;
        }
        return hasText(basePrompt) ? basePrompt : identityHeader;
    }

    private String buildAgentIdentityHeader() {
        return "# Agent Identity\n\n"
                + "Your agent id is `" + agentId + "`. "
                + "This is your unique identifier in the multi-agent system.";
    }

    private String buildWorkspaceSystemPrompt() {
        List<String> promptParts = new ArrayList<>();
        int loadedFiles = 0;
        for (String filename : DEFAULT_SYSTEM_PROMPT_FILES) {
            String content = readWorkspacePromptFile(filename);
            if (!hasText(content)) {
                continue;
            }
            if (!promptParts.isEmpty()) {
                promptParts.add("");
            }
            promptParts.add("# " + filename);
            promptParts.add("");
            promptParts.add(content);
            loadedFiles++;
        }
        if (promptParts.isEmpty()) {
            return "";
        }
        String prompt = String.join("\n\n", promptParts);
        log.debug("Agent {} built workspace system prompt from {} file(s) at {}",
                agentId, loadedFiles, workspaceDir);
        return prompt;
    }

    private String readWorkspacePromptFile(String filename) {
        Path filePath = workspaceDir.resolve(filename);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return "";
        }
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return sanitizeWorkspacePromptContent(filename, content);
        } catch (IOException e) {
            log.warn("Agent {} failed to read prompt file {}: {}", agentId, filePath, e.getMessage());
            return "";
        }
    }

    private String sanitizeWorkspacePromptContent(String filename, String rawContent) {
        if (!hasText(rawContent)) {
            return "";
        }
        String content = rawContent.strip();
        if (content.startsWith("---")) {
            content = YAML_FRONTMATTER_PATTERN.matcher(content).replaceFirst("").strip();
        }
        if ("AGENTS.md".equalsIgnoreCase(filename)) {
            boolean heartbeatEnabled = config.getHeartbeat() != null && config.getHeartbeat().isEnabled();
            if (heartbeatEnabled) {
                content = content.replace("<!-- heartbeat:start -->", "")
                        .replace("<!-- heartbeat:end -->", "")
                        .strip();
            } else {
                content = HEARTBEAT_SECTION_PATTERN.matcher(content).replaceAll("").strip();
            }
        }
        return content;
    }

    private Integer normalizeConfiguredMaxTokens(Integer maxTokens) {
        if (maxTokens == null) {
            return null;
        }
        if (maxTokens <= 1) {
            log.warn("Agent {} ignoring suspicious active_model.max_tokens={} and falling back to provider default",
                    agentId, maxTokens);
            return null;
        }
        return maxTokens;
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

    private void persistChatMessage(String sessionId, String userId, String channel, String role, String content) {
        if (chatSessionStore == null || !hasText(role)) {
            return;
        }
        String normalizedChannel = firstNonBlank(channel, "console");
        if (!hasText(normalizedChannel) || normalizedChannel.startsWith("_")) {
            return;
        }
        try {
            chatSessionStore.appendMessage(
                    firstNonBlank(sessionId, UUID.randomUUID().toString()),
                    firstNonBlank(userId, "default"),
                    normalizedChannel,
                    role,
                    content
            );
        } catch (Exception e) {
            log.warn("Persisting chat message failed for agent {} (session={}, channel={}): {}",
                    agentId, sessionId, normalizedChannel, e.getMessage());
        }
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
