package io.agenttoolbox.tool.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads files from a bucket, parses, chunks, embeds, and stores vectors.
 */
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final StorageAdapter storageAdapter;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStoreManager storeManager;
    private final int chunkSize;
    private final int chunkOverlap;

    public IngestionService(StorageAdapter storageAdapter, EmbeddingModel embeddingModel,
                            EmbeddingStoreManager storeManager, int chunkSize, int chunkOverlap) {
        this.storageAdapter = storageAdapter;
        this.embeddingModel = embeddingModel;
        this.storeManager = storeManager;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public String ingest(String bucketName) {
        List<FileMetadata> files = storageAdapter.list(bucketName, "");
        if (files.isEmpty()) {
            return "No files found in bucket '" + bucketName + "'.";
        }

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        int filesIndexed = 0;
        int totalChunks = 0;
        int skipped = 0;

        for (FileMetadata fileMeta : files) {
            try {
                byte[] content = storageAdapter.read(bucketName, fileMeta.key());
                DocumentParser parser = DocumentParserFactory.forFile(fileMeta.key());
                Document document = parser.parse(new ByteArrayInputStream(content));

                List<TextSegment> segments = DocumentSplitters
                        .recursive(chunkSize, chunkOverlap)
                        .split(document);

                // Add source metadata to each segment
                List<TextSegment> enrichedSegments = new ArrayList<>();
                for (TextSegment segment : segments) {
                    Metadata metadata = segment.metadata().copy();
                    metadata.put("source", fileMeta.key());
                    metadata.put("bucket", bucketName);
                    enrichedSegments.add(TextSegment.from(segment.text(), metadata));
                }

                progress("Indexing %s (%d chunks)...", fileMeta.key(), enrichedSegments.size());

                List<Embedding> embeddings = embeddingModel.embedAll(enrichedSegments).content();
                for (int i = 0; i < enrichedSegments.size(); i++) {
                    store.add(embeddings.get(i), enrichedSegments.get(i));
                }

                filesIndexed++;
                totalChunks += enrichedSegments.size();
            } catch (Exception e) {
                log.warn("Skipping file '{}': {}", fileMeta.key(), e.getMessage());
                skipped++;
            }
        }

        storeManager.saveStore(bucketName, store);

        String result = String.format("Indexed %d file(s) (%d chunks) in bucket '%s'.",
                filesIndexed, totalChunks, bucketName);
        if (skipped > 0) {
            result += String.format(" %d file(s) skipped (unsupported format).", skipped);
        }
        return result;
    }

    private static void progress(String format, Object... args) {
        System.out.printf("  >> " + format + "%n", args);
        System.out.flush();
    }
}
