package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Memory configuration.
 * Maps to Python: ContextCompactConfig + MemorySummaryConfig + EmbeddingConfig
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryConfig {

    /** Backend: "remelight" */
    private String backend = "remelight";

    /** Context window token limit (triggers compaction) */
    private int contextTokenLimit = 8000;

    /** Compaction threshold: ratio of context to trigger compaction (0.0-1.0) */
    private double compactThreshold = 0.85;

    /** Enable automatic context compaction */
    private boolean autoCompact = true;

    /** Enable memory summaries */
    private boolean summaryEnabled = true;

    /** Max tokens in a summary */
    private int summaryMaxTokens = 2000;

    /** Enable semantic search for memory */
    private boolean semanticSearchEnabled = false;

    /** Embedding model for semantic search */
    private String embeddingModel = null;

    /** Embedding dimension */
    private int embeddingDimension = 1536;
}
