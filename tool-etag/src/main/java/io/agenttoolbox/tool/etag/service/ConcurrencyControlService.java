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
            Path path = Path.of(localFilePath);
            byte[] content = Files.readAllBytes(path);

            StringBuilder out = new StringBuilder();
            out.append(String.format("Updating %s/%s with %s...%n", bucketName, fileKey, path.getFileName()));
            out.append(String.format("  Checking ETag: %s%n", knownEtag));

            FileMetadata metadata = storageAdapter.conditionalWrite(bucketName, fileKey, content, knownEtag);

            out.append(String.format("  ETag matched. Write complete.%n"));
            out.append(String.format("Done. Updated %s/%s. New ETag: %s", bucketName, fileKey, metadata.etag()));
            return out.toString();
        } catch (PreconditionFailedException e) {
            return String.format("Updating %s/%s...%n  Checking ETag: %s%n  CONFLICT — file was modified by another process.%n  Your ETag: %s%n  Current ETag: %s",
                    bucketName, fileKey, knownEtag, e.getExpectedEtag(), e.getCurrentEtag());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read local file: " + localFilePath, e);
        }
    }
}
