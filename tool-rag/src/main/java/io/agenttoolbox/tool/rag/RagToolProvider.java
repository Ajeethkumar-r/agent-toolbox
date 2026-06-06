package io.agenttoolbox.tool.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import io.agenttoolbox.common.storage.StorageAdapter;
import io.agenttoolbox.core.ToolProvider;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.tool.etag.storage.LocalStorageAdapter;

import java.nio.file.Path;
import java.time.Duration;

public class RagToolProvider implements ToolProvider {

    private AgentConfig config;

    @Override
    public String name() {
        return "rag";
    }

    @Override
    public String description() {
        return "RAG tools for semantic search over bucket contents (text, PDF, DOCX)";
    }

    @Override
    public void configure(AgentConfig config) {
        this.config = config;
    }

    @Override
    public Object toolInstance() {
        AgentConfig.EmbeddingSection embCfg = config.getEmbedding();
        AgentConfig.OllamaConfig ollamaCfg = config.getLlm().getOllama();

        EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(ollamaCfg.getBaseUrl())
                .modelName(embCfg.getOllama().getModel())
                .timeout(Duration.ofSeconds(ollamaCfg.getTimeoutSeconds()))
                .build();

        StorageAdapter storageAdapter = new LocalStorageAdapter(
                config.getStorage().getLocal().getBucketRoot());

        EmbeddingStoreManager storeManager = new EmbeddingStoreManager(
                Path.of(embCfg.getStorePath()));

        IngestionService ingestionService = new IngestionService(
                storageAdapter, embeddingModel, storeManager,
                embCfg.getChunkSize(), embCfg.getChunkOverlap());

        SearchService searchService = new SearchService(
                embeddingModel, storeManager,
                embCfg.getMaxResults(), embCfg.getMinScore());

        return new RagTools(ingestionService, searchService,
                storageAdapter, embeddingModel,
                embCfg.getChunkSize(), embCfg.getChunkOverlap(),
                embCfg.getMaxResults(), embCfg.getMinScore());
    }
}
