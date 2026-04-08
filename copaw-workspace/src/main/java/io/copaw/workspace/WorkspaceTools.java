package io.copaw.workspace;

import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.copaw.core.security.GuardFinding;
import io.copaw.core.security.ToolGuardEngine;
import io.copaw.core.security.ToolGuardResult;
import io.copaw.core.tools.BuiltinTool;
import io.copaw.core.tools.impl.EditFileTool;
import io.copaw.core.tools.impl.ExecuteShellCommandTool;
import io.copaw.core.tools.impl.GetCurrentTimeTool;
import io.copaw.core.tools.impl.GlobSearchTool;
import io.copaw.core.tools.impl.GrepSearchTool;
import io.copaw.core.tools.impl.ReadFileTool;
import io.copaw.core.tools.impl.WriteFileTool;
import io.copaw.memory.MemoryManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workspace-scoped tool façade registered into AgentScope Toolkit.
 */
public class WorkspaceTools {

    private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(5);

    private final String agentId;
    private final Path workspaceDir;
    private final MemoryManager memoryManager;
    private final ToolGuardEngine toolGuardEngine;
    private final ApprovalService approvalService;

    private final BuiltinTool readFileTool = new ReadFileTool();
    private final BuiltinTool writeFileTool = new WriteFileTool();
    private final BuiltinTool editFileTool = new EditFileTool();
    private final BuiltinTool executeShellCommandTool = new ExecuteShellCommandTool();
    private final BuiltinTool globSearchTool = new GlobSearchTool();
    private final BuiltinTool grepSearchTool = new GrepSearchTool();
    private final BuiltinTool getCurrentTimeTool = new GetCurrentTimeTool();

    public WorkspaceTools(String agentId,
                          Path workspaceDir,
                          MemoryManager memoryManager,
                          ToolGuardEngine toolGuardEngine,
                          ApprovalService approvalService) {
        this.agentId = agentId;
        this.workspaceDir = workspaceDir;
        this.memoryManager = memoryManager;
        this.toolGuardEngine = toolGuardEngine;
        this.approvalService = approvalService;
    }

    @Tool(name = "read_file", description = "Read file content from the workspace or an absolute path.")
    public String readFile(
            @ToolParam(name = "path", description = "File path") String path,
            @ToolParam(name = "encoding", description = "Optional file encoding, default utf-8") String encoding,
            Msg currentMessage) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", normalizePath(path));
        putIfHasText(params, "encoding", encoding);
        return executeBuiltin("read_file", params, readFileTool, currentMessage);
    }

    @Tool(name = "write_file", description = "Write content to a file under the workspace.")
    public String writeFile(
            @ToolParam(name = "path", description = "File path") String path,
            @ToolParam(name = "content", description = "Content to write") String content,
            @ToolParam(name = "encoding", description = "Optional file encoding, default utf-8") String encoding,
            Msg currentMessage) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", normalizePath(path));
        params.put("content", content != null ? content : "");
        putIfHasText(params, "encoding", encoding);
        return executeBuiltin("write_file", params, writeFileTool, currentMessage);
    }

    @Tool(name = "edit_file", description = "Replace exact text in a file.")
    public String editFile(
            @ToolParam(name = "path", description = "File path") String path,
            @ToolParam(name = "old_string", description = "Exact text to replace") String oldString,
            @ToolParam(name = "new_string", description = "Replacement text") String newString,
            @ToolParam(name = "count", description = "Optional replacement count") Integer count,
            Msg currentMessage) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", normalizePath(path));
        params.put("old_string", oldString != null ? oldString : "");
        params.put("new_string", newString != null ? newString : "");
        if (count != null) {
            params.put("count", count);
        }
        return executeBuiltin("edit_file", params, editFileTool, currentMessage);
    }

    @Tool(name = "execute_shell_command", description = "Execute a shell command. Commands run from the workspace by default and may require approval.")
    public String executeShellCommand(
            @ToolParam(name = "command", description = "Shell command to execute") String command,
            @ToolParam(name = "working_dir", description = "Optional working directory, default workspace") String workingDir,
            @ToolParam(name = "timeout", description = "Optional timeout seconds") Integer timeout,
            Msg currentMessage) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", command != null ? command : "");
        params.put("working_dir", normalizeDirectory(workingDir));
        if (timeout != null) {
            params.put("timeout", timeout);
        }
        return executeBuiltin("execute_shell_command", params, executeShellCommandTool, currentMessage);
    }

    @Tool(name = "glob_search", description = "Find files using a glob pattern.")
    public String globSearch(
            @ToolParam(name = "pattern", description = "Glob pattern such as **/*.java") String pattern,
            @ToolParam(name = "directory", description = "Optional search root directory") String directory,
            @ToolParam(name = "max_results", description = "Optional maximum result count") Integer maxResults,
            Msg currentMessage) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pattern", pattern != null ? pattern : "*");
        params.put("directory", normalizeDirectory(directory));
        if (maxResults != null) {
            params.put("max_results", maxResults);
        }
        return executeBuiltin("glob_search", params, globSearchTool, currentMessage);
    }

    @Tool(name = "grep_search", description = "Search file contents with a regex pattern.")
    public String grepSearch(
            @ToolParam(name = "pattern", description = "Regex pattern") String pattern,
            @ToolParam(name = "directory", description = "Optional search root directory") String directory,
            @ToolParam(name = "file_pattern", description = "Optional file glob such as *.java") String filePattern,
            @ToolParam(name = "case_sensitive", description = "Optional case sensitive flag") Boolean caseSensitive,
            @ToolParam(name = "context_lines", description = "Optional context lines per match") Integer contextLines,
            @ToolParam(name = "max_results", description = "Optional maximum result count") Integer maxResults,
            Msg currentMessage) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pattern", pattern != null ? pattern : "");
        params.put("directory", normalizeDirectory(directory));
        putIfHasText(params, "file_pattern", filePattern);
        if (caseSensitive != null) {
            params.put("case_sensitive", caseSensitive);
        }
        if (contextLines != null) {
            params.put("context_lines", contextLines);
        }
        if (maxResults != null) {
            params.put("max_results", maxResults);
        }
        return executeBuiltin("grep_search", params, grepSearchTool, currentMessage);
    }

    @Tool(name = "get_current_time", description = "Get the current date and time.")
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "Optional timezone") String timezone,
            @ToolParam(name = "format", description = "Optional date format") String format,
            Msg currentMessage) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        putIfHasText(params, "timezone", timezone);
        putIfHasText(params, "format", format);
        return executeBuiltin("get_current_time", params, getCurrentTimeTool, currentMessage);
    }

    @Tool(name = "memory_search", description = "Search relevant conversation memory using the workspace memory manager.")
    public String memorySearch(
            @ToolParam(name = "query", description = "Search query") String query,
            @ToolParam(name = "top_k", description = "Optional maximum number of results") Integer topK,
            Msg currentMessage) {
        int limit = topK != null && topK > 0 ? topK : 5;
        List<MemoryManager.Message> results = memoryManager.search(query != null ? query : "", limit);
        if (results.isEmpty()) {
            return "No relevant memory found for query: " + query;
        }

        StringBuilder sb = new StringBuilder("Relevant memory results (" + results.size() + "):\n");
        for (int i = 0; i < results.size(); i++) {
            MemoryManager.Message message = results.get(i);
            sb.append(i + 1)
                    .append(". [")
                    .append(message.role())
                    .append("] ")
                    .append(message.content())
                    .append('\n');
        }
        return sb.toString();
    }

    private String executeBuiltin(String toolName,
                                  Map<String, Object> params,
                                  BuiltinTool delegate,
                                  Msg currentMessage) throws Exception {
        RuntimeToolContext runtimeContext = buildRuntimeToolContext(currentMessage);
        ToolGuardResult guardResult = toolGuardEngine != null
                ? toolGuardEngine.guard(toolName, params)
                : null;

        if (guardResult != null && guardResult.isDenied()) {
            publishGuardEvent(runtimeContext, toolName, "tool_denied", guardResult);
            return formatGuardFailure("Tool call denied", toolName, guardResult);
        }

        if (guardResult != null && guardResult.requiresApproval()) {
            if (approvalService == null) {
                return formatGuardFailure("Tool call requires approval but approval service is unavailable", toolName, guardResult);
            }
            ApprovalService.PendingApprovalTicket ticket = approvalService.requestApproval(
                    runtimeContext,
                    toolName,
                    params,
                    guardResult,
                    APPROVAL_TIMEOUT
            );
            boolean approved = approvalService.awaitDecision(ticket, APPROVAL_TIMEOUT);
            if (!approved) {
                return formatGuardFailure("Tool call cancelled", toolName, guardResult);
            }
        }

        return delegate.execute(params);
    }

    private void publishGuardEvent(RuntimeToolContext context,
                                   String toolName,
                                   String eventType,
                                   ToolGuardResult guardResult) {
        if (approvalService == null || context == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_name", toolName);
        payload.put("message", formatGuardFailure("Tool call denied", toolName, guardResult));
        if (guardResult != null) {
            payload.put("denied", guardResult.isDenied());
            payload.put("requires_approval", guardResult.requiresApproval());
            payload.put("findings", guardResult.getFindings());
            payload.put("severity", guardResult.getMaxSeverity() != null
                    ? guardResult.getMaxSeverity().name()
                    : null);
        }
        approvalService.publishSessionMessage(context, eventType, payload);
    }

    private RuntimeToolContext buildRuntimeToolContext(Msg currentMessage) {
        Map<String, Object> metadata = currentMessage != null && currentMessage.getMetadata() != null
                ? currentMessage.getMetadata()
                : Map.of();
        String chatId = firstNonBlank(
                asString(metadata.get("chat_id")),
                currentMessage != null ? currentMessage.getId() : null,
                UUID.randomUUID().toString()
        );
        String sessionId = firstNonBlank(
                asString(metadata.get("session_id")),
                chatId,
                "_default"
        );
        String userId = firstNonBlank(asString(metadata.get("user_id")), "default");
        String channel = firstNonBlank(asString(metadata.get("channel")), "console");
        String effectiveAgentId = firstNonBlank(asString(metadata.get("agent_id")), agentId);
        return new RuntimeToolContext(
                effectiveAgentId,
                chatId,
                sessionId,
                userId,
                channel,
                workspaceDir
        );
    }

    private String formatGuardFailure(String prefix,
                                      String toolName,
                                      ToolGuardResult guardResult) {
        StringBuilder sb = new StringBuilder(prefix)
                .append(" for tool '")
                .append(toolName)
                .append("'.");
        if (guardResult != null && !guardResult.getFindings().isEmpty()) {
            sb.append(" Findings:");
            for (GuardFinding finding : guardResult.getFindings()) {
                sb.append(" [")
                        .append(finding.getSeverity())
                        .append("] ")
                        .append(finding.getDescription());
            }
        }
        return sb.toString();
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return workspaceDir.toString();
        }
        Path path = Paths.get(rawPath);
        if (!path.isAbsolute()) {
            path = workspaceDir.resolve(path);
        }
        return path.normalize().toString();
    }

    private String normalizeDirectory(String rawDirectory) {
        if (rawDirectory == null || rawDirectory.isBlank()) {
            return workspaceDir.toString();
        }
        return normalizePath(rawDirectory);
    }

    private void putIfHasText(Map<String, Object> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
