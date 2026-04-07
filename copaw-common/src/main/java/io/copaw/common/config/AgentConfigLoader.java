package io.copaw.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.copaw.common.json.CoPawObjectMapperFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for loading and saving per-agent profile config (agent.json).
 *
 * Each agent workspace contains:
 *   <workspace_dir>/agent.json  → AgentProfileConfig
 *
 * Maps to Python: load_agent_config() / save_agent_config() in config.py
 */
@Slf4j
public final class AgentConfigLoader {

    private static final String AGENT_CONFIG_FILENAME = "agent.json";

    private static final ObjectMapper MAPPER = CoPawObjectMapperFactory.create();

    private AgentConfigLoader() {}

    public static AgentProfileConfig load(Path workspaceDir) {
        Path configPath = workspaceDir.resolve(AGENT_CONFIG_FILENAME);
        if (!Files.exists(configPath)) {
            log.info("agent.json not found at {}, returning default config", configPath);
            AgentProfileConfig cfg = new AgentProfileConfig();
            cfg.setWorkspaceDir(workspaceDir.toString());
            return cfg;
        }
        try {
            AgentProfileConfig cfg = MAPPER.readValue(configPath.toFile(), AgentProfileConfig.class);
            if (cfg.getWorkspaceDir() == null) {
                cfg.setWorkspaceDir(workspaceDir.toString());
            }
            return cfg;
        } catch (IOException e) {
            log.error("Failed to load agent.json from {}: {}", configPath, e.getMessage());
            AgentProfileConfig cfg = new AgentProfileConfig();
            cfg.setWorkspaceDir(workspaceDir.toString());
            return cfg;
        }
    }

    public static void save(Path workspaceDir, AgentProfileConfig config) throws IOException {
        Files.createDirectories(workspaceDir);
        Path configPath = workspaceDir.resolve(AGENT_CONFIG_FILENAME);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        log.debug("Saved agent.json to {}", configPath);
    }

    /**
     * Generate a short 6-character agent ID (same length as Python version).
     */
    public static String generateShortAgentId() {
        // Use first 6 chars of a UUID without dashes
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
