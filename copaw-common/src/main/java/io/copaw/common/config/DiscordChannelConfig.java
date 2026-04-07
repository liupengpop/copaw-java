package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Discord channel configuration.
 * Maps to Python: DiscordConfig in config.py
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiscordChannelConfig extends BaseChannelConfig {

    private String botToken = "";
    private String httpProxy = "";
    private String httpProxyAuth = "";
    private boolean acceptBotMessages = false;
}
