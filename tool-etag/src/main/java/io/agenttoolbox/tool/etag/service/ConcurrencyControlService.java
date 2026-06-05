package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.exception.PreconditionFailedException;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConcurrencyControlService {

    private final StorageAdapter storageAdapter;

    public ConcurrencyControlService(StorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    public String update(String bucketName, String fileKey, String localFilePath, String knownEtag) {
        try {
            byte[] content = Files.readAllBytes(Path.of(localFilePath));

            FileMetadata metadata = storageAdapter.conditionalWrite(bucketName, fileKey, content, knownEtag);

            return String.format("Updated %s/%s successfully. New ETag: %s",
                    bucketName, fileKey, metadata.etag());
        } catch (PreconditionFailedException e) {
            return String.format("Conflict — file %s/%s was modified by another process. Expected ETag: %s, Current ETag: %s",
                    bucketName, fileKey, e.getExpectedEtag(), e.getCurrentEtag());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read local file: " + localFilePath, e);
        }
    }
}
