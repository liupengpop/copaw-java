package io.copaw.core.security;

/**
 * Guard severity levels.
 * Maps to Python: GuardSeverity enum in security/tool_guard/models.py
 */
public enum GuardSeverity {
    /** Tool call is blocked unconditionally */
    CRITICAL,
    /** Tool call is blocked unless user approves */
    HIGH,
    /** User should be warned; approval recommended */
    MEDIUM,
    /** Informational warning */
    LOW,
    /** Audit/logging only */
    INFO
}
