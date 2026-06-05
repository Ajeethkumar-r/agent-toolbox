package io.agenttoolbox.core.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.agenttoolbox.common.config.SecretProvider;
import io.agenttoolbox.common.exception.LlmException;
import io.agenttoolbox.core.config.AgentConfig;

import java.time.Duration;

/**
 * Factory that creates the appropriate {@link ChatModel} based on configuration.
 */
public final class ChatModelFactory {

    private ChatModelFactory() {}

    public static ChatModel create(AgentConfig config, SecretProvider secrets) {
        String provider = config.getLlm().getProvider();
        switch (provider) {
            case "ollama":
                return createOllamaModel(config.getLlm().getOllama());
            default:
                throw new LlmException("Unknown LLM provider: " + provider);
        }
    }

    private static ChatModel createOllamaModel(AgentConfig.OllamaConfig ollama) {
        return OllamaChatModel.builder()
                .baseUrl(ollama.getBaseUrl())
                .modelName(ollama.getModel())
                .temperature(ollama.getTemperature())
                .timeout(Duration.ofSeconds(ollama.getTimeoutSeconds()))
                .build();
    }
}
