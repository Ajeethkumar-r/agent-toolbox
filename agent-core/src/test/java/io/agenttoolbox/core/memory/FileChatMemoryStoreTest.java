package io.agenttoolbox.core.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileChatMemoryStoreTest {

    @TempDir
    Path storageDir;

    FileChatMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new FileChatMemoryStore(storageDir);
    }

    @Test
    void returnsEmptyListWhenNoFileExists() {
        List<ChatMessage> messages = store.getMessages("session-1");
        assertThat(messages).isEmpty();
    }

    @Test
    void persistsAndRetrievesMessages() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("hello"),
                AiMessage.from("hi there")
        );
        store.updateMessages("session-1", messages);

        List<ChatMessage> loaded = store.getMessages("session-1");
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0)).isInstanceOf(UserMessage.class);
        assertThat(loaded.get(1)).isInstanceOf(AiMessage.class);
    }

    @Test
    void deletesMessages() {
        store.updateMessages("session-1", List.of(UserMessage.from("hello")));
        assertThat(store.getMessages("session-1")).hasSize(1);

        store.deleteMessages("session-1");
        assertThat(store.getMessages("session-1")).isEmpty();
    }

    @Test
    void deleteIsIdempotentWhenNoFile() {
        store.deleteMessages("nonexistent");
        // Should not throw
    }

    @Test
    void isolatesMemoryIds() {
        store.updateMessages("session-a", List.of(UserMessage.from("A")));
        store.updateMessages("session-b", List.of(UserMessage.from("B1"), UserMessage.from("B2")));

        assertThat(store.getMessages("session-a")).hasSize(1);
        assertThat(store.getMessages("session-b")).hasSize(2);
    }

    @Test
    void overwritesPreviousMessages() {
        store.updateMessages("session-1", List.of(UserMessage.from("old")));
        store.updateMessages("session-1", List.of(UserMessage.from("new1"), UserMessage.from("new2")));

        List<ChatMessage> loaded = store.getMessages("session-1");
        assertThat(loaded).hasSize(2);
    }

    @Test
    void createsJsonFileOnDisk() {
        store.updateMessages("session-1", List.of(UserMessage.from("hello")));
        Path file = storageDir.resolve("chat-memory-session-1.json");
        assertThat(Files.exists(file)).isTrue();
    }
}
