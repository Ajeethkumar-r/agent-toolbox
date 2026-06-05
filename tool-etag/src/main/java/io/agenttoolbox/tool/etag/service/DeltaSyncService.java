package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class DeltaSyncService {

    private final StorageAdapter storageAdapter;

    public DeltaSyncService(StorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    public String sync(String localPath, String bucketName) {
        Path localDir = Path.of(localPath);
        int synced = 0;
        int skipped = 0;
        int total = 0;
        long bytesTransferred = 0;
        StringBuilder log = new StringBuilder();

        try (Stream<Path> walk = Files.walk(localDir)) {
            var files = walk.filter(Files::isRegularFile).toList();
            total = files.size();
            log.append(String.format("Syncing %s to %s (%d files)...%n", localPath, bucketName, total));

            for (Path file : files) {
                String key = localDir.relativize(file).toString().replace('\\', '/');
                byte[] content = Files.readAllBytes(file);
                String localMd5 = Md5Hasher.hashBytes(content);

                boolean needsUpload = false;
                if (!storageAdapter.exists(bucketName, key)) {
                    needsUpload = true;
                    log.append(String.format("  [NEW]     %s (%s)%n", key, formatBytes(content.length)));
                } else {
                    FileMetadata remoteMetadata = storageAdapter.getMetadata(bucketName, key);
                    if (!remoteMetadata.md5Hash().equals(localMd5)) {
                        needsUpload = true;
                        log.append(String.format("  [UPDATED] %s (%s)%n", key, formatBytes(content.length)));
                    } else {
                        log.append(String.format("  [SKIP]    %s (unchanged)%n", key));
                    }
                }

                if (needsUpload) {
                    storageAdapter.write(bucketName, key, content, localMd5);
                    bytesTransferred += content.length;
                    synced++;
                } else {
                    skipped++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to sync local directory: " + localPath, e);
        }

        String transferredStr = formatBytes(bytesTransferred);
        String summary = String.format("Synced %d/%d files. %d skipped (unchanged). %s transferred.",
                synced, total, skipped, transferredStr);

        return summary + "\n" + log.toString().stripTrailing();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else {
            return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        }
    }
}
