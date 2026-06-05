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
            return String.format("NOT MODIFIED — %s/%s unchanged. ETag: %s. 0 bytes transferred.",
                    bucketName, fileKey, result.etag());
        }
        return String.format("MODIFIED — %s/%s changed. New ETag: %s. %s transferred.",
                bucketName, fileKey, result.etag(), formatBytes(result.contentLength()));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
}
