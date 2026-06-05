package io.agenttoolbox.tool.etag;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;

import java.util.List;

public class BrowserTools {

    private final StorageAdapter storageAdapter;

    public BrowserTools(StorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Tool("List all available storage buckets")
    public String listBuckets() {
        try {
            List<String> buckets = storageAdapter.listBuckets();
            if (buckets.isEmpty()) {
                return "No buckets found.";
            }
            StringBuilder sb = new StringBuilder("Buckets:\n");
            for (String bucket : buckets) {
                sb.append(String.format("  - %s%n", bucket));
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("List files in a storage bucket, optionally filtered by prefix")
    public String listFiles(
            @P("Name of the storage bucket") String bucketName,
            @P("Optional prefix to filter files, use empty string for all files") String prefix) {
        try {
            if (prefix == null) prefix = "";
            List<FileMetadata> files = storageAdapter.list(bucketName, prefix);
            if (files.isEmpty()) {
                return "No files found in " + bucketName + (prefix.isEmpty() ? "" : " with prefix " + prefix);
            }
            StringBuilder sb = new StringBuilder(String.format("Files in %s (%d):%n", bucketName, files.size()));
            for (FileMetadata meta : files) {
                sb.append(String.format("  %-40s %8s  ETag: %s%n",
                        meta.key(), formatBytes(meta.size()), meta.etag()));
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Get detailed metadata for a file including ETag, MD5, size, and last modified time")
    public String getFileInfo(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file in the bucket") String fileKey) {
        try {
            FileMetadata meta = storageAdapter.getMetadata(bucketName, fileKey);
            return String.format(
                    "File: %s/%s%n  Size: %s (%d bytes)%n  MD5: %s%n  ETag: %s%n  Last Modified: %s%n  Content Type: %s",
                    meta.bucket(), meta.key(),
                    formatBytes(meta.size()), meta.size(),
                    meta.md5Hash(), meta.etag(),
                    meta.lastModified(), meta.contentType());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Read and display the content of a file from storage")
    public String readFile(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file in the bucket") String fileKey) {
        try {
            byte[] content = storageAdapter.read(bucketName, fileKey);
            if (content.length > 4096) {
                return String.format("File %s/%s is %s. Showing first 4KB:%n%s%n... (truncated)",
                        bucketName, fileKey, formatBytes(content.length),
                        new String(content, 0, 4096));
            }
            return new String(content);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Delete a file from a storage bucket")
    public String deleteFile(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file to delete") String fileKey) {
        try {
            storageAdapter.delete(bucketName, fileKey);
            return String.format("Deleted %s/%s", bucketName, fileKey);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
}
