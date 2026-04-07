package io.copaw.cron;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;

/**
 * Cron job specification.
 * Maps to Python: CronJobSpec in app/crons/models.py
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CronJobSpec {

    private String id;
    private String name;
    private String description = "";

    /** Cron expression, for example one run every 5 minutes. */
    private String cronExpression;

    /** Message to send to the agent when the job fires */
    private String message;

    /** Target agent ID */
    private String agentId;

    /** Session ID for the cron-triggered conversation */
    private String sessionId = "_cron";

    /** Whether this job is enabled */
    private boolean enabled = true;

    /** Job creation time */
    private Instant createdAt = Instant.now();

    /** Last time this job ran */
    private Instant lastRunAt = null;

    /** Next scheduled run time */
    private Instant nextRunAt = null;

    /** Job status: ACTIVE | PAUSED | ERROR */
    private String status = "ACTIVE";
}
