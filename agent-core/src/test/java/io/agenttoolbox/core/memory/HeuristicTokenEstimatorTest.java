package io.agenttoolbox.core.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicTokenEstimatorTest {

    private final HeuristicTokenEstimator estimator = new HeuristicTokenEstimator();

    @Test
    void estimatesTokensForText() {
        // "hello world" = 11 chars -> 11 / 4.0 = 2.75 -> ceil = 3
        assertThat(estimator.estimateTokenCountInText("hello world")).isEqualTo(3);
    }

    @Test
    void estimatesTokensForEmptyString() {
        assertThat(estimator.estimateTokenCountInText("")).isEqualTo(0);
    }

    @Test
    void estimatesTokensForLongText() {
        // 400 chars -> 400 / 4.0 = 100
        String text = "a".repeat(400);
        assertThat(estimator.estimateTokenCountInText(text)).isEqualTo(100);
    }

    @Test
    void estimatesTokensForUserMessage() {
        UserMessage msg = UserMessage.from("hello world");
        int count = estimator.estimateTokenCountInMessage(msg);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void estimatesTokensForMultipleMessages() {
        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                UserMessage.from("hello"),
                AiMessage.from("world")
        );
        int count = estimator.estimateTokenCountInMessages(messages);
        assertThat(count).isGreaterThan(0);
    }
}
