package io.agenttoolbox.core;

/**
 * SPI interface for tool providers.
 * Implementations supply named tools that the agent can use during conversations.
 */
public interface ToolProvider {

    /** Unique name identifying this tool. */
    String name();

    /** Human-readable description of what this tool does. */
    String description();

    /** Returns the tool instance that LangChain4j will introspect for @Tool methods. */
    Object toolInstance();
}
