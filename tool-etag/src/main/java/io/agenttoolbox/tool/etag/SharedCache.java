package io.agenttoolbox.tool.etag;

import io.agenttoolbox.common.cache.ToolCache;
import io.agenttoolbox.core.config.AgentConfig;

import java.time.Duration;

/**
 * Shared singleton cache instance for all tool providers in this module.
 * Both EtagToolProvider and BrowserToolProvider share the same cache
 * so write invalidations from EtagTools are visible to BrowserTools.
 */
final class SharedCache {

    private static volatile ToolCache instance;

    private SharedCache() {}

    static ToolCache get(AgentConfig config) {
        if (instance == null) {
            synchronized (SharedCache.class) {
                if (instance == null) {
                    int ttl = config.getCache().isEnabled() ? config.getCache().getTtlSeconds() : 0;
                    instance = new ToolCache(Duration.ofSeconds(ttl));
                }
            }
        }
        return instance;
    }
}
