package io.agenttoolbox.core.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;

/**
 * Heuristic token estimator using chars / 4.
 * Sufficient for Ollama models that don't ship a native estimator.
 * Swap for provider-native estimator when cloud LLMs are added (Phase B2).
 */
public class HeuristicTokenEstimator implements TokenCountEstimator {

    private static final double CHARS_PER_TOKEN = 4.0;

    @Override
    public int estimateTokenCountInText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInText(message.toString());
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimateTokenCountInMessage(message);
        }
        return total;
    }
}
