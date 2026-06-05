package io.agenttoolbox.common.exception;

public class LlmUnavailableException extends LlmException {
    public LlmUnavailableException(String message) { super(message); }
    public LlmUnavailableException(String message, Throwable cause) { super(message, cause); }
}
