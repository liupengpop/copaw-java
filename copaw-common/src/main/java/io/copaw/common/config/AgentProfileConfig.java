package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-agent profile configuration stored in workspace/agent.json.
 * Maps to Python: AgentProfileConfig in config.py
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentProfileConfig {

    /** Unique agent identifier (6-char short UUID) */
    private String id;

    /** Display name */
    private String name = "CoPaw Agent";

    /** Description */
    private String description = "";

    /** Workspace directory path */
    private String workspaceDir;

    /** Language code (e.g., "en", "zh") */
    private String language = "en";

    /** Whether this agent is enabled */
    private boolean enabled = true;

    /** Active LLM model configuration */
    private ModelSlotConfig activeModel = null;

    /** Memory configuration */
    private MemoryConfig memory = new MemoryConfig();

    /** Agent runtime parameters */
    private AgentRunningConfig running = new AgentRunningConfig();

    /** MCP client configurations */
    private List<McpClientConfig> mcpClients = new ArrayList<>();

    /** Channel configurations (keyed by channel type) */
    private ConsoleChannelConfig console = new ConsoleChannelConfig();
    private TelegramChannelConfig telegram = null;
    private DingTalkChannelConfig dingtalk = null;
    private FeishuChannelConfig feishu = null;
    private WecomChannelConfig wecom = null;
    private DiscordChannelConfig discord = null;
    private MqttChannelConfig mqtt = null;
    private OneBotChannelConfig onebot = null;

    /** Tool Guard security configuration */
    private ToolGuardConfig toolGuard = new ToolGuardConfig();

    /** Heartbeat configuration */
    private HeartbeatConfig heartbeat = new HeartbeatConfig();
}
