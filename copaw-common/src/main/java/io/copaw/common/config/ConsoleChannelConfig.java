package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Console channel configuration.
 * Maps to Python: ConsoleConfig in config.py
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsoleChannelConfig extends BaseChannelConfig {

    private String mediaDir = null;

    public ConsoleChannelConfig() {
        setEnabled(true); // Console is enabled by default
    }
}
