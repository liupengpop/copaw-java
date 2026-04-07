package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * LLM model slot configuration (one model binding for an agent).
 * Maps to Python: ModelSlotConfig / AgentsLLMRoutingConfig
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelSlotConfig {

    /** Provider ID (e.g., "openai", "anthropic", "ollama") */
    private String providerId;

    /** Model name (e.g., "gpt-4o", "claude-3-5-sonnet-20241022") */
    private String model;

    /** Optional base URL override for provider */
    private String baseUrl = null;

    /** Optional API key override (overrides provider-level key) */
    private String apiKey = null;

    /** Max tokens for completion */
    private Integer maxTokens = null;

    /** Temperature */
    private Double temperature = null;
}
