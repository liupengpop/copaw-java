package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * WeChat Work (Enterprise WeChat) channel configuration.
 * Maps to Python: WecomConfig in config.py
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WecomChannelConfig extends BaseChannelConfig {

    private String botId = "";
    private String corpId = "";
    private String secret = "";
    private String token = "";
    private String encodingAesKey = "";
    private String mediaDir = null;
}
