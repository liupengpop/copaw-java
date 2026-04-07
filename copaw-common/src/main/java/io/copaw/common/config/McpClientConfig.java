package io.copaw.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP client configuration.
 * Maps to Python: MCPClientConfig in config.py
 *
 * transport: "stdio" | "sse" | "http"
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpClientConfig {

    private String id;
    private String name = "";
    private String description = "";
    private boolean enabled = true;

    /** Transport type: "stdio" | "sse" | "http" */
    private String transport = "stdio";

    // StdIO transport fields
    private String command = "";
    private List<String> args = new ArrayList<>();
    private java.util.Map<String, String> env = new java.util.HashMap<>();

    // SSE / HTTP transport fields
    private String url = "";

    /** Optional extra headers for SSE/HTTP */
    private java.util.Map<String, String> headers = new java.util.HashMap<>();
}
