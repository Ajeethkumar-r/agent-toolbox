package io.agenttoolbox.tool.etag;

import io.agenttoolbox.core.ToolProvider;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.tool.etag.service.CacheValidationService;
import io.agenttoolbox.tool.etag.service.ConcurrencyControlService;
import io.agenttoolbox.tool.etag.service.DeltaSyncService;
import io.agenttoolbox.tool.etag.service.UploadValidationService;
import io.agenttoolbox.tool.etag.storage.LocalStorageAdapter;

import java.nio.file.Path;

public class EtagToolProvider implements ToolProvider {

    private String bucketRoot;

    @Override
    public String name() {
        return "etag";
    }

    @Override
    public String description() {
        return "ETag/MD5 tools for delta sync, upload validation, concurrency control, and cache validation";
    }

    @Override
    public void configure(AgentConfig config) {
        String configRoot = config.getStorage().getLocal().getBucketRoot();
        // Resolve ${user.home} placeholder if present
        this.bucketRoot = configRoot.replace("${user.home}", System.getProperty("user.home"));
    }

    @Override
    public Object toolInstance() {
        if (bucketRoot == null) {
            bucketRoot = Path.of(System.getProperty("user.home"), ".agent-toolbox", "buckets").toString();
        }
        LocalStorageAdapter storageAdapter = new LocalStorageAdapter(bucketRoot);

        DeltaSyncService deltaSyncService = new DeltaSyncService(storageAdapter);
        UploadValidationService uploadValidationService = new UploadValidationService(storageAdapter);
        ConcurrencyControlService concurrencyControlService = new ConcurrencyControlService(storageAdapter);
        CacheValidationService cacheValidationService = new CacheValidationService(storageAdapter);

        return new EtagTools(deltaSyncService, uploadValidationService, concurrencyControlService, cacheValidationService);
    }
}
