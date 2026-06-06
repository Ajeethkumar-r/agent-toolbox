package io.agenttoolbox.core.config;

import java.nio.file.Path;

/**
 * Root configuration POJO loaded from application.yaml.
 * Uses nested static classes so SnakeYAML can map the YAML structure directly.
 */
public class AgentConfig {

    private AgentSection agent = new AgentSection();
    private LlmSection llm = new LlmSection();
    private StorageSection storage = new StorageSection();
    private CacheSection cache = new CacheSection();
    private OutputSection output = new OutputSection();
    private LoggingSection logging = new LoggingSection();
    private EmbeddingSection embedding = new EmbeddingSection();

    public AgentSection getAgent() { return agent; }
    public void setAgent(AgentSection agent) { this.agent = agent; }

    public LlmSection getLlm() { return llm; }
    public void setLlm(LlmSection llm) { this.llm = llm; }

    public StorageSection getStorage() { return storage; }
    public void setStorage(StorageSection storage) { this.storage = storage; }

    public CacheSection getCache() { return cache; }
    public void setCache(CacheSection cache) { this.cache = cache; }

    public OutputSection getOutput() { return output; }
    public void setOutput(OutputSection output) { this.output = output; }

    public LoggingSection getLogging() { return logging; }
    public void setLogging(LoggingSection logging) { this.logging = logging; }

    public EmbeddingSection getEmbedding() { return embedding; }
    public void setEmbedding(EmbeddingSection embedding) { this.embedding = embedding; }

    // ── Agent section ───────────────────────────────────────────────────
    public static class AgentSection {
        private String name = "Agent Toolbox";
        private String version = "0.1.0";
        private MemoryConfig memory = new MemoryConfig();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public MemoryConfig getMemory() { return memory; }
        public void setMemory(MemoryConfig memory) { this.memory = memory; }
    }

    public static class MemoryConfig {
        private int maxTokens = 4000;
        private String storagePath;

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

        public String getStoragePath() {
            if (storagePath != null) return storagePath;
            return Path.of(System.getProperty("user.home"), ".agent-toolbox").toString();
        }
        public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    }

    // ── LLM section ─────────────────────────────────────────────────────
    public static class LlmSection {
        private String provider = "ollama";
        private OllamaConfig ollama = new OllamaConfig();
        private GeminiConfig gemini = new GeminiConfig();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public OllamaConfig getOllama() { return ollama; }
        public void setOllama(OllamaConfig ollama) { this.ollama = ollama; }

        public GeminiConfig getGemini() { return gemini; }
        public void setGemini(GeminiConfig gemini) { this.gemini = gemini; }
    }

    public static class OllamaConfig {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.1:8b";
        private double temperature = 0.3;
        private int timeoutSeconds = 60;
        private int healthCheckTimeoutSeconds = 5;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getHealthCheckTimeoutSeconds() { return healthCheckTimeoutSeconds; }
        public void setHealthCheckTimeoutSeconds(int healthCheckTimeoutSeconds) { this.healthCheckTimeoutSeconds = healthCheckTimeoutSeconds; }
    }

    public static class GeminiConfig {
        private String model = "gemini-2.0-flash";
        private double temperature = 0.3;
        private int timeoutSeconds = 60;
        private int maxOutputTokens = 4096;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    }

    // ── Storage section ─────────────────────────────────────────────────
    public static class StorageSection {
        private String provider = "local";
        private LocalStorageConfig local = new LocalStorageConfig();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public LocalStorageConfig getLocal() { return local; }
        public void setLocal(LocalStorageConfig local) { this.local = local; }
    }

    public static class LocalStorageConfig {
        private String bucketRoot;
        private int fileReadLimitBytes = 4096;

        public int getFileReadLimitBytes() { return fileReadLimitBytes; }
        public void setFileReadLimitBytes(int fileReadLimitBytes) { this.fileReadLimitBytes = fileReadLimitBytes; }

        public String getBucketRoot() {
            // Priority: explicit config > env var > default
            if (bucketRoot != null) return bucketRoot;
            String envRoot = System.getenv("AGENT_BUCKET_ROOT");
            if (envRoot != null) return envRoot;
            return Path.of(System.getProperty("user.home"), ".agent-toolbox", "buckets").toString();
        }

        public void setBucketRoot(String bucketRoot) { this.bucketRoot = bucketRoot; }
    }

    // ── Cache section ───────────────────────────────────────────────────
    public static class CacheSection {
        private boolean enabled = true;
        private int ttlSeconds = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }

    // ── Output section ──────────────────────────────────────────────────
    public static class OutputSection {
        private String verbosity = "auto";
        private boolean color = true;

        public String getVerbosity() { return verbosity; }
        public void setVerbosity(String verbosity) { this.verbosity = verbosity; }

        public boolean isColor() { return color; }
        public void setColor(boolean color) { this.color = color; }
    }

    // ── Logging section ─────────────────────────────────────────────────
    public static class LoggingSection {
        private String level = "INFO";

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
    }

    // ── Embedding section ──────────────────────────────────────────────────────
    public static class EmbeddingSection {
        private String provider = "ollama";
        private OllamaEmbeddingConfig ollama = new OllamaEmbeddingConfig();
        private String storePath;
        private int chunkSize = 500;
        private int chunkOverlap = 50;
        private int maxResults = 3;
        private double minScore = 0.5;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public OllamaEmbeddingConfig getOllama() { return ollama; }
        public void setOllama(OllamaEmbeddingConfig ollama) { this.ollama = ollama; }

        public String getStorePath() {
            if (storePath != null) return storePath;
            return Path.of(System.getProperty("user.home"), ".agent-toolbox", "embeddings").toString();
        }
        public void setStorePath(String storePath) { this.storePath = storePath; }

        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
    }

    public static class OllamaEmbeddingConfig {
        private String model = "nomic-embed-text";

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}
