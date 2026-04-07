package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Base channel configuration.
 * Maps to Python: BaseChannelConfig in config.py
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseChannelConfig {

    private boolean enabled = false;
    private String botPrefix = "";
    private boolean filterToolMessages = false;
    private boolean filterThinking = false;
    /** "open" | "allowlist" */
    private String dmPolicy = "open";
    /** "open" | "allowlist" */
    private String groupPolicy = "open";
    private List<String> allowFrom = new ArrayList<>();
    private String denyMessage = "";
    private boolean requireMention = false;
}
