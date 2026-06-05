package io.agenttoolbox.common.exception;

public class StorageException extends AgentException {
    public StorageException(String message) { super(message); }
    public StorageException(String message, Throwable cause) { super(message, cause); }
}
