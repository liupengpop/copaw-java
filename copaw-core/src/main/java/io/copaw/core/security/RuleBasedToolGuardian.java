package io.copaw.core.security;

import io.copaw.common.config.ToolGuardConfig;
import io.copaw.common.config.ToolGuardRuleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Rule-based tool guardian using regex patterns from configuration.
 * Maps to Python: RuleBasedToolGuardian in security/tool_guard/guardians/rule_guardian.py
 *
 * Rules are defined in agent.json under toolGuard.rules[].
 * Each rule has:
 *   - id, description, severity
 *   - tools: list of tool names this rule applies to (empty = all)
 *   - pattern: regex pattern to match against serialized tool params
 *   - deny: block the call if matched
 *   - requireApproval: ask user approval if matched
 */
@Component
@Slf4j
public class RuleBasedToolGuardian implements ToolGuardian {

    private final ToolGuardConfig config;
    private List<CompiledRule> compiledRules;

    public RuleBasedToolGuardian(ToolGuardConfig config) {
        this.config = config;
        compileRules();
    }

    @Override
    public String getName() { return "rule-based-guardian"; }

    @Override
    public void reload() {
        compileRules();
        log.info("RuleBasedToolGuardian reloaded {} rules", compiledRules.size());
    }

    @Override
    public List<GuardFinding> guard(String toolName, Map<String, Object> params) {
        List<GuardFinding> findings = new ArrayList<>();

        // Serialize params to string for regex matching
        String paramsStr = paramsToString(params);

        for (CompiledRule rule : compiledRules) {
            if (!rule.config.isEnabled()) continue;
            if (!appliesToTool(rule.config, toolName)) continue;

            if (rule.pattern.matcher(paramsStr).find()
                    || rule.pattern.matcher(toolName).find()) {
                GuardSeverity severity = parseSeverity(rule.config.getSeverity());
                findings.add(GuardFinding.builder()
                        .toolName(toolName)
                        .ruleId(rule.config.getId())
                        .severity(severity)
                        .description(rule.config.getDescription())
                        .requiresApproval(rule.config.isRequireApproval() ||
                                severity == GuardSeverity.HIGH)
                        .threatCategory("rule-match")
                        .build());
            }
        }

        return findings;
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void compileRules() {
        compiledRules = new ArrayList<>();
        for (ToolGuardRuleConfig rule : config.getRules()) {
            if (rule.getPattern() == null || rule.getPattern().isBlank()) continue;
            try {
                Pattern p = Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE);
                compiledRules.add(new CompiledRule(rule, p));
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
        if (params == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(' ');
        }
        return sb.toString();
    }

    private GuardSeverity parseSeverity(String s) {
        if (s == null) return GuardSeverity.MEDIUM;
        try {
            return GuardSeverity.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GuardSeverity.MEDIUM;
        }
    }

    private record CompiledRule(ToolGuardRuleConfig config, Pattern pattern) {}
}
