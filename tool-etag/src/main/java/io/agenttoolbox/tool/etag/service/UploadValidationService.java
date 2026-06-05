package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.exception.HashMismatchException;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadValidationService {

    private final StorageAdapter storageAdapter;

    public UploadValidationService(StorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    public String upload(String localFilePath, String bucketName, String destinationKey) {
        try {
            Path path = Path.of(localFilePath);
            byte[] content = Files.readAllBytes(path);
            String md5 = Md5Hasher.hashBytes(content);

            // Auto-derive key from filename if empty or null
            if (destinationKey == null || destinationKey.isBlank()) {
                destinationKey = path.getFileName().toString();
            }

            FileMetadata metadata = storageAdapter.write(bucketName, destinationKey, content, md5);

            return String.format("Uploaded %s/%s (%s, MD5 verified: %s)",
                    bucketName, destinationKey, formatBytes(metadata.size()), metadata.md5Hash());
        } catch (HashMismatchException e) {
            return String.format("REJECTED — MD5 mismatch: expected=%s, actual=%s",
                    e.getExpectedMd5(), e.getActualMd5());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read local file: " + localFilePath, e);
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
}
