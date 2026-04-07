package io.copaw.api.controller;

import io.copaw.common.config.*;
import io.copaw.workspace.MultiAgentManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;

/**
 * Multi-agent management REST API.
 * Maps to Python: app/routers/agents.py
 *
 * Endpoints:
 *   GET    /agents            - List all agents
 *   POST   /agents            - Create a new agent
 *   PUT    /agents/{id}       - Update agent metadata
 *   DELETE /agents/{id}       - Delete an agent
 *   POST   /agents/{id}/reload - Hot reload an agent
 *   GET    /agents/{id}/files  - List agent workspace files
 *   GET    /agents/{id}/files/{name} - Get file content
 *   POST   /agents/{id}/files/{name} - Save file content
 *   DELETE /agents/{id}/files/{name} - Delete file
 */
@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
@Slf4j
public class AgentsController {

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

        // Determine workspace directory
        String wsDir = req.getWorkspaceDir();
        if (wsDir == null || wsDir.isBlank()) {
            // Default: <root_workspace>/agents/<agentId>
            Path rootWs = rootConfigPath.getParent().resolve("agents").resolve(agentId);
            wsDir = rootWs.toAbsolutePath().toString();
        }

        // Create workspace directory
        Files.createDirectories(Paths.get(wsDir));

        // Build and save agent profile
        AgentProfileConfig profile = new AgentProfileConfig();
        profile.setId(agentId);
        profile.setName(req.getName());
        profile.setDescription(req.getDescription() != null ? req.getDescription() : "");
        profile.setWorkspaceDir(wsDir);
        profile.setLanguage(req.getLanguage() != null ? req.getLanguage() : "en");

        AgentConfigLoader.save(Paths.get(wsDir), profile);

        // Register in root config
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
                                            @RequestParam(defaultValue = "false") boolean deleteFiles)
            throws IOException {
        // Stop if running
        multiAgentManager.stopAgent(agentId);

        // Remove from root config
        CoPawRootConfig.AgentProfileRef removed =
                rootConfig.getAgents().getProfiles().remove(agentId);
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId);
        }
        rootConfig.save(rootConfigPath);

        // Optionally delete workspace directory
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

        if (req.getName() != null) ref.setName(req.getName());
        if (req.getEnabled() != null) ref.setEnabled(req.getEnabled());
        rootConfig.save(rootConfigPath);

        // Also update agent.json if workspace exists
        Path wsDir = Paths.get(ref.getWorkspaceDir());
        if (Files.exists(wsDir)) {
            AgentProfileConfig profile = AgentConfigLoader.load(wsDir);
            if (req.getName() != null) profile.setName(req.getName());
            if (req.getDescription() != null) profile.setDescription(req.getDescription());
            AgentConfigLoader.save(wsDir, profile);
        }

        return Map.of("updated", true, "id", agentId);
    }

    // ------------------------------------------------------------------
    // File management within workspace
    // ------------------------------------------------------------------

    @GetMapping("/{id}/files")
    public Map<String, Object> listFiles(@PathVariable("id") String agentId) {
        CoPawRootConfig.AgentProfileRef ref =
                rootConfig.getAgents().getProfiles().get(agentId);
        if (ref == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found");

        Path wsDir = Paths.get(ref.getWorkspaceDir());
        List<Map<String, Object>> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(wsDir, "*.md")) {
            for (Path p : stream) {
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                files.add(Map.of(
                        "filename", p.getFileName().toString(),
                        "path", p.toString(),
                        "size", attrs.size(),
                        "modified", attrs.lastModifiedTime().toInstant().toString()
                ));
            }
        } catch (IOException e) {
            log.warn("Failed to list files for agent {}: {}", agentId, e.getMessage());
        }
        return Map.of("files", files);
    }

    @GetMapping("/{id}/files/{filename}")
    public Map<String, Object> getFile(@PathVariable("id") String agentId,
                                        @PathVariable String filename) throws IOException {
        Path filePath = getAgentFilePath(agentId, filename);
        if (!Files.exists(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + filename);
        }
        return Map.of("filename", filename, "content", Files.readString(filePath));
    }

    @PostMapping("/{id}/files/{filename}")
    public Map<String, Object> saveFile(@PathVariable("id") String agentId,
                                         @PathVariable String filename,
                                         @RequestBody Map<String, String> body) throws IOException {
        Path filePath = getAgentFilePath(agentId, filename);
        Files.writeString(filePath, body.getOrDefault("content", ""));
        return Map.of("saved", true, "filename", filename);
    }

    @DeleteMapping("/{id}/files/{filename}")
    public Map<String, Object> deleteFile(@PathVariable("id") String agentId,
                                           @PathVariable String filename) throws IOException {
        Path filePath = getAgentFilePath(agentId, filename);
        boolean deleted = Files.deleteIfExists(filePath);
        return Map.of("deleted", deleted, "filename", filename);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Path getAgentFilePath(String agentId, String filename) {
        CoPawRootConfig.AgentProfileRef ref =
                rootConfig.getAgents().getProfiles().get(agentId);
        if (ref == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found");
        // Security: validate filename has no path separators
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename: " + filename);
        }
        return Paths.get(ref.getWorkspaceDir()).resolve(filename);
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
    @Data public static class CreateAgentRequest {
        private String name;
        private String description;
        private String workspaceDir;
        private String language;
    }

    @Data public static class UpdateAgentRequest {
        private String name;
        private String description;
        private Boolean enabled;
    }
}
