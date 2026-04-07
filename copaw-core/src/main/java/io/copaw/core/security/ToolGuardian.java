package io.copaw.core.security;

import java.util.List;
import java.util.Map;

/**
 * Base interface for all tool guardians.
 * Maps to Python: BaseToolGuardian in security/tool_guard/guardians/__init__.py
 *
 * Implementations:
 * - FilePathToolGuardian  - file path safety
 * - RuleBasedToolGuardian - regex rule matching
 */
public interface ToolGuardian {

    /** Unique name for this guardian */
    String getName();

    /**
     * Whether this guardian should always run, even for tools outside the
     * guarded scope (e.g., file path checks should always apply).
     */
    default boolean isAlwaysRun() {
        return false;
    }

    /**
     * Examine a tool call and return any findings.
     *
     * @param toolName  name of the tool being called
     * @param params    the tool's arguments
     * @return list of findings (empty = safe)
     */
    List<GuardFinding> guard(String toolName, Map<String, Object> params);

    /**
     * Optional: reload rules from disk/config.
     */
    default void reload() {}
}
