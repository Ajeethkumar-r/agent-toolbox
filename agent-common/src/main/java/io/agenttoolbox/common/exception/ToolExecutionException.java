package io.agenttoolbox.common.exception;

public class ToolExecutionException extends ToolException {
    public ToolExecutionException(String toolName, Throwable cause) { super("Tool execution failed: " + toolName, cause); }
}
