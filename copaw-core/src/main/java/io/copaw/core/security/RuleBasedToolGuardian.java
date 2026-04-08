package io.copaw.core.security;

import io.copaw.common.config.ToolGuardConfig;
import io.copaw.common.config.ToolGuardRuleConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Rule-based tool guardian using regex patterns from configuration.
 * Maps to Python: RuleBasedToolGuardian in security/tool_guard/guardians/rule_guardian.py
 */
@Slf4j
public class RuleBasedToolGuardian implements ToolGuardian {

    private final ToolGuardConfig config;
    private List<CompiledRule> compiledRules;

    public RuleBasedToolGuardian(ToolGuardConfig config) {
        this.config = config != null ? config : new ToolGuardConfig();
        compileRules();
    }

    @Override
    public String getName() {
        return "rule-based-guardian";
    }

    @Override
    public void reload() {
        compileRules();
        log.info("RuleBasedToolGuardian reloaded {} rules", compiledRules.size());
    }

    @Override
    public List<GuardFinding> guard(String toolName, Map<String, Object> params) {
        List<GuardFinding> findings = new ArrayList<>();
        String paramsStr = paramsToString(params);

        for (CompiledRule rule : compiledRules) {
            if (!rule.config.isEnabled()) {
                continue;
            }
            if (!appliesToTool(rule.config, toolName)) {
                continue;
            }
            if (!(rule.pattern.matcher(paramsStr).find() || rule.pattern.matcher(toolName).find())) {
                continue;
            }

            GuardSeverity severity = rule.config.isDeny()
                    ? GuardSeverity.CRITICAL
                    : parseSeverity(rule.config.getSeverity());
            findings.add(GuardFinding.builder()
                    .toolName(toolName)
                    .ruleId(rule.config.getId())
                    .severity(severity)
                    .description(rule.config.getDescription())
                    .requiresApproval(!rule.config.isDeny()
                            && (rule.config.isRequireApproval() || severity == GuardSeverity.HIGH))
                    .threatCategory("rule-match")
                    .build());
        }

        return findings;
    }

    private void compileRules() {
        compiledRules = new ArrayList<>();
        for (ToolGuardRuleConfig rule : config.getRules()) {
            if (rule.getPattern() == null || rule.getPattern().isBlank()) {
                continue;
            }
            try {
                Pattern pattern = Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE);
                compiledRules.add(new CompiledRule(rule, pattern));
            } catch (PatternSyntaxException e) {
                log.warn("Invalid regex in tool guard rule '{}': {}", rule.getId(), e.getMessage());
            }
        }
        log.debug("Compiled {} tool guard rules", compiledRules.size());
    }

    private boolean appliesToTool(ToolGuardRuleConfig rule, String toolName) {
        List<String> tools = rule.getTools();
        return tools == null || tools.isEmpty() || tools.contains(toolName);
    }

    private String paramsToString(Map<String, Object> params) {
        if (params == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append(' ');
        }
        return sb.toString();
    }

    private GuardSeverity parseSeverity(String value) {
        if (value == null) {
            return GuardSeverity.MEDIUM;
        }
        try {
            return GuardSeverity.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GuardSeverity.MEDIUM;
        }
    }

    private record CompiledRule(ToolGuardRuleConfig config, Pattern pattern) {}
}
