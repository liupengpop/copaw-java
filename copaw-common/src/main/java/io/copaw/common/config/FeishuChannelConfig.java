package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Feishu (Lark) channel configuration.
 * Maps to Python: FeishuConfig in config.py
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeishuChannelConfig extends BaseChannelConfig {

    private String appId = "";
    private String appSecret = "";
    private String encryptKey = "";
    private String verificationToken = "";
    private String mediaDir = null;
    /** "feishu" for China, "lark" for international */
    private String domain = "feishu";
}
