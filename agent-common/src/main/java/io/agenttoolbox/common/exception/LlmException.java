package io.agenttoolbox.common.exception;

public class LlmException extends AgentException {
    public LlmException(String message) { super(message); }
    public LlmException(String message, Throwable cause) { super(message, cause); }
}
