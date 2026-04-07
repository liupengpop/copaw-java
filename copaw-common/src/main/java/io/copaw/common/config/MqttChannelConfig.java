package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MQTT channel configuration.
 * Maps to Python: MQTTConfig in config.py
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttChannelConfig extends BaseChannelConfig {

    private String host = "";
    private Integer port = null;
    private String transport = "";
    private boolean cleanSession = true;
    private int qos = 2;
    private String username = null;
    private String password = null;
    private String subscribeTopic = "";
    private String publishTopic = "";
    private boolean tlsEnabled = false;
    private String tlsCaCerts = null;
    private String tlsCertfile = null;
    private String tlsKeyfile = null;
}
