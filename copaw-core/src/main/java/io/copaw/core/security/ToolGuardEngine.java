package io.copaw.core.security;

import io.copaw.common.config.ToolGuardConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates all registered ToolGuardians and aggregates findings.
 * Maps to Python: ToolGuardEngine in security/tool_guard/engine.py
 *
 * Usage:
 *   ToolGuardResult result = engine.guard("execute_shell_command", params);
 *   if (result != null && result.isDenied()) { throw new ToolDeniedException(...); }
 *   if (result != null && result.requiresApproval()) { // ask user }
 */
@Slf4j
public class ToolGuardEngine {

    private final List<ToolGuardian> guardians;
    private volatile boolean enabled;
    private volatile Set<String> deniedTools;
    private volatile Set<String> guardedTools; // null = all tools

    public ToolGuardEngine(ToolGuardConfig config, List<ToolGuardian> guardians) {
        ToolGuardConfig safeConfig = config != null ? config : new ToolGuardConfig();
        this.guardians = new ArrayList<>(guardians != null ? guardians : List.of());
        this.enabled = safeConfig.isEnabled();
        this.deniedTools = new HashSet<>(safeConfig.getDeniedTools());
        this.guardedTools = safeConfig.getGuardedTools().isEmpty()
                ? null
                : new HashSet<>(safeConfig.getGuardedTools());
        log.info("ToolGuardEngine initialized: enabled={}, guardians={}, deniedTools={}",
                enabled, getGuardianNames(), deniedTools);
    }

    public void registerGuardian(ToolGuardian guardian) {
        guardians.add(guardian);
        log.debug("Registered tool guardian: {}", guardian.getName());
    }

    public boolean unregisterGuardian(String name) {
        int before = guardians.size();
        guardians.removeIf(g -> g.getName().equals(name));
        return guardians.size() < before;
    }

    public List<String> getGuardianNames() {
        return guardians.stream().map(ToolGuardian::getName).toList();
    }

    /**
     * Guard a tool call.
     *
     * @param toolName name of the tool being called
     * @param params tool arguments
     * @param onlyAlwaysRun if true, only run guardians with alwaysRun=true
     * @return ToolGuardResult, or null if guarding is disabled
     */
    public ToolGuardResult guard(String toolName, Map<String, Object> params, boolean onlyAlwaysRun) {
        if (!enabled) {
            return null;
        }

        long t0 = System.nanoTime();
        ToolGuardResult result = new ToolGuardResult(toolName, params != null ? params : Map.of());

        if (isDenied(toolName)) {
            result.getFindings().add(GuardFinding.builder()
                    .toolName(toolName)
                    .ruleId("tool-denied-list")
                    .severity(GuardSeverity.CRITICAL)
                    .description("Tool is denied by workspace tool_guard configuration: " + toolName)
                    .requiresApproval(false)
                    .threatCategory("tool-denied")
                    .build());
        }

        List<ToolGuardian> activeGuardians = guardians.stream()
                .filter(guardian -> onlyAlwaysRun
                        ? guardian.isAlwaysRun()
                        : isGuarded(toolName) || guardian.isAlwaysRun())
                .toList();

        for (ToolGuardian guardian : activeGuardians) {
            try {
                List<GuardFinding> findings = guardian.guard(toolName, params != null ? params : Map.of());
                result.getFindings().addAll(findings);
                result.getGuardiansUsed().add(guardian.getName());
            } catch (Exception ex) {
                log.warn("Tool guardian '{}' failed on tool '{}': {}",
                        guardian.getName(), toolName, ex.getMessage());
                result.getGuardiansFailed().add(
                        Map.of("name", guardian.getName(), "error", ex.getMessage()));
            }
        }

        result.setGuardDurationSeconds((System.nanoTime() - t0) / 1e9);

        if (!result.isSafe()) {
            log.warn("Tool guard findings for '{}': severity={}, findings={}",
                    toolName, result.getMaxSeverity(), result.getFindings().size());
        }

        return result;
    }

    public ToolGuardResult guard(String toolName, Map<String, Object> params) {
        return guard(toolName, params, false);
    }

    public boolean isDenied(String toolName) {
        return deniedTools != null && deniedTools.contains(toolName);
    }

    public boolean isGuarded(String toolName) {
        return guardedTools == null || guardedTools.contains(toolName);
    }

    public void reload() {
        guardians.forEach(ToolGuardian::reload);
        log.info("Tool guard rules reloaded");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
