package io.agenttoolbox.tool.etag;

import io.agenttoolbox.common.cache.ToolCache;
import io.agenttoolbox.core.ToolProvider;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.tool.etag.service.CacheValidationService;
import io.agenttoolbox.tool.etag.service.ConcurrencyControlService;
import io.agenttoolbox.tool.etag.service.DeltaSyncService;
import io.agenttoolbox.tool.etag.service.UploadValidationService;
import io.agenttoolbox.tool.etag.storage.LocalStorageAdapter;

import java.time.Duration;

public class EtagToolProvider implements ToolProvider {

    private String bucketRoot;
    private ToolCache cache;

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
        this.bucketRoot = config.getStorage().getLocal().getBucketRoot();
        this.cache = SharedCache.get(config);
    }

    @Override
    public Object toolInstance() {
        if (bucketRoot == null) {
            bucketRoot = new AgentConfig().getStorage().getLocal().getBucketRoot();
        }
        if (cache == null) {
            cache = new ToolCache(Duration.ofSeconds(30));
        }
        LocalStorageAdapter storageAdapter = new LocalStorageAdapter(bucketRoot);

        return new EtagTools(
                new DeltaSyncService(storageAdapter),
                new UploadValidationService(storageAdapter),
                new ConcurrencyControlService(storageAdapter),
                new CacheValidationService(storageAdapter),
                cache);
    }
}
