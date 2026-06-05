package io.agenttoolbox.core.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    @Test
    void loadsConfigFromInputStream() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("test-config.yaml");
        AgentConfig config = ConfigLoader.load(is);
        assertThat(config.getAgent().getName()).isEqualTo("Test Agent");
        assertThat(config.getAgent().getVersion()).isEqualTo("0.0.1");
        assertThat(config.getAgent().getMemory().getMaxMessages()).isEqualTo(10);
    }

    @Test
    void loadsLlmConfig() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("test-config.yaml");
        AgentConfig config = ConfigLoader.load(is);
        assertThat(config.getLlm().getProvider()).isEqualTo("ollama");
        assertThat(config.getLlm().getOllama().getBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(config.getLlm().getOllama().getModel()).isEqualTo("llama3.1:8b");
        assertThat(config.getLlm().getOllama().getTemperature()).isEqualTo(0.1);
        assertThat(config.getLlm().getOllama().getTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void loadsStorageConfig() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("test-config.yaml");
        AgentConfig config = ConfigLoader.load(is);
        assertThat(config.getStorage().getProvider()).isEqualTo("local");
        assertThat(config.getStorage().getLocal().getBucketRoot()).isEqualTo("/tmp/test-buckets");
    }
}
