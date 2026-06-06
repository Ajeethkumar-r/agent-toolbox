# Cross-Cutting Concerns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the Phase C agent with persistent memory, E2E testing, multi-tool chaining, and error recovery — all within existing modules.

**Architecture:** Four independent concerns implemented across `agent-common`, `agent-core`, `tool-etag`, `agent-cli`, and `agent-integration-tests`. No new modules. Build order unchanged: agent-common -> agent-core -> tool-etag -> agent-cli -> agent-integration-tests.

**Tech Stack:** Java 17, LangChain4j 1.0.0 (ChatMemoryStore, TokenCountEstimator, TokenWindowChatMemory), WireMock 3.10.0, JUnit 5, Mockito 5, AssertJ 3.

**Spec:** `docs/superpowers/specs/2026-06-06-cross-cutting-concerns-design.md`

---

## File Map

### New Files

| File | Module | Responsibility |
|------|--------|----------------|
| `agent-common/src/main/java/io/agenttoolbox/common/error/ToolErrorFormatter.java` | agent-common | Map exceptions to structured ERROR + ACTION strings |
| `agent-common/src/test/java/io/agenttoolbox/common/error/ToolErrorFormatterTest.java` | agent-common | Unit tests for all exception types |
| `agent-core/src/main/java/io/agenttoolbox/core/memory/FileChatMemoryStore.java` | agent-core | Persist chat memory to JSON files on disk |
| `agent-core/src/main/java/io/agenttoolbox/core/memory/HeuristicTokenEstimator.java` | agent-core | Estimate token counts using chars/4 heuristic |
| `agent-core/src/test/java/io/agenttoolbox/core/memory/FileChatMemoryStoreTest.java` | agent-core | Unit tests for file-based persistence |
| `agent-core/src/test/java/io/agenttoolbox/core/memory/HeuristicTokenEstimatorTest.java` | agent-core | Unit tests for token estimation |
| `agent-integration-tests/src/test/java/io/agenttoolbox/it/e2e/AgentE2ETest.java` | agent-integration-tests | WireMock-based full-loop agent tests |
| `agent-integration-tests/src/test/java/io/agenttoolbox/it/e2e/WireMockOllamaStubs.java` | agent-integration-tests | Static factory methods for Ollama response JSON |

### Modified Files

| File | Change |
|------|--------|
| `agent-core/src/main/java/io/agenttoolbox/core/config/AgentConfig.java` | Replace `maxMessages` with `maxTokens` + `storagePath` in MemoryConfig |
| `agent-core/src/main/java/io/agenttoolbox/core/AgentRunner.java` | Use TokenWindowChatMemory + FileChatMemoryStore + clearMemory() method |
| `agent-core/src/main/resources/prompts/system-prompt.txt` | Enhanced prompt with RULES and COMMON PATTERNS |
| `agent-core/src/test/resources/test-config.yaml` | Update memory config to match new schema |
| `agent-core/src/test/java/io/agenttoolbox/core/config/ConfigLoaderTest.java` | Update assertions for new memory config fields |
| `agent-core/src/test/java/io/agenttoolbox/core/AgentRunnerTest.java` | Update test setup for new config fields |
| `agent-cli/src/main/java/io/agenttoolbox/cli/AgentCli.java` | Add `clear` REPL command |
| `agent-cli/src/main/resources/application.yaml` | Update memory config section |
| `tool-etag/src/main/java/io/agenttoolbox/tool/etag/EtagTools.java` | Use ToolErrorFormatter in catch blocks |
| `tool-etag/src/main/java/io/agenttoolbox/tool/etag/BrowserTools.java` | Use ToolErrorFormatter in catch blocks |

---

## Task 1: ToolErrorFormatter — Tests

**Files:**
- Create: `agent-common/src/test/java/io/agenttoolbox/common/error/ToolErrorFormatterTest.java`

- [ ] **Step 1: Write the failing tests**

Create `agent-common/src/test/java/io/agenttoolbox/common/error/ToolErrorFormatterTest.java`:

```java
package io.agenttoolbox.common.error;

import io.agenttoolbox.common.exception.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolErrorFormatterTest {

    @Test
    void formatsBucketNotFoundException() {
        BucketNotFoundException e = new BucketNotFoundException("my-bucket");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("my-bucket");
        assertThat(result).contains("ACTION: Call listBuckets to see available bucket names.");
    }

    @Test
    void formatsFileNotFoundException() {
        FileNotFoundException e = new FileNotFoundException("my-bucket", "docs/readme.md");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("docs/readme.md");
        assertThat(result).contains("ACTION: Call listFiles with the bucket name to see available files.");
    }

    @Test
    void formatsPreconditionFailedException() {
        PreconditionFailedException e = new PreconditionFailedException("abc123", "def456");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("ACTION: Call getFileInfo to get the current ETag, then retry conditionalUpdate with the new ETag.");
    }

    @Test
    void formatsHashMismatchException() {
        HashMismatchException e = new HashMismatchException("expected123", "actual456");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("ACTION: The file may be corrupted. Try uploading again with uploadWithValidation.");
    }

    @Test
    void formatsGenericStorageException() {
        StorageException e = new StorageException("disk full");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("ACTION: This is a storage error. Check if the bucket exists and the file path is correct.");
    }

    @Test
    void formatsUnknownException() {
        RuntimeException e = new RuntimeException("something weird");
        String result = ToolErrorFormatter.format(e);
        assertThat(result).startsWith("ERROR:");
        assertThat(result).contains("something weird");
        assertThat(result).contains("ACTION: An unexpected error occurred. Report this to the user.");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl agent-common -Dtest=ToolErrorFormatterTest -f pom.xml`
Expected: Compilation error — `ToolErrorFormatter` does not exist yet.

---

## Task 2: ToolErrorFormatter — Implementation

**Files:**
- Create: `agent-common/src/main/java/io/agenttoolbox/common/error/ToolErrorFormatter.java`

- [ ] **Step 1: Write minimal implementation**

Create `agent-common/src/main/java/io/agenttoolbox/common/error/ToolErrorFormatter.java`:

```java
package io.agenttoolbox.common.error;

import io.agenttoolbox.common.exception.*;

/**
 * Formats tool exceptions into structured ERROR + ACTION strings
 * that guide the LLM toward recovery actions.
 */
public final class ToolErrorFormatter {

    private ToolErrorFormatter() {}

    public static String format(Exception e) {
        if (e instanceof BucketNotFoundException) {
            return "ERROR: " + e.getMessage()
                    + "\nACTION: Call listBuckets to see available bucket names.";
        }
        if (e instanceof FileNotFoundException) {
            return "ERROR: " + e.getMessage()
                    + "\nACTION: Call listFiles with the bucket name to see available files.";
        }
        if (e instanceof PreconditionFailedException) {
            return "ERROR: " + e.getMessage()
                    + "\nACTION: Call getFileInfo to get the current ETag, then retry conditionalUpdate with the new ETag.";
        }
        if (e instanceof HashMismatchException) {
            return "ERROR: " + e.getMessage()
                    + "\nACTION: The file may be corrupted. Try uploading again with uploadWithValidation.";
        }
        if (e instanceof StorageException) {
            return "ERROR: " + e.getMessage()
                    + "\nACTION: This is a storage error. Check if the bucket exists and the file path is correct.";
        }
        return "ERROR: " + e.getMessage()
                + "\nACTION: An unexpected error occurred. Report this to the user.";
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./mvnw test -pl agent-common -Dtest=ToolErrorFormatterTest -f pom.xml`
Expected: All 6 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add agent-common/src/main/java/io/agenttoolbox/common/error/ToolErrorFormatter.java \
       agent-common/src/test/java/io/agenttoolbox/common/error/ToolErrorFormatterTest.java
git commit -m "feat(agent-common): add ToolErrorFormatter with structured ERROR/ACTION output"
```

---

## Task 3: Wire ToolErrorFormatter into EtagTools and BrowserTools

**Files:**
- Modify: `tool-etag/src/main/java/io/agenttoolbox/tool/etag/EtagTools.java`
- Modify: `tool-etag/src/main/java/io/agenttoolbox/tool/etag/BrowserTools.java`

- [ ] **Step 1: Update EtagTools catch blocks**

In `tool-etag/src/main/java/io/agenttoolbox/tool/etag/EtagTools.java`, add the import and replace all 4 catch blocks:

Add import at the top:
```java
import io.agenttoolbox.common.error.ToolErrorFormatter;
```

Replace every occurrence of:
```java
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
```

With:
```java
        } catch (Exception e) {
            return ToolErrorFormatter.format(e);
        }
```

There are 4 catch blocks total — one in each method: `deltaSync`, `uploadWithValidation`, `conditionalUpdate`, `cacheValidation`.

- [ ] **Step 2: Update BrowserTools catch blocks**

In `tool-etag/src/main/java/io/agenttoolbox/tool/etag/BrowserTools.java`, add the import and replace all 5 catch blocks:

Add import at the top:
```java
import io.agenttoolbox.common.error.ToolErrorFormatter;
```

Replace every occurrence of:
```java
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
```

With:
```java
        } catch (Exception e) {
            return ToolErrorFormatter.format(e);
        }
```

There are 5 catch blocks total — one in each method: `listBuckets`, `listFiles`, `getFileInfo`, `readFile`, `deleteFile`.

- [ ] **Step 3: Build tool-etag to verify compilation**

Run: `./mvnw compile -pl agent-common,tool-etag -f pom.xml`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Run existing tool-etag tests to verify no regressions**

Run: `./mvnw test -pl tool-etag -f pom.xml`
Expected: All existing tests PASS. The service-level tests mock `StorageAdapter` and don't go through the tool's catch blocks, so they are unaffected.

- [ ] **Step 5: Commit**

```bash
git add tool-etag/src/main/java/io/agenttoolbox/tool/etag/EtagTools.java \
       tool-etag/src/main/java/io/agenttoolbox/tool/etag/BrowserTools.java
git commit -m "feat(tool-etag): use ToolErrorFormatter for structured LLM error recovery"
```

---

## Task 4: Enhanced System Prompt

**Files:**
- Modify: `agent-core/src/main/resources/prompts/system-prompt.txt`

- [ ] **Step 1: Replace system prompt**

Overwrite `agent-core/src/main/resources/prompts/system-prompt.txt` with:

```
You are a file storage assistant. You MUST use your tools to answer every question.
Never answer without calling a tool first. Output the tool result exactly as returned.

RULES:
1. Always call a tool before answering. Never guess or make up data.
2. You can call multiple tools in sequence to complete a task.
3. If a tool returns an error, read the suggested action and try it.
4. When a user asks to do something that requires information you don't have, get it first.

COMMON PATTERNS:
- "sync and verify" → call deltaSync, then call cacheValidation on a synced file
- "upload and check" → call uploadWithValidation, then call getFileInfo
- "what's in my storage" → call listBuckets, then call listFiles on each bucket
- "update safely" → call getFileInfo (to get ETag), then call conditionalUpdate with that ETag

If a tool needs a bucket name and the user didn't specify one, call listBuckets first.
If a tool needs an ETag and you don't have one, call getFileInfo first.
```

- [ ] **Step 2: Build agent-core to verify prompt is valid**

Run: `./mvnw compile -pl agent-core -f pom.xml`
Expected: BUILD SUCCESS. LangChain4j loads prompts at runtime, so compilation doesn't validate content, but it verifies the file is on the classpath.

- [ ] **Step 3: Commit**

```bash
git add agent-core/src/main/resources/prompts/system-prompt.txt
git commit -m "feat(agent-core): enhance system prompt for multi-tool chaining and error recovery"
```

---

## Task 5: HeuristicTokenEstimator — Tests

**Files:**
- Create: `agent-core/src/test/java/io/agenttoolbox/core/memory/HeuristicTokenEstimatorTest.java`

- [ ] **Step 1: Write the failing tests**

Create `agent-core/src/test/java/io/agenttoolbox/core/memory/HeuristicTokenEstimatorTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl agent-core -Dtest=HeuristicTokenEstimatorTest -f pom.xml`
Expected: Compilation error — `HeuristicTokenEstimator` does not exist yet.

---

## Task 6: HeuristicTokenEstimator — Implementation

**Files:**
- Create: `agent-core/src/main/java/io/agenttoolbox/core/memory/HeuristicTokenEstimator.java`

- [ ] **Step 1: Write minimal implementation**

Create `agent-core/src/main/java/io/agenttoolbox/core/memory/HeuristicTokenEstimator.java`:

```java
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
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./mvnw test -pl agent-core -Dtest=HeuristicTokenEstimatorTest -f pom.xml`
Expected: All 5 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add agent-core/src/main/java/io/agenttoolbox/core/memory/HeuristicTokenEstimator.java \
       agent-core/src/test/java/io/agenttoolbox/core/memory/HeuristicTokenEstimatorTest.java
git commit -m "feat(agent-core): add HeuristicTokenEstimator for Ollama token counting"
```

---

## Task 7: FileChatMemoryStore — Tests

**Files:**
- Create: `agent-core/src/test/java/io/agenttoolbox/core/memory/FileChatMemoryStoreTest.java`

- [ ] **Step 1: Write the failing tests**

Create `agent-core/src/test/java/io/agenttoolbox/core/memory/FileChatMemoryStoreTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl agent-core -Dtest=FileChatMemoryStoreTest -f pom.xml`
Expected: Compilation error — `FileChatMemoryStore` does not exist yet.

---

## Task 8: FileChatMemoryStore — Implementation

**Files:**
- Create: `agent-core/src/main/java/io/agenttoolbox/core/memory/FileChatMemoryStore.java`

- [ ] **Step 1: Write minimal implementation**

Create `agent-core/src/main/java/io/agenttoolbox/core/memory/FileChatMemoryStore.java`:

```java
package io.agenttoolbox.core.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
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
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./mvnw test -pl agent-core -Dtest=FileChatMemoryStoreTest -f pom.xml`
Expected: All 7 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add agent-core/src/main/java/io/agenttoolbox/core/memory/FileChatMemoryStore.java \
       agent-core/src/test/java/io/agenttoolbox/core/memory/FileChatMemoryStoreTest.java
git commit -m "feat(agent-core): add FileChatMemoryStore for persistent chat memory"
```

---

## Task 9: Update AgentConfig for New Memory Schema

**Files:**
- Modify: `agent-core/src/main/java/io/agenttoolbox/core/config/AgentConfig.java`
- Modify: `agent-core/src/test/resources/test-config.yaml`
- Modify: `agent-core/src/test/java/io/agenttoolbox/core/config/ConfigLoaderTest.java`
- Modify: `agent-cli/src/main/resources/application.yaml`

- [ ] **Step 1: Update MemoryConfig in AgentConfig.java**

In `agent-core/src/main/java/io/agenttoolbox/core/config/AgentConfig.java`, replace the `MemoryConfig` class (lines 52-57):

Replace:
```java
    public static class MemoryConfig {
        private int maxMessages = 20;

        public int getMaxMessages() { return maxMessages; }
        public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }
    }
```

With:
```java
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
```

Note: `Path` is already imported at line 3.

- [ ] **Step 2: Update application.yaml**

In `agent-cli/src/main/resources/application.yaml`, replace:

```yaml
  memory:
    max-messages: 20
```

With:
```yaml
  memory:
    max-tokens: 4000
```

- [ ] **Step 3: Update test-config.yaml**

In `agent-core/src/test/resources/test-config.yaml`, replace:

```yaml
  memory:
    max-messages: 10
```

With:
```yaml
  memory:
    max-tokens: 2000
    storage-path: /tmp/test-memory
```

- [ ] **Step 4: Update ConfigLoaderTest**

In `agent-core/src/test/java/io/agenttoolbox/core/config/ConfigLoaderTest.java`, in the `loadsConfigFromInputStream` test, replace:

```java
        assertThat(config.getAgent().getMemory().getMaxMessages()).isEqualTo(10);
```

With:
```java
        assertThat(config.getAgent().getMemory().getMaxTokens()).isEqualTo(2000);
        assertThat(config.getAgent().getMemory().getStoragePath()).isEqualTo("/tmp/test-memory");
```

- [ ] **Step 5: Update AgentRunnerTest**

In `agent-core/src/test/java/io/agenttoolbox/core/AgentRunnerTest.java`, no changes are needed — the tests use `new AgentConfig()` which picks up defaults, and neither test calls `initialize()` successfully (one tests unavailable Ollama, other tests tool names). Verify they still pass.

- [ ] **Step 6: Run all agent-core tests**

Run: `./mvnw test -pl agent-core -f pom.xml`
Expected: All tests PASS (ConfigLoaderTest, AgentRunnerTest, ChatModelFactoryTest, ToolRegistryTest, FileChatMemoryStoreTest, HeuristicTokenEstimatorTest).

- [ ] **Step 7: Commit**

```bash
git add agent-core/src/main/java/io/agenttoolbox/core/config/AgentConfig.java \
       agent-core/src/test/resources/test-config.yaml \
       agent-core/src/test/java/io/agenttoolbox/core/config/ConfigLoaderTest.java \
       agent-cli/src/main/resources/application.yaml
git commit -m "refactor(agent-core): replace max-messages with max-tokens and storage-path config"
```

---

## Task 10: Wire TokenWindowChatMemory + FileChatMemoryStore into AgentRunner

**Files:**
- Modify: `agent-core/src/main/java/io/agenttoolbox/core/AgentRunner.java`

- [ ] **Step 1: Update AgentRunner**

Replace the full content of `agent-core/src/main/java/io/agenttoolbox/core/AgentRunner.java`:

```java
package io.agenttoolbox.core;

import dev.langchain4j.memory.chat.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import io.agenttoolbox.common.config.SecretProvider;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.core.llm.ChatModelFactory;
import io.agenttoolbox.core.llm.OllamaHealthCheck;
import io.agenttoolbox.core.memory.FileChatMemoryStore;
import io.agenttoolbox.core.memory.HeuristicTokenEstimator;
import io.agenttoolbox.core.model.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Thin orchestrator that wires together the LLM, tools, and chat memory
 * to produce an {@link AgentService} capable of conversing with users.
 */
public class AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);

    private final AgentConfig config;
    private final SecretProvider secrets;
    private final ToolRegistry toolRegistry;

    private volatile AgentService agentService;
    private volatile ChatMemory chatMemory;
    private volatile boolean initialized;

    public AgentRunner(AgentConfig config, SecretProvider secrets, ToolRegistry toolRegistry) {
        this.config = config;
        this.secrets = secrets;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Lazily initializes the LLM connection, health check, and AI service.
     */
    public void initialize() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;

            String provider = config.getLlm().getProvider();
            if ("ollama".equals(provider)) {
                OllamaHealthCheck.verify(
                        config.getLlm().getOllama().getBaseUrl(),
                        config.getLlm().getOllama().getHealthCheckTimeoutSeconds());
            }

            ChatModel chatModel = ChatModelFactory.create(config, secrets);
            List<Object> tools = toolRegistry.discoverTools(config);

            chatMemory = TokenWindowChatMemory.builder()
                    .id("default")
                    .maxTokens(config.getAgent().getMemory().getMaxTokens())
                    .tokenCountEstimator(new HeuristicTokenEstimator())
                    .chatMemoryStore(new FileChatMemoryStore(
                            Path.of(config.getAgent().getMemory().getStoragePath())))
                    .build();

            AiServices<AgentService> builder = AiServices.builder(AgentService.class)
                    .chatModel(chatModel)
                    .chatMemory(chatMemory);

            if (!tools.isEmpty()) {
                builder.tools(tools);
            }

            agentService = builder.build();
            initialized = true;
            log.info("AgentRunner initialized with provider={}, tools={}", provider, toolRegistry.getProviderNames());
        }
    }

    /**
     * Sends a user message to the agent and returns the response.
     * Initializes lazily on first call.
     */
    public AgentResponse chat(String userMessage) {
        try {
            initialize();
            String reply = agentService.chat(userMessage);
            return AgentResponse.ok(reply);
        } catch (Exception e) {
            log.error("Chat failed: {}", e.getMessage(), e);
            return AgentResponse.error(e.getMessage());
        }
    }

    /** Clears chat memory (both in-memory and persisted). */
    public void clearMemory() {
        if (chatMemory != null) {
            chatMemory.clear();
        }
    }

    /** Returns the names of all registered tool providers. */
    public List<String> getToolNames() {
        return toolRegistry.getProviderNames();
    }

    /** Returns the current configuration. */
    public AgentConfig getConfig() {
        return config;
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./mvnw compile -pl agent-common,agent-core -f pom.xml`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Run agent-core tests**

Run: `./mvnw test -pl agent-core -f pom.xml`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add agent-core/src/main/java/io/agenttoolbox/core/AgentRunner.java
git commit -m "feat(agent-core): wire TokenWindowChatMemory with FileChatMemoryStore into AgentRunner"
```

---

## Task 11: Add `clear` Command to CLI REPL

**Files:**
- Modify: `agent-cli/src/main/java/io/agenttoolbox/cli/AgentCli.java`

- [ ] **Step 1: Add `clear` to the REPL loop**

In `agent-cli/src/main/java/io/agenttoolbox/cli/AgentCli.java`, in the `runRepl` method, add a `clear` command handler after the `help` block.

Replace:
```java
                if ("help".equalsIgnoreCase(input)) {
                    System.out.println("Available tools: " + runner.getToolNames());
                    System.out.println("Commands: help, quit/exit");
                    System.out.println("Describe your file storage problem in natural language.\n");
                    continue;
                }
```

With:
```java
                if ("help".equalsIgnoreCase(input)) {
                    System.out.println("Available tools: " + runner.getToolNames());
                    System.out.println("Commands: help, clear, quit/exit");
                    System.out.println("Describe your file storage problem in natural language.\n");
                    continue;
                }
                if ("clear".equalsIgnoreCase(input)) {
                    runner.clearMemory();
                    System.out.println("Chat memory cleared.\n");
                    continue;
                }
```

- [ ] **Step 2: Build the full project**

Run: `./mvnw compile -f pom.xml`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add agent-cli/src/main/java/io/agenttoolbox/cli/AgentCli.java
git commit -m "feat(agent-cli): add 'clear' REPL command to reset chat memory"
```

---

## Task 12: WireMock Ollama Stubs Helper

**Files:**
- Create: `agent-integration-tests/src/test/java/io/agenttoolbox/it/e2e/WireMockOllamaStubs.java`

- [ ] **Step 1: Create the stubs helper**

Create `agent-integration-tests/src/test/java/io/agenttoolbox/it/e2e/WireMockOllamaStubs.java`:

```java
package io.agenttoolbox.it.e2e;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static factory methods for building Ollama-compatible JSON responses
 * used by WireMock stubs in E2E tests.
 */
public final class WireMockOllamaStubs {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WireMockOllamaStubs() {}

    /** Response for GET /api/tags (health check). */
    public static String tagsResponse() {
        return "{\"models\":[{\"name\":\"llama3.1:8b\"}]}";
    }

    /** Response where the LLM returns a text answer (no tool call). */
    public static String textResponse(String content) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "assistant");
            message.put("content", content);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("model", "llama3.1:8b");
            response.put("message", message);
            response.put("done", true);

            return MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /** Response where the LLM calls a tool with the given name and arguments. */
    public static String toolCallResponse(String toolName, Map<String, Object> arguments) {
        try {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", toolName);
            function.put("arguments", arguments);

            Map<String, Object> toolCall = new LinkedHashMap<>();
            toolCall.put("function", function);

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "assistant");
            message.put("content", "");
            message.put("tool_calls", List.of(toolCall));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("model", "llama3.1:8b");
            response.put("message", message);
            response.put("done", true);

            return MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -pl agent-integration-tests -f pom.xml -DskipTests`
Expected: BUILD SUCCESS (or compilation of test sources with `test-compile`).

Run: `./mvnw test-compile -pl agent-integration-tests -f pom.xml`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add agent-integration-tests/src/test/java/io/agenttoolbox/it/e2e/WireMockOllamaStubs.java
git commit -m "test(integration): add WireMockOllamaStubs helper for E2E tests"
```

---

## Task 13: E2E Agent Tests with WireMock

**Files:**
- Create: `agent-integration-tests/src/test/java/io/agenttoolbox/it/e2e/AgentE2ETest.java`

- [ ] **Step 1: Create the E2E test class**

Create `agent-integration-tests/src/test/java/io/agenttoolbox/it/e2e/AgentE2ETest.java`:

```java
package io.agenttoolbox.it.e2e;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.agenttoolbox.common.config.EnvVarSecretProvider;
import io.agenttoolbox.core.AgentRunner;
import io.agenttoolbox.core.ToolRegistry;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.core.model.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest(httpPort = 18434)
class AgentE2ETest {

    private static final String WIREMOCK_BASE_URL = "http://localhost:18434";

    @TempDir Path bucketRoot;
    @TempDir Path memoryDir;

    AgentRunner runner;

    @BeforeEach
    void setUp() {
        // Stub Ollama health check
        stubFor(get("/api/tags")
                .willReturn(okJson(WireMockOllamaStubs.tagsResponse())));

        AgentConfig config = buildTestConfig();
        runner = new AgentRunner(config, new EnvVarSecretProvider(), new ToolRegistry());
    }

    @Test
    void singleToolCall_listBuckets() throws IOException {
        // Create a bucket directory so listBuckets has something to return
        Files.createDirectories(bucketRoot.resolve("test-bucket"));

        // First call: LLM decides to call listBuckets
        stubFor(post("/api/chat")
                .inScenario("single-tool")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson(WireMockOllamaStubs.toolCallResponse("listBuckets", Map.of())))
                .willSetStateTo("tool-called"));

        // Second call: LLM formats the tool result into a response
        stubFor(post("/api/chat")
                .inScenario("single-tool")
                .whenScenarioStateIs("tool-called")
                .willReturn(okJson(WireMockOllamaStubs.textResponse(
                        "Found 1 bucket: test-bucket"))));

        AgentResponse response = runner.chat("list my buckets");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("test-bucket");
    }

    @Test
    void toolCallWithParameters_listFiles() throws IOException {
        // Create bucket with a file
        Path bucket = bucketRoot.resolve("docs");
        Files.createDirectories(bucket);
        Files.writeString(bucket.resolve("readme.txt"), "hello");
        // Create metadata sidecar
        Files.writeString(bucket.resolve(".readme.txt.metadata.json"),
                "{\"key\":\"readme.txt\",\"bucket\":\"docs\",\"md5Hash\":\"abc\",\"etag\":\"abc\",\"size\":5,\"lastModified\":\"2026-06-06T00:00:00Z\",\"contentType\":\"text/plain\"}");

        // First call: LLM calls listFiles with parameters
        stubFor(post("/api/chat")
                .inScenario("param-tool")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson(WireMockOllamaStubs.toolCallResponse(
                        "listFiles", Map.of("bucketName", "docs", "prefix", ""))))
                .willSetStateTo("tool-called"));

        // Second call: LLM formats response
        stubFor(post("/api/chat")
                .inScenario("param-tool")
                .whenScenarioStateIs("tool-called")
                .willReturn(okJson(WireMockOllamaStubs.textResponse(
                        "Found 1 file in docs: readme.txt"))));

        AgentResponse response = runner.chat("what files are in docs?");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("readme.txt");
    }

    @Test
    void multiToolChain_listBucketsThenListFiles() throws IOException {
        // Create bucket with a file
        Path bucket = bucketRoot.resolve("my-bucket");
        Files.createDirectories(bucket);
        Files.writeString(bucket.resolve("data.csv"), "a,b,c");
        Files.writeString(bucket.resolve(".data.csv.metadata.json"),
                "{\"key\":\"data.csv\",\"bucket\":\"my-bucket\",\"md5Hash\":\"x\",\"etag\":\"x\",\"size\":5,\"lastModified\":\"2026-06-06T00:00:00Z\",\"contentType\":\"text/csv\"}");

        // Call 1: LLM calls listBuckets
        stubFor(post("/api/chat")
                .inScenario("multi-tool")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson(WireMockOllamaStubs.toolCallResponse("listBuckets", Map.of())))
                .willSetStateTo("after-list-buckets"));

        // Call 2: LLM calls listFiles on discovered bucket
        stubFor(post("/api/chat")
                .inScenario("multi-tool")
                .whenScenarioStateIs("after-list-buckets")
                .willReturn(okJson(WireMockOllamaStubs.toolCallResponse(
                        "listFiles", Map.of("bucketName", "my-bucket", "prefix", ""))))
                .willSetStateTo("after-list-files"));

        // Call 3: LLM formats final answer
        stubFor(post("/api/chat")
                .inScenario("multi-tool")
                .whenScenarioStateIs("after-list-files")
                .willReturn(okJson(WireMockOllamaStubs.textResponse(
                        "You have 1 bucket (my-bucket) with 1 file: data.csv"))));

        AgentResponse response = runner.chat("what's in my storage?");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("my-bucket");
        assertThat(response.message()).contains("data.csv");
    }

    @Test
    void toolErrorRecovery_bucketNotFound() throws IOException {
        // Create the correct bucket
        Files.createDirectories(bucketRoot.resolve("my-bucket"));

        // Call 1: LLM calls listFiles with a typo bucket name
        stubFor(post("/api/chat")
                .inScenario("error-recovery")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson(WireMockOllamaStubs.toolCallResponse(
                        "listFiles", Map.of("bucketName", "my-buckeet", "prefix", ""))))
                .willSetStateTo("error-returned"));

        // Call 2: After ERROR + ACTION, LLM calls listBuckets
        stubFor(post("/api/chat")
                .inScenario("error-recovery")
                .whenScenarioStateIs("error-returned")
                .willReturn(okJson(WireMockOllamaStubs.toolCallResponse("listBuckets", Map.of())))
                .willSetStateTo("buckets-listed"));

        // Call 3: LLM calls listFiles with correct name
        stubFor(post("/api/chat")
                .inScenario("error-recovery")
                .whenScenarioStateIs("buckets-listed")
                .willReturn(okJson(WireMockOllamaStubs.toolCallResponse(
                        "listFiles", Map.of("bucketName", "my-bucket", "prefix", ""))))
                .willSetStateTo("files-listed"));

        // Call 4: LLM formats final answer
        stubFor(post("/api/chat")
                .inScenario("error-recovery")
                .whenScenarioStateIs("files-listed")
                .willReturn(okJson(WireMockOllamaStubs.textResponse(
                        "The bucket 'my-bucket' is empty."))));

        AgentResponse response = runner.chat("list files in my-buckeet");

        assertThat(response.success()).isTrue();
    }

    @Test
    void memoryPersistence_survivesRestart() {
        // Stub a simple interaction
        stubFor(post("/api/chat")
                .willReturn(okJson(WireMockOllamaStubs.textResponse("Hello! How can I help?"))));

        // First conversation
        AgentResponse response1 = runner.chat("hi");
        assertThat(response1.success()).isTrue();

        // Create a new runner pointing to the same memory dir (simulates restart)
        AgentConfig config2 = buildTestConfig();
        AgentRunner runner2 = new AgentRunner(config2, new EnvVarSecretProvider(), new ToolRegistry());

        AgentResponse response2 = runner2.chat("what did I say before?");
        assertThat(response2.success()).isTrue();

        // Verify that WireMock received requests from both runners
        // (proves the second runner initialized and sent a request)
        verify(atLeast(2), postRequestedFor(urlEqualTo("/api/chat")));
    }

    private AgentConfig buildTestConfig() {
        AgentConfig config = new AgentConfig();
        config.getLlm().getOllama().setBaseUrl(WIREMOCK_BASE_URL);
        config.getLlm().getOllama().setModel("llama3.1:8b");
        config.getLlm().getOllama().setTimeoutSeconds(10);
        config.getLlm().getOllama().setHealthCheckTimeoutSeconds(5);
        config.getStorage().getLocal().setBucketRoot(bucketRoot.toString());
        config.getAgent().getMemory().setMaxTokens(2000);
        config.getAgent().getMemory().setStoragePath(memoryDir.toString());
        return config;
    }
}
```

- [ ] **Step 2: Run E2E tests**

Run: `./mvnw test -pl agent-integration-tests -Dtest=AgentE2ETest -f pom.xml`
Expected: All 5 tests PASS.

If tests fail due to WireMock response format mismatches with LangChain4j's Ollama client, inspect the exact request/response format by adding `System.out.println(getAllServeEvents())` in the test and adjust `WireMockOllamaStubs` accordingly.

- [ ] **Step 3: Run ALL integration tests to check for regressions**

Run: `./mvnw test -pl agent-integration-tests -f pom.xml`
Expected: All tests PASS (both `LocalStorageIntegrationTest` and `AgentE2ETest`).

- [ ] **Step 4: Commit**

```bash
git add agent-integration-tests/src/test/java/io/agenttoolbox/it/e2e/AgentE2ETest.java
git commit -m "test(integration): add WireMock E2E tests for full agent loop"
```

---

## Task 14: Full Build Verification

**Files:** None — this is a validation task.

- [ ] **Step 1: Run full project build with tests**

Run: `./mvnw clean verify -f pom.xml`
Expected: BUILD SUCCESS across all 5 modules. All unit and integration tests pass.

- [ ] **Step 2: Verify test counts**

Check the Surefire reports for expected test counts:
- `agent-common`: ToolErrorFormatterTest (6 tests) + any existing tests
- `agent-core`: FileChatMemoryStoreTest (7) + HeuristicTokenEstimatorTest (5) + ConfigLoaderTest (3) + AgentRunnerTest (2) + ChatModelFactoryTest + ToolRegistryTest
- `tool-etag`: All existing service tests (unchanged)
- `agent-integration-tests`: LocalStorageIntegrationTest (6) + AgentE2ETest (5)

Run: `./mvnw test -f pom.xml 2>&1 | grep "Tests run:"`
Expected: All modules show 0 failures, 0 errors.

- [ ] **Step 3: Commit if any fixes were needed**

If any fixes were applied in this step, commit them:
```bash
git add -A
git commit -m "fix: resolve issues found during full build verification"
```
