package io.agenttoolbox.core.config;

/**
 * Root configuration POJO loaded from application.yaml.
 * Uses nested static classes so SnakeYAML can map the YAML structure directly.
 */
public class AgentConfig {

    private AgentSection agent = new AgentSection();
    private LlmSection llm = new LlmSection();
    private StorageSection storage = new StorageSection();
    private OutputSection output = new OutputSection();
    private LoggingSection logging = new LoggingSection();

    public AgentSection getAgent() { return agent; }
    public void setAgent(AgentSection agent) { this.agent = agent; }

    public LlmSection getLlm() { return llm; }
    public void setLlm(LlmSection llm) { this.llm = llm; }

    public StorageSection getStorage() { return storage; }
    public void setStorage(StorageSection storage) { this.storage = storage; }

    public OutputSection getOutput() { return output; }
    public void setOutput(OutputSection output) { this.output = output; }

    public LoggingSection getLogging() { return logging; }
    public void setLogging(LoggingSection logging) { this.logging = logging; }

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
        private int maxMessages = 20;

        public int getMaxMessages() { return maxMessages; }
        public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }
    }

    // ── LLM section ─────────────────────────────────────────────────────
    public static class LlmSection {
        private String provider = "ollama";
        private OllamaConfig ollama = new OllamaConfig();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public OllamaConfig getOllama() { return ollama; }
        public void setOllama(OllamaConfig ollama) { this.ollama = ollama; }
    }

    public static class OllamaConfig {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.1:8b";
        private double temperature = 0.1;
        private int timeoutSeconds = 60;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
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
        private String bucketRoot = "/tmp/agent-buckets";

        public String getBucketRoot() { return bucketRoot; }
        public void setBucketRoot(String bucketRoot) { this.bucketRoot = bucketRoot; }
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
}
