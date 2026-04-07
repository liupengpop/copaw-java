package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool Guard rule configuration.
 * Maps to Python: ToolGuardRuleConfig in config.py
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolGuardRuleConfig {

    private String id;
    private String description = "";
    /** Severity: CRITICAL | HIGH | MEDIUM | LOW | INFO */
    private String severity = "MEDIUM";
    /** Tool names this rule applies to; empty = all tools */
    private List<String> tools = new ArrayList<>();
    /** Regex pattern to match in tool arguments */
    private String pattern = "";
    /** If true, the tool call is blocked immediately */
    private boolean deny = false;
    /** If true, ask user approval before proceeding */
    private boolean requireApproval = false;
    private boolean enabled = true;
}
