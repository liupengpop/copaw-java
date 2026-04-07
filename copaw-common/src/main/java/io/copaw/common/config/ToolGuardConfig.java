package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool Guard configuration.
 * Maps to Python: ToolGuardConfig in config.py
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolGuardConfig {

    private boolean enabled = true;
    /** Tool names that are always denied; overrides rules */
    private List<String> deniedTools = new ArrayList<>();
    /** Tool names in guard scope; null/empty = all tools */
    private List<String> guardedTools = new ArrayList<>();
    private List<ToolGuardRuleConfig> rules = new ArrayList<>();
}
