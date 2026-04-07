package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OneBot v11 channel configuration (NapCat / Lagrange).
 * Maps to Python: OneBotConfig in config.py
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OneBotChannelConfig extends BaseChannelConfig {

    private String wsHost = "0.0.0.0";
    private int wsPort = 6199;
    private String accessToken = "";
    private boolean shareSessionInGroup = false;
}
