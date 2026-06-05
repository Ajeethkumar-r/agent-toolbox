package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.storage.ConditionalReadResult;
import io.agenttoolbox.common.storage.StorageAdapter;

public class CacheValidationService {

    private final StorageAdapter storageAdapter;

    public CacheValidationService(StorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    public String validate(String bucketName, String fileKey, String knownEtag) {
        ConditionalReadResult result = storageAdapter.conditionalRead(bucketName, fileKey, knownEtag);

        if (!result.modified()) {
            return String.format("NOT MODIFIED — %s/%s has not changed. ETag: %s. 0 bytes transferred.",
                    bucketName, fileKey, result.etag());
        } else {
            return String.format("MODIFIED — %s/%s has changed. New ETag: %s. %d bytes transferred.",
                    bucketName, fileKey, result.etag(), result.contentLength());
        }
    }
}
