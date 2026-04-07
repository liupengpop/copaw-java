package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.copaw.common.json.CoPawObjectMapperFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root configuration: tracks all agent profiles.
 * Root config.json layout:
 * {
 *   "agents": {
 *     "profiles": {
 *       "abc123": { "id": "abc123", "name": "...", "workspace_dir": "..." },
 *       ...
 *     }
 *   }
 * }
 *
 * Maps to Python: CoPawConfig + AgentsConfig in config.py
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class CoPawRootConfig {

    private AgentsConfig agents = new AgentsConfig();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgentsConfig {
        /** agent_id -> AgentProfileRef */
        private Map<String, AgentProfileRef> profiles = new LinkedHashMap<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgentProfileRef {
        private String id;
        private String name;
        private String workspaceDir;
        private boolean enabled = true;
    }

    // -----------------------------------------------------------------------
    // Static helpers to load / save
    // -----------------------------------------------------------------------

    private static final ObjectMapper MAPPER = CoPawObjectMapperFactory.create();

    public static CoPawRootConfig load(Path configPath) {
        if (!Files.exists(configPath)) {
            log.info("config.json not found at {}, returning default config", configPath);
            return new CoPawRootConfig();
        }
        try {
            return MAPPER.readValue(configPath.toFile(), CoPawRootConfig.class);
        } catch (IOException e) {
            log.error("Failed to load config.json from {}: {}", configPath, e.getMessage());
            return new CoPawRootConfig();
        }
    }

    public void save(Path configPath) throws IOException {
        Files.createDirectories(configPath.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), this);
    }
}
