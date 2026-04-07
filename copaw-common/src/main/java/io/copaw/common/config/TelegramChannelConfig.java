package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Telegram channel configuration.
 * Maps to Python: TelegramConfig in config.py
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramChannelConfig extends BaseChannelConfig {

    private String botToken = "";
    private String httpProxy = "";
    private String httpProxyAuth = "";
    private Boolean showTyping = null;
}
