package io.agenttoolbox.tool.rag;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.agenttoolbox.common.error.ToolErrorFormatter;
import io.agenttoolbox.common.storage.StorageAdapter;

import java.io.ByteArrayInputStream;
import java.util.List;

public class RagTools {

    private final IngestionService ingestionService;
    private final SearchService searchService;
    private final StorageAdapter storageAdapter;
    private final EmbeddingModel embeddingModel;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int maxResults;
    private final double minScore;

    public RagTools(IngestionService ingestionService, SearchService searchService,
                    StorageAdapter storageAdapter, EmbeddingModel embeddingModel,
                    int chunkSize, int chunkOverlap, int maxResults, double minScore) {
        this.ingestionService = ingestionService;
        this.searchService = searchService;
        this.storageAdapter = storageAdapter;
        this.embeddingModel = embeddingModel;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    @Tool("Index all files in a storage bucket for semantic search. Must be called before searchContent.")
    public String ingestBucket(
            @P("Name of the storage bucket to index") String bucketName) {
        try {
            progress("Ingesting bucket '%s'...", bucketName);
            return ingestionService.ingest(bucketName);
        } catch (Exception e) {
            return ToolErrorFormatter.format(e);
        }
    }

    @Tool("Search indexed bucket contents by meaning. Call ingestBucket first if not already indexed.")
    public String searchContent(
            @P("The search query describing what you're looking for") String query,
            @P("Name of the storage bucket to search") String bucketName) {
        try {
            progress("Searching '%s' in bucket '%s'...", query, bucketName);
            return searchService.search(query, bucketName);
        } catch (Exception e) {
            return ToolErrorFormatter.format(e);
        }
    }

    @Tool("Answer a question about a specific file's content without requiring prior indexing")
    public String askAboutFile(
            @P("Name of the storage bucket") String bucketName,
            @P("Key (path) of the file in the bucket") String fileKey,
            @P("The question to answer about the file") String question) {
        try {
            progress("Reading %s/%s...", bucketName, fileKey);
            byte[] content = storageAdapter.read(bucketName, fileKey);
            DocumentParser parser = DocumentParserFactory.forFile(fileKey);
            Document document = parser.parse(new ByteArrayInputStream(content));

            List<TextSegment> segments = DocumentSplitters
                    .recursive(chunkSize, chunkOverlap)
                    .split(document);

            if (segments.isEmpty()) {
                return "File is empty or could not be parsed: " + fileKey;
            }

            progress("Embedding %d chunks from %s...", segments.size(), fileKey);
            List<Embedding> chunkEmbeddings = embeddingModel.embedAll(segments).content();
            Embedding queryEmbedding = embeddingModel.embed(question).content();

            // Use a temporary in-memory store for one-shot search
            InMemoryEmbeddingStore<TextSegment> tempStore = new InMemoryEmbeddingStore<>();
            for (int i = 0; i < segments.size(); i++) {
                tempStore.add(chunkEmbeddings.get(i), segments.get(i));
            }

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            EmbeddingSearchResult<TextSegment> result = tempStore.search(request);
            List<EmbeddingMatch<TextSegment>> matches = result.matches();

            if (matches.isEmpty()) {
                return String.format("No relevant sections found in %s/%s for \"%s\".", bucketName, fileKey, question);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Most relevant sections from %s/%s for \"%s\":%n%n",
                    bucketName, fileKey, question));
            for (int i = 0; i < matches.size(); i++) {
                EmbeddingMatch<TextSegment> match = matches.get(i);
                sb.append(String.format("[%d] (score: %.2f)%n    \"%s\"%n%n",
                        i + 1, match.score(), match.embedded().text()));
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            return ToolErrorFormatter.format(e);
        }
    }

    private static void progress(String format, Object... args) {
        System.out.printf("  >> " + format + "%n", args);
        System.out.flush();
    }
}
