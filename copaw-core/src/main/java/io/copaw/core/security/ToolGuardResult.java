package io.copaw.core.security;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aggregated result from the ToolGuardEngine.
 * Maps to Python: ToolGuardResult in security/tool_guard/models.py
 */
@Data
public class ToolGuardResult {

    private final String toolName;
    private final Map<String, Object> params;
    private final List<GuardFinding> findings = new ArrayList<>();
    private final List<String> guardiansUsed = new ArrayList<>();
    private final List<Map<String, String>> guardiansFailed = new ArrayList<>();
    private double guardDurationSeconds;

    /**
     * True when any finding has severity CRITICAL, meaning the tool call
     * must be blocked.
     */
    public boolean isDenied() {
        return findings.stream()
                .anyMatch(f -> f.getSeverity() == GuardSeverity.CRITICAL);
    }

    /**
     * True when any finding requires user approval before proceeding.
     */
    public boolean requiresApproval() {
        return findings.stream().anyMatch(GuardFinding::isRequiresApproval);
    }

    /**
     * True when there are no findings at all.
     */
    public boolean isSafe() {
        return findings.isEmpty();
    }

    /**
     * The highest severity across all findings, or null if no findings.
     */
    public GuardSeverity getMaxSeverity() {
        return findings.stream()
                .map(GuardFinding::getSeverity)
                .min(java.util.Comparator.comparingInt(GuardSeverity::ordinal))
                .orElse(null);
    }
}
