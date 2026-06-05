package io.agenttoolbox.tool.etag;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.agenttoolbox.common.cache.ToolCache;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;

import java.util.List;

public class BrowserTools {

    private final StorageAdapter storageAdapter;
    private final ToolCache cache;
    private final int fileReadLimitBytes;

    public BrowserTools(StorageAdapter storageAdapter, ToolCache cache, int fileReadLimitBytes) {
        this.storageAdapter = storageAdapter;
        this.cache = cache;
        this.fileReadLimitBytes = fileReadLimitBytes;
    }

    @Tool("List all available storage buckets")
    public String listBuckets() {
        try {
            String cacheKey = "buckets";
            String cached = cache.get(cacheKey);
            if (cached != null) {
                progress("[cached] Listing buckets...");
                return cached;
            }

            progress("Listing buckets...");
            List<String> buckets = storageAdapter.listBuckets();
            if (buckets.isEmpty()) {
                return "No buckets found.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %d bucket(s):%n", buckets.size()));
            for (String bucket : buckets) {
                sb.append(String.format("  - %s%n", bucket));
            }
            String result = sb.toString().stripTrailing();
            cache.put(cacheKey, result);
            return result;
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
            String cacheKey = ToolCache.key(bucketName, "files", prefix);
            String cached = cache.get(cacheKey);
            if (cached != null) {
                progress("[cached] Listing files in %s...", bucketName);
                return cached;
            }

            progress("Listing files in %s%s...", bucketName, prefix.isEmpty() ? "" : " (prefix: " + prefix + ")");
            List<FileMetadata> files = storageAdapter.list(bucketName, prefix);
            if (files.isEmpty()) {
                return "No files found in " + bucketName;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %d file(s):%n", files.size()));
            for (FileMetadata meta : files) {
                sb.append(String.format("  %-40s %8s  ETag: %s%n",
                        meta.key(), formatBytes(meta.size()), meta.etag()));
            }
            String result = sb.toString().stripTrailing();
            cache.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Get detailed metadata for a file including ETag, MD5, size, and last modified time")
    public String getFileInfo(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file in the bucket") String fileKey) {
        try {
            String cacheKey = ToolCache.key(bucketName, "info", fileKey);
            String cached = cache.get(cacheKey);
            if (cached != null) {
                progress("[cached] Fetching metadata for %s/%s...", bucketName, fileKey);
                return cached;
            }

            progress("Fetching metadata for %s/%s...", bucketName, fileKey);
            FileMetadata meta = storageAdapter.getMetadata(bucketName, fileKey);
            String result = String.format(
                    "File: %s/%s%n  Size:          %s (%d bytes)%n  MD5:           %s%n  ETag:          %s%n  Last Modified: %s%n  Content Type:  %s",
                    meta.bucket(), meta.key(),
                    formatBytes(meta.size()), meta.size(),
                    meta.md5Hash(), meta.etag(),
                    meta.lastModified(), meta.contentType());
            cache.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Read and display the content of a file from storage")
    public String readFile(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file in the bucket") String fileKey) {
        try {
            String cacheKey = ToolCache.key(bucketName, "read", fileKey);
            String cached = cache.get(cacheKey);
            if (cached != null) {
                progress("[cached] Reading %s/%s...", bucketName, fileKey);
                return cached;
            }

            FileMetadata meta = storageAdapter.getMetadata(bucketName, fileKey);
            progress("Reading %s/%s (%s)...", bucketName, fileKey, formatBytes(meta.size()));
            byte[] content = storageAdapter.read(bucketName, fileKey);
            String result;
            if (content.length > fileReadLimitBytes) {
                result = String.format("Showing first %s of %s:%n%s%n... (truncated)",
                        formatBytes(fileReadLimitBytes), formatBytes(content.length),
                        new String(content, 0, fileReadLimitBytes));
            } else {
                result = new String(content);
            }
            cache.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Delete a file from a storage bucket")
    public String deleteFile(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file to delete") String fileKey) {
        try {
            progress("Deleting %s/%s...", bucketName, fileKey);
            storageAdapter.delete(bucketName, fileKey);
            cache.invalidate(bucketName);
            return String.format("SUCCESS: file %s/%s has been deleted.", bucketName, fileKey);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private static void progress(String format, Object... args) {
        System.out.printf("  >> " + format + "%n", args);
        System.out.flush();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
}
