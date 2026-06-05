package io.agenttoolbox.tool.etag;

import io.agenttoolbox.common.cache.ToolCache;
import io.agenttoolbox.core.ToolProvider;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.tool.etag.storage.LocalStorageAdapter;

import java.time.Duration;

public class BrowserToolProvider implements ToolProvider {

    private String bucketRoot;
    private ToolCache cache;
    private int fileReadLimitBytes = 4096;

    @Override
    public String name() {
        return "browser";
    }

    @Override
    public String description() {
        return "File browser tools for listing buckets, listing files, reading files, getting file info, and deleting files";
    }

    @Override
    public void configure(AgentConfig config) {
        this.bucketRoot = config.getStorage().getLocal().getBucketRoot();
        this.fileReadLimitBytes = config.getStorage().getLocal().getFileReadLimitBytes();
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
        return new BrowserTools(new LocalStorageAdapter(bucketRoot), cache, fileReadLimitBytes);
    }
}
