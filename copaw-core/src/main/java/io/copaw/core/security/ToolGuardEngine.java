package io.copaw.core.security;

import io.copaw.common.config.ToolGuardConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Orchestrates all registered ToolGuardians and aggregates findings.
 * Maps to Python: ToolGuardEngine in security/tool_guard/engine.py
 *
 * Usage:
 *   ToolGuardResult result = engine.guard("execute_shell_command", params);
 *   if (result.isDenied()) { throw new ToolDeniedException(...); }
 *   if (result.requiresApproval()) { // ask user }
 */
@Service
@Slf4j
public class ToolGuardEngine {

    private final List<ToolGuardian> guardians;
    private volatile boolean enabled;
    private volatile Set<String> deniedTools;
    private volatile Set<String> guardedTools; // null = all tools

    public ToolGuardEngine(ToolGuardConfig config, List<ToolGuardian> guardians) {
        this.guardians = new ArrayList<>(guardians);
        this.enabled = config.isEnabled();
        this.deniedTools = new HashSet<>(config.getDeniedTools());
        this.guardedTools = config.getGuardedTools().isEmpty()
                ? null
                : new HashSet<>(config.getGuardedTools());
        log.info("ToolGuardEngine initialized: enabled={}, guardians={}, deniedTools={}",
                enabled, getGuardianNames(), deniedTools);
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Core interface
    // ------------------------------------------------------------------

    /**
     * Guard a tool call.
     *
     * @param toolName     name of the tool being called
     * @param params       tool arguments
     * @param onlyAlwaysRun if true, only run guardians with alwaysRun=true
     * @return ToolGuardResult, or null if guarding is disabled
     */
    public ToolGuardResult guard(String toolName, Map<String, Object> params,
                                  boolean onlyAlwaysRun) {
        if (!enabled) {
            return null;
        }

        long t0 = System.nanoTime();
        ToolGuardResult result = new ToolGuardResult(toolName, params);

        List<ToolGuardian> activeGuardians = onlyAlwaysRun
                ? guardians.stream().filter(ToolGuardian::isAlwaysRun).toList()
                : guardians;

        for (ToolGuardian guardian : activeGuardians) {
            try {
                List<GuardFinding> findings = guardian.guard(toolName, params);
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

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
