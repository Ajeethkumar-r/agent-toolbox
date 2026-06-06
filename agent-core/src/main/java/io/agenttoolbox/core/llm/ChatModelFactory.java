package io.agenttoolbox.core.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
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
            case "gemini":
                return createGeminiModel(config.getLlm().getGemini(), secrets);
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

    private static ChatModel createGeminiModel(AgentConfig.GeminiConfig gemini, SecretProvider secrets) {
        String apiKey = secrets.get("GEMINI_API_KEY")
                .orElseThrow(() -> new LlmException("GEMINI_API_KEY not found in secrets"));
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(gemini.getModel())
                .temperature(gemini.getTemperature())
                .maxOutputTokens(gemini.getMaxOutputTokens())
                .timeout(Duration.ofSeconds(gemini.getTimeoutSeconds()))
                .build();
    }
}
