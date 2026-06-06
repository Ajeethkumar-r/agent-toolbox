package io.agenttoolbox.tool.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingStoreManagerTest {

    @TempDir Path storePath;
    EmbeddingStoreManager manager;

    @BeforeEach
    void setUp() {
        manager = new EmbeddingStoreManager(storePath);
    }

    @Test
    void returnsNullForNonIndexedBucket() {
        assertThat(manager.getStore("unknown-bucket")).isNull();
    }

    @Test
    void savesAndLoadsStore() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.add(Embedding.from(new float[]{0.1f, 0.2f, 0.3f}),
                TextSegment.from("test chunk", new Metadata().put("source", "file.txt")));

        manager.saveStore("my-bucket", store);

        // Clear in-memory cache and reload from disk
        EmbeddingStoreManager manager2 = new EmbeddingStoreManager(storePath);
        InMemoryEmbeddingStore<TextSegment> loaded = manager2.getStore("my-bucket");
        assertThat(loaded).isNotNull();
    }

    @Test
    void createsJsonFileOnDisk() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.add(Embedding.from(new float[]{0.1f, 0.2f}), TextSegment.from("hello"));

        manager.saveStore("docs", store);

        Path file = storePath.resolve("docs.json");
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void replacesExistingStore() {
        InMemoryEmbeddingStore<TextSegment> store1 = new InMemoryEmbeddingStore<>();
        store1.add(Embedding.from(new float[]{0.1f}), TextSegment.from("old"));
        manager.saveStore("bucket", store1);

        InMemoryEmbeddingStore<TextSegment> store2 = new InMemoryEmbeddingStore<>();
        store2.add(Embedding.from(new float[]{0.2f}), TextSegment.from("new1"));
        store2.add(Embedding.from(new float[]{0.3f}), TextSegment.from("new2"));
        manager.saveStore("bucket", store2);

        InMemoryEmbeddingStore<TextSegment> loaded = manager.getStore("bucket");
        assertThat(loaded).isNotNull();
    }

    @Test
    void cachesStoreInMemory() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.add(Embedding.from(new float[]{0.5f}), TextSegment.from("cached"));
        manager.saveStore("cached-bucket", store);

        // Second call returns from cache (same instance)
        InMemoryEmbeddingStore<TextSegment> first = manager.getStore("cached-bucket");
        InMemoryEmbeddingStore<TextSegment> second = manager.getStore("cached-bucket");
        assertThat(first).isSameAs(second);
    }
}
