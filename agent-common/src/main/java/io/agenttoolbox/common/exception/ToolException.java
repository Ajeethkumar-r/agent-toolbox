package io.agenttoolbox.common.exception;

public class ToolException extends AgentException {
    public ToolException(String message) { super(message); }
    public ToolException(String message, Throwable cause) { super(message, cause); }
}
