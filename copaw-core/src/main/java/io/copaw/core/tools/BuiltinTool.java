package io.copaw.core.tools;

import java.util.Map;

/**
 * Base interface for all built-in tools callable by the agent.
 * Maps to Python: tool functions in agents/tools/__init__.py
 *
 * Tools are registered with the AgentScope ReActAgent toolkit.
 * Each tool has a name, description, and execute() method.
 */
public interface BuiltinTool {

    /** Tool name (must be unique) */
    String getName();

    /** Human-readable description shown to the LLM */
    String getDescription();

    /**
     * Execute the tool with the given parameters.
     *
     * @param params  map of parameter name → value (from LLM function call)
     * @return result string to return to the agent
     */
    String execute(Map<String, Object> params) throws Exception;
}
