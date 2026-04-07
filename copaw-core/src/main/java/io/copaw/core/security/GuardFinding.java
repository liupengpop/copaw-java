package io.copaw.core.security;

import lombok.Builder;
import lombok.Data;

/**
 * A single finding from a tool guardian.
 * Maps to Python: GuardFinding in security/tool_guard/models.py
 */
@Data
@Builder
public class GuardFinding {

    private String toolName;
    private String ruleId;
    private GuardSeverity severity;
    private String description;
    private boolean requiresApproval;

    /** Category of the threat (informational) */
    private String threatCategory;
}
