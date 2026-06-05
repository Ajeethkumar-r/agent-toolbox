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

            StringBuilder out = new StringBuilder();
            out.append(String.format("Uploading %s (%s) to %s/%s...%n",
                    path.getFileName(), formatBytes(content.length), bucketName, destinationKey));
            out.append(String.format("  Computing MD5: %s%n", md5));

            FileMetadata metadata = storageAdapter.write(bucketName, destinationKey, content, md5);

            out.append(String.format("  MD5 verified. Upload complete.%n"));
            out.append(String.format("Done. Uploaded %s/%s (%s, ETag: %s)",
                    bucketName, destinationKey, formatBytes(metadata.size()), metadata.md5Hash()));
            return out.toString();
        } catch (HashMismatchException e) {
            return String.format("Uploading %s to %s...%nREJECTED — MD5 mismatch: expected=%s, actual=%s",
                    localFilePath, bucketName, e.getExpectedMd5(), e.getActualMd5());
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
