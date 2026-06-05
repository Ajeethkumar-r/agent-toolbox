package io.agenttoolbox.common.exception;

public class ToolNotFoundException extends ToolException {
    public ToolNotFoundException(String toolName) { super("Tool not found: " + toolName); }
}
