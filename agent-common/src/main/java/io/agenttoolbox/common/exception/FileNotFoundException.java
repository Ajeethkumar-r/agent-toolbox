package io.agenttoolbox.common.exception;

public class FileNotFoundException extends StorageException {
    public FileNotFoundException(String bucket, String key) { super("File not found: " + bucket + "/" + key); }
}
