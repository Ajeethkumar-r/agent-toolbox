package io.agenttoolbox.tool.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-bucket InMemoryEmbeddingStore instances with JSON file persistence.
 */
public class EmbeddingStoreManager {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStoreManager.class);

    private final Path storePath;
    private final Map<String, InMemoryEmbeddingStore<TextSegment>> stores = new ConcurrentHashMap<>();

    public EmbeddingStoreManager(Path storePath) {
        this.storePath = storePath;
        try {
            Files.createDirectories(storePath);
        } catch (IOException e) {
            log.warn("Could not create embedding store directory: {}", storePath, e);
        }
    }

    /**
     * Get the embedding store for a bucket. Loads from disk if not in memory.
     * Returns null if the bucket has never been indexed.
     */
    public InMemoryEmbeddingStore<TextSegment> getStore(String bucketName) {
        return stores.computeIfAbsent(bucketName, name -> {
            Path file = resolveFile(name);
            if (!Files.exists(file)) {
                return null;
            }
            try {
                log.info("Loading embedding store for bucket '{}' from {}", name, file);
                return InMemoryEmbeddingStore.fromFile(file);
            } catch (Exception e) {
                log.warn("Failed to load embedding store for bucket '{}': {}", name, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Save (or replace) the embedding store for a bucket and persist to disk.
     */
    public void saveStore(String bucketName, InMemoryEmbeddingStore<TextSegment> store) {
        stores.put(bucketName, store);
        Path file = resolveFile(bucketName);
        try {
            store.serializeToFile(file);
            log.info("Persisted embedding store for bucket '{}' to {}", bucketName, file);
        } catch (Exception e) {
            log.error("Failed to persist embedding store for bucket '{}': {}", bucketName, e.getMessage());
        }
    }

    private Path resolveFile(String bucketName) {
        return storePath.resolve(bucketName + ".json");
    }
}
