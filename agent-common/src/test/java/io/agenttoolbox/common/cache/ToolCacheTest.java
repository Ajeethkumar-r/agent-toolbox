package io.agenttoolbox.common.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCacheTest {

    @Test
    void getReturnsNullForMissingKey() {
        ToolCache cache = new ToolCache(Duration.ofSeconds(30));
        assertThat(cache.get("missing")).isNull();
    }

    @Test
    void putAndGetReturnsValue() {
        ToolCache cache = new ToolCache(Duration.ofSeconds(30));
        cache.put("bucket:files:test", "result data");
        assertThat(cache.get("bucket:files:test")).isEqualTo("result data");
    }

    @Test
    void expiredEntryReturnsNull() throws InterruptedException {
        ToolCache cache = new ToolCache(Duration.ofMillis(50));
        cache.put("key", "value");
        Thread.sleep(100);
        assertThat(cache.get("key")).isNull();
    }

    @Test
    void invalidateBucketRemovesMatchingEntries() {
        ToolCache cache = new ToolCache(Duration.ofSeconds(30));
        cache.put("my-bucket:files:", "file list");
        cache.put("my-bucket:info:a.txt", "file info");
        cache.put("other-bucket:files:", "other list");
        cache.put("buckets", "bucket list");

        cache.invalidate("my-bucket");

        assertThat(cache.get("my-bucket:files:")).isNull();
        assertThat(cache.get("my-bucket:info:a.txt")).isNull();
        assertThat(cache.get("other-bucket:files:")).isNotNull();
        assertThat(cache.get("buckets")).isNull(); // bucket list also invalidated
    }

    @Test
    void invalidateKeyRemovesSingleEntry() {
        ToolCache cache = new ToolCache(Duration.ofSeconds(30));
        cache.put("a", "1");
        cache.put("b", "2");
        cache.invalidateKey("a");
        assertThat(cache.get("a")).isNull();
        assertThat(cache.get("b")).isEqualTo("2");
    }

    @Test
    void clearRemovesEverything() {
        ToolCache cache = new ToolCache(Duration.ofSeconds(30));
        cache.put("a", "1");
        cache.put("b", "2");
        cache.clear();
        assertThat(cache.size()).isZero();
    }

    @Test
    void keyBuilderJoinsParts() {
        assertThat(ToolCache.key("bucket", "files", "prefix")).isEqualTo("bucket:files:prefix");
    }
}
