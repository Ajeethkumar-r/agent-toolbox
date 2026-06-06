package io.agenttoolbox.tool.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionServiceTest {

    private StorageAdapter storageAdapter;
    private EmbeddingModel embeddingModel;
    private EmbeddingStoreManager storeManager;
    private IngestionService service;

    @TempDir java.nio.file.Path tempDir;

    @BeforeEach
    void setUp() {
        storageAdapter = mock(StorageAdapter.class);
        embeddingModel = mock(EmbeddingModel.class);
        storeManager = new EmbeddingStoreManager(tempDir);
        service = new IngestionService(storageAdapter, embeddingModel, storeManager, 200, 20);
    }

    @Test
    void ingestsTextFilesFromBucket() {
        FileMetadata meta = new FileMetadata("readme.txt", "docs", "md5", "etag", 13, Instant.now(), "text/plain");
        when(storageAdapter.list("docs", "")).thenReturn(List.of(meta));
        when(storageAdapter.read("docs", "readme.txt")).thenReturn("Hello world content".getBytes(StandardCharsets.UTF_8));
        when(embeddingModel.embedAll(anyList())).thenReturn(
                Response.from(List.of(Embedding.from(new float[]{0.1f, 0.2f, 0.3f}))));

        String result = service.ingest("docs");

        assertThat(result).contains("Indexed");
        assertThat(result).contains("1 file");
        assertThat(storeManager.getStore("docs")).isNotNull();
    }

    @Test
    void skipsUnparseableFiles() {
        FileMetadata meta1 = new FileMetadata("good.txt", "bucket", "m", "e", 5, Instant.now(), "text/plain");
        FileMetadata meta2 = new FileMetadata("bad.bin", "bucket", "m", "e", 5, Instant.now(), "application/octet-stream");

        when(storageAdapter.list("bucket", "")).thenReturn(List.of(meta1, meta2));
        when(storageAdapter.read("bucket", "good.txt")).thenReturn("good content".getBytes(StandardCharsets.UTF_8));
        when(storageAdapter.read("bucket", "bad.bin")).thenReturn(new byte[]{0, 1, 2, 3, 4});
        when(embeddingModel.embedAll(anyList())).thenReturn(
                Response.from(List.of(Embedding.from(new float[]{0.1f}))));

        String result = service.ingest("bucket");

        assertThat(result).contains("1 file");
    }

    @Test
    void returnsMessageForEmptyBucket() {
        when(storageAdapter.list("empty", "")).thenReturn(List.of());

        String result = service.ingest("empty");

        assertThat(result).containsIgnoringCase("no files");
    }
}
