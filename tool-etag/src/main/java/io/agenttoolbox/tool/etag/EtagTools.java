package io.agenttoolbox.tool.etag;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.agenttoolbox.common.cache.ToolCache;
import io.agenttoolbox.tool.etag.service.CacheValidationService;
import io.agenttoolbox.tool.etag.service.ConcurrencyControlService;
import io.agenttoolbox.tool.etag.service.DeltaSyncService;
import io.agenttoolbox.tool.etag.service.UploadValidationService;

public class EtagTools {

    private final DeltaSyncService deltaSyncService;
    private final UploadValidationService uploadValidationService;
    private final ConcurrencyControlService concurrencyControlService;
    private final CacheValidationService cacheValidationService;
    private final ToolCache cache;

    public EtagTools(DeltaSyncService deltaSyncService,
                     UploadValidationService uploadValidationService,
                     ConcurrencyControlService concurrencyControlService,
                     CacheValidationService cacheValidationService,
                     ToolCache cache) {
        this.deltaSyncService = deltaSyncService;
        this.uploadValidationService = uploadValidationService;
        this.concurrencyControlService = concurrencyControlService;
        this.cacheValidationService = cacheValidationService;
        this.cache = cache;
    }

    @Tool("Sync a local directory to a storage bucket, uploading only changed files based on MD5 comparison")
    public String deltaSync(
            @P("Absolute path to the local directory to sync") String localPath,
            @P("Name of the target storage bucket") String bucketName) {
        try {
            progress("Syncing %s to %s...", localPath, bucketName);
            String result = deltaSyncService.sync(localPath, bucketName);
            cache.invalidate(bucketName);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Upload a file with MD5 validation to ensure data integrity")
    public String uploadWithValidation(
            @P("Absolute path to the local file to upload") String localFilePath,
            @P("Name of the target storage bucket") String bucketName,
            @P("Destination key (path) in the bucket") String destinationKey) {
        try {
            progress("Uploading %s to %s/%s...", localFilePath, bucketName,
                    (destinationKey == null || destinationKey.isBlank()) ? "(auto)" : destinationKey);
            String result = uploadValidationService.upload(localFilePath, bucketName, destinationKey);
            cache.invalidate(bucketName);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Update a file in storage only if the known ETag matches, preventing concurrent modification conflicts")
    public String conditionalUpdate(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file in the bucket") String fileKey,
            @P("Absolute path to the local file with updated content") String localFilePath,
            @P("The ETag value from the last known version of the file") String knownEtag) {
        try {
            progress("Updating %s/%s (ETag: %s)...", bucketName, fileKey, knownEtag);
            String result = concurrencyControlService.update(bucketName, fileKey, localFilePath, knownEtag);
            cache.invalidate(bucketName);
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Validate if a cached file is still current by comparing ETags, avoiding unnecessary data transfer")
    public String cacheValidation(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file in the bucket") String fileKey,
            @P("The ETag value of the cached version") String knownEtag) {
        try {
            progress("Checking %s/%s (ETag: %s)...", bucketName, fileKey, knownEtag);
            return cacheValidationService.validate(bucketName, fileKey, knownEtag);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private static void progress(String format, Object... args) {
        System.out.printf("  >> " + format + "%n", args);
        System.out.flush();
    }
}
