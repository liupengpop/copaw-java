package io.copaw.api.controller;

import io.copaw.common.config.AgentConfigLoader;
import io.copaw.common.config.AgentProfileConfig;
import io.copaw.common.config.AgentRunningConfig;
import io.copaw.common.config.BaseChannelConfig;
import io.copaw.common.config.CoPawRootConfig;
import io.copaw.common.config.HeartbeatConfig;
import io.copaw.workspace.MultiAgentManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Multi-agent management REST API.
 * Maps to Python: app/routers/agents.py
 *
 * Endpoints:
 *   GET    /agents                 - List all agents
 *   POST   /agents                 - Create a new agent
 *   PUT    /agents/{id}            - Update agent metadata
 *   DELETE /agents/{id}            - Delete an agent
 *   POST   /agents/{id}/reload     - Hot reload an agent
 *   GET    /agents/{id}/files      - List agent workspace directory entries
 *   GET    /agents/{id}/file       - Get file content by relative path
 *   POST   /agents/{id}/file       - Save file content by relative path
 *   DELETE /agents/{id}/file       - Delete file by relative path
 */
@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
@Slf4j
public class AgentsController {

    private static final Set<String> EDITABLE_FILE_EXTENSIONS = Set.of(
            ".md", ".txt", ".json", ".yaml", ".yml", ".toml"
    );

    private final MultiAgentManager multiAgentManager;
    private final CoPawRootConfig rootConfig;
    private final Path rootConfigPath;

    // ------------------------------------------------------------------
    // GET /agents - list
    // ------------------------------------------------------------------

    @GetMapping
    public Map<String, Object> listAgents() {
        List<Map<String, Object>> agents = new ArrayList<>();
        rootConfig.getAgents().getProfiles().forEach((id, ref) -> {
            agents.add(Map.of(
                    "id", id,
                    "name", ref.getName() != null ? ref.getName() : id,
                    "workspaceDir", ref.getWorkspaceDir(),
                    "enabled", ref.isEnabled(),
                    "loaded", multiAgentManager.isLoaded(id)
            ));
        });
        return Map.of("agents", agents, "total", agents.size());
    }

    // ------------------------------------------------------------------
    // POST /agents - create
    // ------------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createAgent(@RequestBody CreateAgentRequest req) throws IOException {
        String agentId = AgentConfigLoader.generateShortAgentId();

        String wsDir = req.getWorkspaceDir();
        if (wsDir == null || wsDir.isBlank()) {
            Path rootWs = rootConfigPath.getParent().resolve("agents").resolve(agentId);
            wsDir = rootWs.toAbsolutePath().toString();
        }

        Files.createDirectories(Paths.get(wsDir));

        AgentProfileConfig profile = new AgentProfileConfig();
        profile.setId(agentId);
        profile.setName(req.getName());
        profile.setDescription(req.getDescription() != null ? req.getDescription() : "");
        profile.setWorkspaceDir(wsDir);
        profile.setLanguage(req.getLanguage() != null ? req.getLanguage() : "en");

        AgentConfigLoader.save(Paths.get(wsDir), profile);

        CoPawRootConfig.AgentProfileRef ref = new CoPawRootConfig.AgentProfileRef();
        ref.setId(agentId);
        ref.setName(req.getName());
        ref.setWorkspaceDir(wsDir);
        ref.setEnabled(true);
        rootConfig.getAgents().getProfiles().put(agentId, ref);
        rootConfig.save(rootConfigPath);

        log.info("Created agent: {} ({})", agentId, req.getName());
        return Map.of("id", agentId, "name", req.getName(), "workspace_dir", wsDir);
    }

    // ------------------------------------------------------------------
    // DELETE /agents/{id}
    // ------------------------------------------------------------------

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteAgent(@PathVariable("id") String agentId,
                                           @RequestParam(name = "deleteFiles", defaultValue = "false") boolean deleteFiles)
            throws IOException {
        multiAgentManager.stopAgent(agentId);

        CoPawRootConfig.AgentProfileRef removed =
                rootConfig.getAgents().getProfiles().remove(agentId);
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId);
        }
        rootConfig.save(rootConfigPath);

        if (deleteFiles && removed.getWorkspaceDir() != null) {
            Path wsDir = Paths.get(removed.getWorkspaceDir());
            if (Files.exists(wsDir)) {
                deleteRecursively(wsDir);
                log.info("Deleted workspace directory for agent: {}", agentId);
            }
        }

        return Map.of("deleted", true, "id", agentId);
    }

    // ------------------------------------------------------------------
    // POST /agents/{id}/reload
    // ------------------------------------------------------------------

    @PostMapping("/{id}/reload")
    public Map<String, Object> reloadAgent(@PathVariable("id") String agentId) {
        boolean reloaded = multiAgentManager.reloadAgent(agentId);
        return Map.of("reloaded", reloaded, "id", agentId);
    }

    // ------------------------------------------------------------------
    // PUT /agents/{id} - update metadata
    // ------------------------------------------------------------------

    @PutMapping("/{id}")
    public Map<String, Object> updateAgent(@PathVariable("id") String agentId,
                                           @RequestBody UpdateAgentRequest req) throws IOException {
        CoPawRootConfig.AgentProfileRef ref =
                rootConfig.getAgents().getProfiles().get(agentId);
        if (ref == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId);
        }

        if (req.getName() != null) {
            ref.setName(req.getName());
        }
        if (req.getEnabled() != null) {
            ref.setEnabled(req.getEnabled());
        }
        rootConfig.save(rootConfigPath);

        Path wsDir = Paths.get(ref.getWorkspaceDir());
        if (Files.exists(wsDir)) {
            AgentProfileConfig profile = AgentConfigLoader.load(wsDir);
            if (req.getName() != null) {
                profile.setName(req.getName());
            }
            if (req.getDescription() != null) {
                profile.setDescription(req.getDescription());
            }
            AgentConfigLoader.save(wsDir, profile);
        }

        return Map.of("updated", true, "id", agentId);
    }

    // ------------------------------------------------------------------
    // File management within workspace
    // ------------------------------------------------------------------

    @GetMapping("/{id}/files")
    public Map<String, Object> listFiles(@PathVariable("id") String agentId,
                                         @RequestParam(name = "path", defaultValue = "") String directoryPath)
            throws IOException {
        Path workspaceDir = getWorkspaceDir(agentId);
        String normalizedRelativePath = normalizeRelativePath(directoryPath, true);
        Path targetDir = resolveWorkspacePath(workspaceDir, normalizedRelativePath, true);

        if (!Files.isDirectory(targetDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a directory: " + directoryPath);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir)) {
            for (Path entry : stream) {
                if (!shouldExposeEntry(entry)) {
                    continue;
                }
                entries.add(buildEntryPayload(workspaceDir, entry));
            }
        }

        entries.sort(Comparator
                .comparing((Map<String, Object> entry) -> !(Boolean) entry.get("isDirectory"))
                .thenComparing(entry -> String.valueOf(entry.get("name")), String.CASE_INSENSITIVE_ORDER));

        String parentPath = "";
        if (!normalizedRelativePath.isBlank()) {
            Path normalizedPath = Paths.get(normalizedRelativePath).normalize();
            Path parent = normalizedPath.getParent();
            parentPath = parent != null ? toForwardSlash(parent.toString()) : "";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("currentPath", normalizedRelativePath);
        response.put("parentPath", parentPath);
        response.put("entries", entries);
        response.put("files", entries);
        return response;
    }

    @GetMapping("/{id}/file")
    public Map<String, Object> getFileByPath(@PathVariable("id") String agentId,
                                             @RequestParam(name = "path") String filePath) throws IOException {
        return readWorkspaceFile(agentId, filePath);
    }

    @GetMapping("/{id}/files/{filename}")
    public Map<String, Object> getFile(@PathVariable("id") String agentId,
                                       @PathVariable("filename") String filename) throws IOException {
        return readWorkspaceFile(agentId, filename);
    }

    @PostMapping("/{id}/file")
    public Map<String, Object> saveFileByPath(@PathVariable("id") String agentId,
                                              @RequestBody WorkspaceFileWriteRequest body) throws IOException {
        if (body == null || body.getPath() == null || body.getPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
        }
        return writeWorkspaceFile(agentId, body.getPath(), body.getContent());
    }

    @PostMapping("/{id}/files/{filename}")
    public Map<String, Object> saveFile(@PathVariable("id") String agentId,
                                        @PathVariable("filename") String filename,
                                        @RequestBody Map<String, String> body) throws IOException {
        String content = body != null ? body.getOrDefault("content", "") : "";
        return writeWorkspaceFile(agentId, filename, content);
    }

    @DeleteMapping("/{id}/file")
    public Map<String, Object> deleteFileByPath(@PathVariable("id") String agentId,
                                                @RequestParam(name = "path") String filePath) throws IOException {
        return deleteWorkspaceFile(agentId, filePath);
    }

    @DeleteMapping("/{id}/files/{filename}")
    public Map<String, Object> deleteFile(@PathVariable("id") String agentId,
                                          @PathVariable("filename") String filename) throws IOException {
        return deleteWorkspaceFile(agentId, filename);
    }

    // ------------------------------------------------------------------
    // GET /agents/{id}/channels  - list channel configs
    // POST /agents/{id}/channels - update channel config
    // ------------------------------------------------------------------

    @GetMapping("/{id}/channels")
    public Map<String, Object> getChannels(@PathVariable("id") String agentId) throws IOException {
        Path wsDir = getWorkspaceDir(agentId);
        AgentProfileConfig profile = AgentConfigLoader.load(wsDir);
        List<Map<String, Object>> channels = new ArrayList<>();
        addChannelInfo(channels, "console",   profile.getConsole());
        addChannelInfo(channels, "telegram",  profile.getTelegram());
        addChannelInfo(channels, "dingtalk",  profile.getDingtalk());
        addChannelInfo(channels, "feishu",    profile.getFeishu());
        addChannelInfo(channels, "wecom",     profile.getWecom());
        addChannelInfo(channels, "discord",   profile.getDiscord());
        addChannelInfo(channels, "mqtt",      profile.getMqtt());
        addChannelInfo(channels, "onebot",    profile.getOnebot());
        return Map.of("channels", channels);
    }

    @PostMapping("/{id}/channels/{channel}/enable")
    public Map<String, Object> enableChannel(@PathVariable("id") String agentId,
                                             @PathVariable("channel") String channel) throws IOException {
        return setChannelEnabled(agentId, channel, true);
    }

    @PostMapping("/{id}/channels/{channel}/disable")
    public Map<String, Object> disableChannel(@PathVariable("id") String agentId,
                                              @PathVariable("channel") String channel) throws IOException {
        return setChannelEnabled(agentId, channel, false);
    }

    private Map<String, Object> setChannelEnabled(String agentId, String channel, boolean enabled) throws IOException {
        Path wsDir = getWorkspaceDir(agentId);
        AgentProfileConfig profile = AgentConfigLoader.load(wsDir);
        BaseChannelConfig cfg = getChannelConfig(profile, channel);
        if (cfg == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found: " + channel);
        }
        cfg.setEnabled(enabled);
        setChannelConfig(profile, channel, cfg);
        AgentConfigLoader.save(wsDir, profile);
        if (multiAgentManager.isLoaded(agentId)) {
            multiAgentManager.reloadAgent(agentId);
        }
        return Map.of("channel", channel, "enabled", enabled);
    }

    // ------------------------------------------------------------------
    // GET /agents/{id}/heartbeat - get heartbeat config
    // POST /agents/{id}/heartbeat - save heartbeat config
    // ------------------------------------------------------------------

    @GetMapping("/{id}/heartbeat")
    public HeartbeatConfig getHeartbeat(@PathVariable("id") String agentId) throws IOException {
        Path wsDir = getWorkspaceDir(agentId);
        AgentProfileConfig profile = AgentConfigLoader.load(wsDir);
        HeartbeatConfig cfg = profile.getHeartbeat();
        return cfg != null ? cfg : new HeartbeatConfig();
    }

    @PostMapping("/{id}/heartbeat")
    public Map<String, Object> saveHeartbeat(@PathVariable("id") String agentId,
                                             @RequestBody HeartbeatConfig req) throws IOException {
        Path wsDir = getWorkspaceDir(agentId);
        AgentProfileConfig profile = AgentConfigLoader.load(wsDir);
        profile.setHeartbeat(req);
        AgentConfigLoader.save(wsDir, profile);
        if (multiAgentManager.isLoaded(agentId)) {
            multiAgentManager.reloadAgent(agentId);
        }
        return Map.of("saved", true);
    }

    // ------------------------------------------------------------------
    // GET /agents/{id}/runtime  - get running config
    // POST /agents/{id}/runtime - save running config
    // ------------------------------------------------------------------

    @GetMapping("/{id}/runtime")
    public AgentRunningConfig getRuntime(@PathVariable("id") String agentId) throws IOException {
        Path wsDir = getWorkspaceDir(agentId);
        AgentProfileConfig profile = AgentConfigLoader.load(wsDir);
        AgentRunningConfig cfg = profile.getRunning();
        return cfg != null ? cfg : new AgentRunningConfig();
    }

    @PostMapping("/{id}/runtime")
    public Map<String, Object> saveRuntime(@PathVariable("id") String agentId,
                                           @RequestBody AgentRunningConfig req) throws IOException {
        Path wsDir = getWorkspaceDir(agentId);
        AgentProfileConfig profile = AgentConfigLoader.load(wsDir);
        profile.setRunning(req);
        AgentConfigLoader.save(wsDir, profile);
        if (multiAgentManager.isLoaded(agentId)) {
            multiAgentManager.reloadAgent(agentId);
        }
        return Map.of("saved", true);
    }

    // ------------------------------------------------------------------
    // Channel helper methods
    // ------------------------------------------------------------------

    private void addChannelInfo(List<Map<String, Object>> list, String name, BaseChannelConfig cfg) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("enabled", cfg != null && cfg.isEnabled());
        entry.put("configured", cfg != null);
        list.add(entry);
    }

    private BaseChannelConfig getChannelConfig(AgentProfileConfig profile, String channel) {
        switch (channel.toLowerCase(Locale.ROOT)) {
            case "console":  return profile.getConsole();
            case "telegram": return profile.getTelegram();
            case "dingtalk": return profile.getDingtalk();
            case "feishu":   return profile.getFeishu();
            case "wecom":    return profile.getWecom();
            case "discord":  return profile.getDiscord();
            case "mqtt":     return profile.getMqtt();
            case "onebot":   return profile.getOnebot();
            default:         return null;
        }
    }

    private void setChannelConfig(AgentProfileConfig profile, String channel, BaseChannelConfig cfg) {
        // All channel types share enabled flag through BaseChannelConfig; cast is safe here
        // because we retrieved the same reference from getChannelConfig and mutated it in place.
        // No assignment needed – the mutation already happened on the live object.
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Map<String, Object> readWorkspaceFile(String agentId, String relativePath) throws IOException {
        Path workspaceDir = getWorkspaceDir(agentId);
        String normalizedRelativePath = normalizeRelativePath(relativePath, false);
        Path filePath = resolveWorkspacePath(workspaceDir, normalizedRelativePath, true);

        if (Files.isDirectory(filePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path is a directory: " + normalizedRelativePath);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("path", normalizedRelativePath);
        response.put("filename", filePath.getFileName().toString());
        response.put("content", Files.readString(filePath));
        return response;
    }

    private Map<String, Object> writeWorkspaceFile(String agentId,
                                                   String relativePath,
                                                   String content) throws IOException {
        Path workspaceDir = getWorkspaceDir(agentId);
        String normalizedRelativePath = normalizeRelativePath(relativePath, false);
        Path filePath = resolveWorkspacePath(workspaceDir, normalizedRelativePath, false);

        if (Files.exists(filePath) && Files.isDirectory(filePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path is a directory: " + normalizedRelativePath);
        }

        Path parent = filePath.getParent();
        if (parent != null) {
            assertExistingAncestorWithinWorkspace(workspaceDir, parent, normalizedRelativePath);
            Files.createDirectories(parent);
        }

        Files.writeString(
                filePath,
                content != null ? content : "",
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );

        return Map.of(
                "saved", true,
                "filename", filePath.getFileName().toString(),
                "path", normalizedRelativePath
        );
    }

    private Map<String, Object> deleteWorkspaceFile(String agentId, String relativePath) throws IOException {
        Path workspaceDir = getWorkspaceDir(agentId);
        String normalizedRelativePath = normalizeRelativePath(relativePath, false);
        Path filePath = resolveWorkspacePath(workspaceDir, normalizedRelativePath, true);

        if (Files.isDirectory(filePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Directory deletion is not supported: " + normalizedRelativePath);
        }

        Files.delete(filePath);
        return Map.of(
                "deleted", true,
                "filename", filePath.getFileName().toString(),
                "path", normalizedRelativePath
        );
    }

    private Path getWorkspaceDir(String agentId) {
        CoPawRootConfig.AgentProfileRef ref = rootConfig.getAgents().getProfiles().get(agentId);
        if (ref == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId);
        }
        return Paths.get(ref.getWorkspaceDir()).toAbsolutePath().normalize();
    }

    private Path resolveWorkspacePath(Path workspaceDir,
                                      String normalizedRelativePath,
                                      boolean mustExist) throws IOException {
        Path candidate = normalizedRelativePath.isBlank()
                ? workspaceDir
                : workspaceDir.resolve(normalizedRelativePath).normalize();

        if (!candidate.startsWith(workspaceDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path escapes workspace: " + normalizedRelativePath);
        }

        if (mustExist && !Files.exists(candidate)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Path not found: " + normalizedRelativePath);
        }

        if (Files.exists(candidate)) {
            Path workspaceRealPath = workspaceDir.toRealPath();
            Path candidateRealPath = candidate.toRealPath();
            if (!candidateRealPath.startsWith(workspaceRealPath)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolved path escapes workspace: " + normalizedRelativePath);
            }
        }

        return candidate;
    }

    private String normalizeRelativePath(String rawPath, boolean allowBlank) {
        String value = rawPath != null ? rawPath.trim() : "";
        if (value.isBlank()) {
            if (allowBlank) {
                return "";
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
        }

        value = value.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.isBlank()) {
            if (allowBlank) {
                return "";
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is required");
        }

        if (value.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid path: " + rawPath);
        }

        Path normalizedPath = Paths.get(value).normalize();
        if (normalizedPath.isAbsolute()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Absolute path is not allowed: " + rawPath);
        }

        String normalized = toForwardSlash(normalizedPath.toString());
        if (normalized.equals(".") || normalized.isBlank()) {
            return allowBlank ? "" : normalized;
        }
        return normalized;
    }

    private boolean shouldExposeEntry(Path entry) throws IOException {
        String name = entry.getFileName().toString();
        if (name.startsWith(".")) {
            return false;
        }
        if (Files.isHidden(entry)) {
            return false;
        }
        if (Files.isDirectory(entry)) {
            return true;
        }
        return isEditableFile(name);
    }

    private boolean isEditableFile(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String extension = filename.substring(dotIndex).toLowerCase(Locale.ROOT);
        return EDITABLE_FILE_EXTENSIONS.contains(extension);
    }

    private void assertExistingAncestorWithinWorkspace(Path workspaceDir,
                                                       Path candidatePath,
                                                       String normalizedRelativePath) throws IOException {
        Path existing = candidatePath;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }

        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path has no valid ancestor: " + normalizedRelativePath);
        }

        Path workspaceRealPath = workspaceDir.toRealPath();
        Path existingRealPath = existing.toRealPath();
        if (!existingRealPath.startsWith(workspaceRealPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolved path escapes workspace: " + normalizedRelativePath);
        }
    }

    private Map<String, Object> buildEntryPayload(Path workspaceDir, Path entry) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
        boolean isDirectory = attrs.isDirectory();
        String relativePath = toForwardSlash(workspaceDir.relativize(entry).toString());
        String fileName = entry.getFileName().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", fileName);
        payload.put("filename", fileName);
        payload.put("path", relativePath);
        payload.put("isDirectory", isDirectory);
        payload.put("editable", isDirectory || isEditableFile(fileName));
        payload.put("size", isDirectory ? 0L : attrs.size());
        payload.put("modified", attrs.lastModifiedTime().toInstant().toString());
        return payload;
    }

    private String toForwardSlash(String path) {
        return path.replace('\\', '/');
    }

    private void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // DTOs
    @Data
    public static class CreateAgentRequest {
        private String name;
        private String description;
        private String workspaceDir;
        private String language;
    }

    @Data
    public static class UpdateAgentRequest {
        private String name;
        private String description;
        private Boolean enabled;
    }

    @Data
    public static class WorkspaceFileWriteRequest {
        private String path;
        private String content;
    }
}
