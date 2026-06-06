package io.agenttoolbox.common.exception;

public class BucketNotIndexedException extends StorageException {
    public BucketNotIndexedException(String bucket) {
        super("Bucket has not been indexed: " + bucket);
    }
}
