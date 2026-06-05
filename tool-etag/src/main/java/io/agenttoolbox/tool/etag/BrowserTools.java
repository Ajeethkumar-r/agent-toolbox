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
            StringBuilder sb = new StringBuilder("Listing buckets...\n");
            List<String> buckets = storageAdapter.listBuckets();
            if (buckets.isEmpty()) {
                sb.append("No buckets found.");
                return sb.toString();
            }
            sb.append(String.format("Found %d bucket(s):%n", buckets.size()));
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
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Listing files in %s%s...%n",
                    bucketName, prefix.isEmpty() ? "" : " (prefix: " + prefix + ")"));
            List<FileMetadata> files = storageAdapter.list(bucketName, prefix);
            if (files.isEmpty()) {
                sb.append("No files found.");
                return sb.toString();
            }
            sb.append(String.format("Found %d file(s):%n", files.size()));
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
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Fetching metadata for %s/%s...%n", bucketName, fileKey));
            FileMetadata meta = storageAdapter.getMetadata(bucketName, fileKey);
            sb.append(String.format("File: %s/%s%n", meta.bucket(), meta.key()));
            sb.append(String.format("  Size:          %s (%d bytes)%n", formatBytes(meta.size()), meta.size()));
            sb.append(String.format("  MD5:           %s%n", meta.md5Hash()));
            sb.append(String.format("  ETag:          %s%n", meta.etag()));
            sb.append(String.format("  Last Modified: %s%n", meta.lastModified()));
            sb.append(String.format("  Content Type:  %s", meta.contentType()));
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Read and display the content of a file from storage")
    public String readFile(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file in the bucket") String fileKey) {
        try {
            FileMetadata meta = storageAdapter.getMetadata(bucketName, fileKey);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Reading %s/%s (%s)...%n", bucketName, fileKey, formatBytes(meta.size())));
            byte[] content = storageAdapter.read(bucketName, fileKey);
            if (content.length > 4096) {
                sb.append(String.format("Showing first 4KB of %s:%n", formatBytes(content.length)));
                sb.append(new String(content, 0, 4096));
                sb.append("\n... (truncated)");
            } else {
                sb.append(new String(content));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Delete a file from a storage bucket")
    public String deleteFile(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file to delete") String fileKey) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Deleting %s/%s...%n", bucketName, fileKey));
            storageAdapter.delete(bucketName, fileKey);
            sb.append(String.format("Done. Deleted %s/%s", bucketName, fileKey));
            return sb.toString();
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
