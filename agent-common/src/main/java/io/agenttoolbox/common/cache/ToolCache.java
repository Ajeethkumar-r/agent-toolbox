package io.agenttoolbox.common.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory cache for tool results with TTL-based expiration.
 * Thread-safe. Write operations should call invalidate() to clear stale entries.
 */
public class ToolCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Duration ttl;

    public ToolCache(Duration ttl) {
        this.ttl = ttl;
    }

    public String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (entry.isExpired(ttl)) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    public void put(String key, String value) {
        cache.put(key, new CacheEntry(value, Instant.now()));
    }

    /** Invalidate all entries matching a bucket prefix. */
    public void invalidate(String bucket) {
        cache.entrySet().removeIf(e -> e.getKey().startsWith(bucket + ":") || e.getKey().equals("buckets"));
    }

    /** Invalidate a specific key. */
    public void invalidateKey(String key) {
        cache.remove(key);
    }

    /** Clear all cached entries. */
    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    /** Build a cache key from parts. */
    public static String key(String... parts) {
        return String.join(":", parts);
    }

    private record CacheEntry(String value, Instant created) {
        boolean isExpired(Duration ttl) {
            return Instant.now().isAfter(created.plus(ttl));
        }
    }
}
