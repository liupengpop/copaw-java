package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Heartbeat configuration.
 * Maps to Python: HeartbeatConfig in config.py
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeartbeatConfig {

    private boolean enabled = false;
    /** Cron expression or interval string, for example every 10 minutes or every 30m. */
    private String every = "1h";
    /** Message to send as heartbeat */
    private String target = "heartbeat";
}
