package io.agenttoolbox.common.exception;

public class LlmTimeoutException extends LlmException {
    public LlmTimeoutException(String message) { super(message); }
    public LlmTimeoutException(String message, Throwable cause) { super(message, cause); }
}
