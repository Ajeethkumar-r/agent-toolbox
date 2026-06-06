# Cross-Cutting Concerns Design Spec

**Date:** 2026-06-06
**Scope:** Four cross-cutting improvements to agent-toolbox before Phase B
**Modules affected:** `agent-core`, `agent-common`, `agent-integration-tests`, `tool-etag`

---

## Overview

Four concerns that improve the agent's reliability and testability without adding new tools or cloud dependencies. These harden the Phase C foundation before moving to Phase B (GCS, cloud LLMs).

1. **Conversation memory persistence** — survive CLI restarts
2. **E2E testing with WireMock** — CI-testable agent loop without Ollama
3. **Multi-tool chaining** — teach the LLM to sequence tool calls
4. **Error recovery prompting** — structured error responses the LLM can act on

---

## 1. Conversation Memory Persistence

### Problem

`AgentRunner` uses `MessageWindowChatMemory.withMaxMessages(20)` — purely in-memory. Every CLI restart loses all context. With tool calls consuming 3-4 messages per interaction (user + tool-call + tool-result + assistant), the effective window is ~5 exchanges.

### Design

Follow LangChain4j's official `ChatMemoryStore` SPI pattern.

#### Components

**`FileChatMemoryStore`** (new class in `agent-core`)
Implements `dev.langchain4j.store.memory.chat.ChatMemoryStore`:

```java
public class FileChatMemoryStore implements ChatMemoryStore {
    private final Path storageDir;  // ~/.agent-toolbox/

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // Read from ~/.agent-toolbox/chat-memory-{memoryId}.json
        // Use ChatMessageDeserializer.messagesFromJson()
        // Return empty list if file doesn't exist
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // Write to ~/.agent-toolbox/chat-memory-{memoryId}.json
        // Use ChatMessageSerializer.messagesToJson()
        // Atomic write: write to .tmp then rename
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // Delete the JSON file
    }
}
```

**`HeuristicTokenEstimator`** (new class in `agent-core`)
Implements `dev.langchain4j.model.TokenCountEstimator`:

```java
public class HeuristicTokenEstimator implements TokenCountEstimator {
    private static final double CHARS_PER_TOKEN = 4.0;

    @Override
    public int estimateTokenCount(String text) {
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    // Delegate other overloads to the text-based estimate
}
```

Rationale: Ollama doesn't ship a token estimator. The `chars / 4` heuristic is standard for English text and sufficient for a local 8B model. When cloud LLMs are added (Phase B2), this gets swapped for the provider's native estimator.

#### Changes to `AgentRunner`

Replace the current memory setup:

```java
// Before
.chatMemory(MessageWindowChatMemory.withMaxMessages(
    config.getAgent().getMemory().getMaxMessages()))

// After
.chatMemory(TokenWindowChatMemory.builder()
    .id("default")
    .maxTokens(config.getAgent().getMemory().getMaxTokens())
    .tokenCountEstimator(new HeuristicTokenEstimator())
    .chatMemoryStore(new FileChatMemoryStore(
        Path.of(config.getAgent().getMemory().getStoragePath())))
    .build())
```

#### Config Changes

```yaml
agent:
  memory:
    max-tokens: 4000           # replaces max-messages: 20
    storage-path: ~/.agent-toolbox   # directory for chat-memory-*.json
```

The `max-messages` field is removed. `max-tokens: 4000` gives roughly 40-50 messages worth of context for the 8B model (whose context window is typically 8192 tokens).

#### CLI Changes

Add a `clear` command to the REPL. This requires `AgentRunner` to expose a `clearMemory()` method that delegates to `chatMemory.clear()` — which triggers `FileChatMemoryStore.deleteMessages()` and removes the JSON file.

```java
// AgentRunner — new method
public void clearMemory() {
    if (chatMemory != null) {
        chatMemory.clear();
    }
}
```

The `chatMemory` field must be stored as an instance variable (currently it's created inline in the builder chain).

#### Eviction Behavior (per LangChain4j docs)

- `TokenWindowChatMemory` evicts oldest messages when token count exceeds `maxTokens`
- Evicted messages are also removed from `FileChatMemoryStore` (store stays in sync with memory)
- If an `AiMessage` with `ToolExecutionRequest`s is evicted, orphan `ToolExecutionResultMessage`s are auto-evicted too

#### What This Does NOT Do

- No separate "history" log. The store persists only what's in the active window. Full conversation history is a future concern — YAGNI for now.
- No multi-user/multi-session support. Single `"default"` memory ID. Multi-user is a Phase B concern.

#### Files Changed

| File | Change |
|------|--------|
| `agent-core/.../memory/FileChatMemoryStore.java` | **New** — ChatMemoryStore impl |
| `agent-core/.../memory/HeuristicTokenEstimator.java` | **New** — token estimator |
| `agent-core/.../AgentRunner.java` | Use TokenWindowChatMemory + FileChatMemoryStore |
| `agent-core/.../config/AgentConfig.java` | Replace `maxMessages` with `maxTokens` + `storagePath` |
| `agent-cli/.../AgentCli.java` | Add `clear` REPL command |
| `agent-cli/.../resources/application.yaml` | Update memory config |

---

## 2. E2E Testing with WireMock

### Problem

The only integration tests (`LocalStorageIntegrationTest`) test tool→storage without the LLM. The full agent loop (user message → LLM → tool call → tool result → response) cannot be tested in CI because it requires a running Ollama instance.

### Design

Use WireMock to stub Ollama's HTTP endpoints. The tests exercise `AgentRunner` end-to-end with deterministic, canned LLM responses.

#### Ollama API Endpoints to Stub

**Health check:** `GET /api/tags`
```json
{"models": [{"name": "llama3.1:8b"}]}
```

**Chat completion:** `POST /api/chat`

Request format (from LangChain4j's Ollama integration):
```json
{
  "model": "llama3.1:8b",
  "messages": [...],
  "tools": [...],
  "stream": false
}
```

Response format — tool call:
```json
{
  "model": "llama3.1:8b",
  "message": {
    "role": "assistant",
    "content": "",
    "tool_calls": [{
      "function": {
        "name": "listBuckets",
        "arguments": {}
      }
    }]
  }
}
```

Response format — final answer:
```json
{
  "model": "llama3.1:8b",
  "message": {
    "role": "assistant",
    "content": "Found 2 buckets: my-bucket, uploads"
  }
}
```

#### Test Structure

```java
@WireMockTest(httpPort = 18434)
class AgentE2ETest {

    @TempDir Path bucketRoot;

    AgentRunner runner;

    @BeforeEach
    void setUp() {
        // Stub health check
        stubFor(get("/api/tags").willReturn(okJson(TAGS_RESPONSE)));

        // Build config pointing Ollama to WireMock port
        AgentConfig config = testConfig(bucketRoot, "http://localhost:18434");

        runner = new AgentRunner(config, new EnvVarSecretProvider(), new ToolRegistry());
    }
}
```

#### Test Scenarios

| Test | What It Validates |
|------|------------------|
| `singleToolCall` | User asks "list buckets" → LLM calls `listBuckets` → tool returns result → LLM formats response |
| `toolCallWithParameters` | User asks "list files in my-bucket" → LLM calls `listFiles(bucketName="my-bucket", prefix="")` |
| `multiToolChain` | LLM makes two sequential tool calls in one conversation turn |
| `toolErrorRecovery` | LLM calls tool → tool returns error → LLM retries with corrected parameters |
| `memoryPersistence` | Chat → restart runner → verify memory reloaded from disk |
| `noToolNeeded` | User says "hello" → LLM responds without calling tools (if model supports it) |

#### WireMock Response Sequencing

For multi-tool tests, use WireMock's `Scenario` API to return different responses on sequential calls:

```java
stubFor(post("/api/chat")
    .inScenario("multi-tool")
    .whenScenarioStateIs(STARTED)
    .willReturn(okJson(TOOL_CALL_LIST_BUCKETS))
    .willSetStateTo("after-list"));

stubFor(post("/api/chat")
    .inScenario("multi-tool")
    .whenScenarioStateIs("after-list")
    .willReturn(okJson(FINAL_ANSWER)));
```

#### Test Helpers

**`WireMockOllamaStubs`** — utility class with static factory methods:

```java
public final class WireMockOllamaStubs {
    public static String healthCheckResponse() { ... }
    public static String toolCallResponse(String toolName, Map<String, Object> args) { ... }
    public static String textResponse(String content) { ... }
    public static String toolCallChain(List<ToolCall> calls, String finalAnswer) { ... }
}
```

#### Files Changed

| File | Change |
|------|--------|
| `agent-integration-tests/.../e2e/AgentE2ETest.java` | **New** — WireMock-based full-loop tests |
| `agent-integration-tests/.../e2e/WireMockOllamaStubs.java` | **New** — response builders |
| `agent-integration-tests/pom.xml` | Add `agent-cli` dependency (already has wiremock) |

---

## 3. Multi-Tool Chaining

### Problem

LangChain4j 1.0.0 already supports sequential tool calls — the framework loops automatically. The bottleneck is the **8B model's reasoning**: it doesn't know it *can* chain tools or *when* it should.

### Design

Enhance the system prompt to teach the model about chaining. No code changes to the framework — this is purely prompt engineering.

#### Current System Prompt

```
You are a file storage assistant. You MUST use your tools to answer.
Never answer without calling a tool first. Output the tool result exactly as returned.
```

#### New System Prompt

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

#### Why This Works

- 8B models respond well to explicit instruction patterns and examples
- The "COMMON PATTERNS" section gives the model a playbook — it doesn't need to reason from first principles
- The fallback rules ("if you don't have X, call Y first") prevent the most common failure mode: the model guessing parameter values instead of looking them up

#### Files Changed

| File | Change |
|------|--------|
| `agent-core/.../resources/prompts/system-prompt.txt` | Replace with enhanced prompt |

---

## 4. Error Recovery Prompting

### Problem

All 9 tool methods catch exceptions and return `"Error: " + e.getMessage()`. The raw error messages are Java exception messages — not designed for LLM consumption. The 8B model sees `"Error: Bucket 'foo' not found"` and either hallucinates a response or gives up.

### Design

Wrap tool errors with structured, actionable hints. The LLM gets a clear "what went wrong" + "what to do next" format.

#### `ToolErrorFormatter` (new class in `agent-common`)

```java
public final class ToolErrorFormatter {

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
        // Generic fallback
        return "ERROR: " + e.getMessage()
            + "\nACTION: An unexpected error occurred. Report this to the user.";
    }
}
```

#### Changes to Tool Classes

Replace the `catch` blocks in `EtagTools` and `BrowserTools`:

```java
// Before
catch (Exception e) {
    return "Error: " + e.getMessage();
}

// After
catch (Exception e) {
    return ToolErrorFormatter.format(e);
}
```

#### Why Structured Errors Work

The system prompt (Section 3) includes the rule: *"If a tool returns an error, read the suggested action and try it."* The `ACTION:` prefix gives the LLM a concrete next step. This pairing — structured errors + system prompt instruction — creates a recovery loop:

1. User: "sync my docs to my-buckeet" (typo)
2. Tool: `ERROR: Bucket 'my-buckeet' not found. ACTION: Call listBuckets to see available bucket names.`
3. LLM: calls `listBuckets` → sees `my-bucket`
4. LLM: calls `deltaSync("~/docs", "my-bucket")` → success

Without this, the 8B model would return the raw error to the user or hallucinate.

#### Files Changed

| File | Change |
|------|--------|
| `agent-common/.../error/ToolErrorFormatter.java` | **New** — structured error formatting |
| `tool-etag/.../EtagTools.java` | Replace catch blocks with `ToolErrorFormatter.format(e)` |
| `tool-etag/.../BrowserTools.java` | Replace catch blocks with `ToolErrorFormatter.format(e)` |

---

## Dependency & Build Order

No new modules. All changes are within existing modules.

Build order remains: `agent-common` → `agent-core` → `tool-etag` → `agent-cli` → `agent-integration-tests`

New dependencies introduced:
- `agent-common` gains no new external deps (ToolErrorFormatter uses existing exception types)
- `agent-core` gains no new external deps (ChatMemoryStore, TokenCountEstimator are already in langchain4j)
- `agent-integration-tests` gains no new external deps (WireMock already declared)

---

## Config Changes Summary

```yaml
# application.yaml — changes highlighted
agent:
  name: Agent Toolbox
  version: 0.1.0
  memory:
    max-tokens: 4000             # NEW (replaces max-messages: 20)
    storage-path: ~/.agent-toolbox  # NEW
```

The `max-messages` config key is removed. `AgentConfig.MemoryConfig` is updated accordingly.

---

## Testing Strategy

| Concern | Test Type | Location |
|---------|-----------|----------|
| FileChatMemoryStore | Unit test | `agent-core` — write/read/delete JSON, atomic writes, missing file handling |
| HeuristicTokenEstimator | Unit test | `agent-core` — verify estimates for various string lengths |
| ToolErrorFormatter | Unit test | `agent-common` — verify each exception type produces correct ACTION |
| E2E agent loop | Integration test | `agent-integration-tests` — WireMock stubs, full AgentRunner exercise |
| System prompt | E2E test | `agent-integration-tests` — WireMock scenario for multi-tool and error recovery |

---

## File Inventory

### New Files (8)

| File | Module | Purpose |
|------|--------|---------|
| `agent-core/.../memory/FileChatMemoryStore.java` | agent-core | Persistent chat memory to JSON |
| `agent-core/.../memory/HeuristicTokenEstimator.java` | agent-core | Token count estimator for Ollama |
| `agent-core/src/test/.../memory/FileChatMemoryStoreTest.java` | agent-core | Unit tests for store |
| `agent-core/src/test/.../memory/HeuristicTokenEstimatorTest.java` | agent-core | Unit tests for estimator |
| `agent-common/.../error/ToolErrorFormatter.java` | agent-common | Structured error formatting |
| `agent-common/src/test/.../error/ToolErrorFormatterTest.java` | agent-common | Unit tests for formatter |
| `agent-integration-tests/.../e2e/AgentE2ETest.java` | agent-integration-tests | WireMock E2E tests |
| `agent-integration-tests/.../e2e/WireMockOllamaStubs.java` | agent-integration-tests | Stub response builders |

### Modified Files (6)

| File | Change |
|------|--------|
| `agent-core/.../AgentRunner.java` | TokenWindowChatMemory + FileChatMemoryStore |
| `agent-core/.../config/AgentConfig.java` | `maxTokens` + `storagePath` replacing `maxMessages` |
| `agent-core/.../resources/prompts/system-prompt.txt` | Enhanced prompt with chaining rules |
| `agent-cli/.../AgentCli.java` | `clear` REPL command |
| `agent-cli/.../resources/application.yaml` | Updated memory config |
| `tool-etag/.../EtagTools.java` | Use ToolErrorFormatter in catch blocks |
| `tool-etag/.../BrowserTools.java` | Use ToolErrorFormatter in catch blocks |
