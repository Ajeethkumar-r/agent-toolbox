package io.agenttoolbox.tool.etag;

import io.agenttoolbox.core.ToolProvider;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.tool.etag.storage.LocalStorageAdapter;

public class BrowserToolProvider implements ToolProvider {

    private String bucketRoot;

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
    }

    @Override
    public Object toolInstance() {
        if (bucketRoot == null) {
            bucketRoot = new AgentConfig().getStorage().getLocal().getBucketRoot();
        }
        return new BrowserTools(new LocalStorageAdapter(bucketRoot));
    }
}
