package io.agenttoolbox.core.llm;

import io.agenttoolbox.common.config.SecretProvider;
import io.agenttoolbox.common.exception.LlmException;
import io.agenttoolbox.core.config.AgentConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelFactoryTest {

    @Test
    void create_throwsForUnknownProvider() {
        AgentConfig config = new AgentConfig();
        config.getLlm().setProvider("unknown");
        SecretProvider secrets = key -> Optional.empty();
        assertThatThrownBy(() -> ChatModelFactory.create(config, secrets))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Unknown LLM provider: unknown");
    }

    @Test
    void create_returnsNonNullForOllamaProvider() {
        AgentConfig config = new AgentConfig();
        config.getLlm().setProvider("ollama");
        config.getLlm().getOllama().setBaseUrl("http://localhost:11434");
        config.getLlm().getOllama().setModel("llama3.1:8b");
        SecretProvider secrets = key -> Optional.empty();
        var model = ChatModelFactory.create(config, secrets);
        assertThat(model).isNotNull();
    }
}
