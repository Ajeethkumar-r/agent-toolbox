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

            FileMetadata metadata = storageAdapter.write(bucketName, destinationKey, content, md5);

            return String.format("Uploaded %s to %s/%s (%d bytes, MD5 verified: %s)",
                    path.getFileName(), bucketName, destinationKey, metadata.size(), metadata.md5Hash());
        } catch (HashMismatchException e) {
            return String.format("Upload REJECTED — MD5 mismatch for %s: expected=%s, actual=%s",
                    destinationKey, e.getExpectedMd5(), e.getActualMd5());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read local file: " + localFilePath, e);
        }
    }
}
