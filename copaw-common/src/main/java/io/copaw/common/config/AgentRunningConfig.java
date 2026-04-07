package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent running configuration (runtime tuning parameters).
 * Maps to Python: AgentsRunningConfig in config.py
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentRunningConfig {

    /** Maximum ReAct iterations per request */
    private int maxIters = 30;

    /** Maximum input length in characters */
    private int maxInputLength = 50000;

    /** Token threshold to trigger memory compaction (0 = auto) */
    private int memoryCompactThreshold = 0;

    /** Maximum concurrent LLM requests */
    private int maxConcurrentLlm = 5;

    /** LLM max retries on transient errors */
    private int llmMaxRetries = 3;

    /** LLM acquire timeout in seconds */
    private int llmAcquireTimeout = 30;
}
