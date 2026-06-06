package io.agenttoolbox.tool.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.agenttoolbox.common.exception.BucketNotIndexedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    private EmbeddingModel embeddingModel;
    private EmbeddingStoreManager storeManager;
    private SearchService service;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        storeManager = new EmbeddingStoreManager(tempDir);
        service = new SearchService(embeddingModel, storeManager, 3, 0.0);
    }

    @Test
    void throwsWhenBucketNotIndexed() {
        when(embeddingModel.embed(anyString())).thenReturn(
                Response.from(Embedding.from(new float[]{0.1f, 0.2f})));

        assertThatThrownBy(() -> service.search("query", "nonexistent"))
                .isInstanceOf(BucketNotIndexedException.class);
    }

    @Test
    void returnsFormattedResults() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.add(Embedding.from(new float[]{1.0f, 0.0f}),
                TextSegment.from("Authentication uses JWT tokens",
                        new Metadata().put("source", "auth.txt")));
        store.add(Embedding.from(new float[]{0.0f, 1.0f}),
                TextSegment.from("The weather is sunny",
                        new Metadata().put("source", "weather.txt")));
        storeManager.saveStore("docs", store);

        when(embeddingModel.embed(anyString())).thenReturn(
                Response.from(Embedding.from(new float[]{0.9f, 0.1f})));

        String result = service.search("how does auth work?", "docs");

        assertThat(result).contains("auth.txt");
        assertThat(result).contains("Authentication uses JWT tokens");
    }

    @Test
    void returnsNoResultsMessage() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        storeManager.saveStore("empty-indexed", store);

        when(embeddingModel.embed(anyString())).thenReturn(
                Response.from(Embedding.from(new float[]{0.5f})));

        String result = service.search("anything", "empty-indexed");

        assertThat(result).containsIgnoringCase("no results");
    }
}
