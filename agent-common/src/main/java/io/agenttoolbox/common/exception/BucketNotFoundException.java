package io.agenttoolbox.common.exception;

public class BucketNotFoundException extends StorageException {
    public BucketNotFoundException(String bucket) { super("Bucket not found: " + bucket); }
}
