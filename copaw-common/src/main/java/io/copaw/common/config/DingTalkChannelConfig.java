package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DingTalk channel configuration.
 * Maps to Python: DingTalkConfig in config.py
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DingTalkChannelConfig extends BaseChannelConfig {

    private String clientId = "";
    private String clientSecret = "";
    private String messageType = "markdown";
    private String cardTemplateId = "";
    private String cardTemplateKey = "content";
    private String robotCode = "";
    private String mediaDir = null;
    private boolean cardAutoLayout = false;
}
