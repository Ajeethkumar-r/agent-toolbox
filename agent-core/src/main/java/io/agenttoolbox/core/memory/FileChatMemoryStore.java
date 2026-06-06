package io.agenttoolbox.core.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

/**
 * Persists chat memory to JSON files on disk.
 * Each memory ID gets its own file: chat-memory-{memoryId}.json
 * Uses atomic writes (tmp + rename) to prevent corruption.
 */
public class FileChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(FileChatMemoryStore.class);

    private final Path storageDir;

    public FileChatMemoryStore(Path storageDir) {
        this.storageDir = storageDir;
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            log.warn("Could not create storage directory: {}", storageDir, e);
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Path file = resolveFile(memoryId);
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }
        try {
            String json = Files.readString(file);
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (IOException e) {
            log.warn("Failed to read chat memory from {}: {}", file, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Path file = resolveFile(memoryId);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            Files.writeString(tmp, json);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Failed to persist chat memory to {}: {}", file, e.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Path file = resolveFile(memoryId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete chat memory file {}: {}", file, e.getMessage());
        }
    }

    private Path resolveFile(Object memoryId) {
        return storageDir.resolve("chat-memory-" + memoryId + ".json");
    }
}
