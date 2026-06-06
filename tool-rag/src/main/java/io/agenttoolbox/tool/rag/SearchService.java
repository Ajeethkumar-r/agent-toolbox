package io.agenttoolbox.tool.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.agenttoolbox.common.exception.BucketNotIndexedException;

import java.util.List;

/**
 * Queries the embedding store and formats results for the LLM.
 */
public class SearchService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStoreManager storeManager;
    private final int maxResults;
    private final double minScore;

    public SearchService(EmbeddingModel embeddingModel, EmbeddingStoreManager storeManager,
                         int maxResults, double minScore) {
        this.embeddingModel = embeddingModel;
        this.storeManager = storeManager;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    public String search(String query, String bucketName) {
        InMemoryEmbeddingStore<TextSegment> store = storeManager.getStore(bucketName);
        if (store == null) {
            throw new BucketNotIndexedException(bucketName);
        }

        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        EmbeddingSearchResult<TextSegment> result = store.search(request);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        if (matches.isEmpty()) {
            return String.format("No results found for \"%s\" in bucket \"%s\". Try a different search term or re-index the bucket.",
                    query, bucketName);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d result(s) for \"%s\" in bucket \"%s\":%n%n",
                matches.size(), query, bucketName));

        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            TextSegment segment = match.embedded();
            String source = segment.metadata().getString("source");
            sb.append(String.format("[%d] (score: %.2f) source: %s%n    \"%s\"%n%n",
                    i + 1, match.score(),
                    source != null ? source : "unknown",
                    segment.text()));
        }

        return sb.toString().stripTrailing();
    }
}
