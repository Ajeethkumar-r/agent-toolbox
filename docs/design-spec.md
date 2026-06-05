# Agent Toolbox — Design Spec

**Date:** 2026-06-04
**Status:** Draft
**Repo:** `agent-toolbox` at `~/projects/agent-toolbox`

---

## 1. Overview

Agent Toolbox is a Java-based, LLM-powered CLI agent platform. Users describe problems in natural language; the agent reasons about which tool to apply and executes it.

The first tool is **ETag/MD5** — four operations for efficient, safe interactions with cloud storage (GCS). The platform is designed to host additional tools in the future.

### Goals

- Accept natural language input, reason about intent, select and execute the correct tool
- Solve four ETag/MD5 problems: delta sync, corruption prevention, optimistic concurrency, cache validation
- Run fully local with zero cloud dependencies (Phase C), then incrementally adopt cloud services (Phase B)
- Serve both end-users (who describe problems conversationally) and developers (who use the Java API directly)

### Non-Goals

- Web UI or REST API (future consideration, not in scope)
- Multi-turn autonomous planning (agent executes one tool per request, not chains)
- Real-time file watching or daemon mode

---

## 2. Architecture

### Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Agent framework | LangChain4j 1.x | Native Ollama support, `@Tool` annotations, built-in tool calling via AiServices |
| LLM (Phase C) | Ollama (local) | Zero cloud cost, model-agnostic, swap via config |
| LLM (Phase B) | OpenAI / Anthropic / Google | LangChain4j supports all; config-only switch |
| CLI | Picocli | Industry standard Java CLI, lightweight, no Spring overhead |
| Storage (Phase C) | Local filesystem | Simulates GCS bucket semantics (ETags, metadata, conditional ops) |
| Storage (Phase B) | Google Cloud Storage | Real GCS via `google-cloud-storage` SDK |
| Build | Maven (multi-module) | Matches existing project ecosystem |
| Java | 17 | Matches existing project standard |
| Logging | SLF4J API + Logback | SLF4J facade in library modules, Logback runtime binding in CLI only |

### High-Level Flow

```
User (natural language)
  │
  ▼
AgentCli (Picocli REPL)
  │
  ▼
AgentRunner (orchestrator)
  │
  ├──▶ ChatLanguageModel (Ollama / Cloud via LangChain4j)
  │       │
  │       ▼
  │     LLM reasons: "User wants delta sync → call deltaSyncTool"
  │       │
  │       ▼
  ├──▶ ToolRegistry (ServiceLoader discovery)
  │       │
  │       ▼
  │     EtagToolProvider.deltaSync(sourcePath, targetBucket)
  │       │
  │       ▼
  │     StorageAdapter (local FS or GCS)
  │       │
  │       ▼
  └──▶ AgentResponse → OutputFormatter → CLI output
```

### Dependency Flow Between Modules

```
agent-common ◄── agent-core ◄── tool-etag
                      ▲
                      │
                 agent-cli
                 agent-integration-tests
```

- `agent-common`: No dependencies on other modules. Shared kernel only.
- `agent-core`: Depends on `agent-common`. Owns the agent orchestration, LLM factory, tool registry.
- `tool-etag`: Depends on `agent-common` and `agent-core` (for `ToolProvider` SPI interface).
- `agent-cli`: Depends on `agent-core` and `tool-etag` (runtime). Thin entry point. Owns runtime config and Logback binding.
- `agent-integration-tests`: Depends on all modules. Test-scoped only.

---

## 3. Project Structure

**Base package:** `io.agenttoolbox`
(Follows reversed-domain convention per Oracle Java naming standards. Avoids collision risk of generic names like `com.toolbox`.)

```
agent-toolbox/
├── pom.xml                              # Parent POM (BOM imports, module aggregator, plugin mgmt)
├── mvnw                                 # Maven wrapper (no local Maven install needed)
├── mvnw.cmd
├── .mvn/wrapper/maven-wrapper.properties
├── .gitignore
├── .editorconfig
├── README.md
│
├── agent-common/                        # Shared kernel — no business logic
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/agenttoolbox/common/
│       │   ├── exception/
│       │   │   ├── AgentException.java          # Base unchecked exception
│       │   │   ├── LlmException.java
│       │   │   ├── StorageException.java
│       │   │   ├── HashMismatchException.java
│       │   │   └── PreconditionFailedException.java
│       │   ├── crypto/
│       │   │   └── Md5Hasher.java               # MD5 computation utility
│       │   ├── storage/
│       │   │   ├── StorageAdapter.java           # Interface (shared across storage tools)
│       │   │   └── ConditionalReadResult.java    # Response type for conditional reads
│       │   ├── model/
│       │   │   └── FileMetadata.java             # Path, size, hash, etag, lastModified
│       │   └── config/
│       │       └── SecretProvider.java           # Interface: env vars → file → Vault
│       └── test/java/io/agenttoolbox/common/
│           └── crypto/
│               └── Md5HasherTest.java
│
├── agent-core/                          # Agent loop engine — tool-agnostic
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/io/agenttoolbox/core/
│       │   │   ├── AgentRunner.java             # Builds AiServices, delegates to LangChain4j
│       │   │   ├── ToolRegistry.java            # ServiceLoader-based discovery
│       │   │   ├── ToolProvider.java            # SPI interface tools must implement
│       │   │   ├── health/
│       │   │   │   └── OllamaHealthCheck.java   # Verify Ollama is reachable at startup
│       │   │   ├── llm/
│       │   │   │   └── ChatModelFactory.java    # Creates ChatLanguageModel from config
│       │   │   ├── config/
│       │   │   │   └── AgentConfig.java         # YAML-driven config POJO
│       │   │   └── model/
│       │   │       ├── AgentRequest.java
│       │   │       └── AgentResponse.java
│       │   └── resources/
│       │       └── prompts/
│       │           └── system-prompt.txt         # Agent persona & instructions
│       └── test/java/io/agenttoolbox/core/
│           ├── AgentRunnerTest.java
│           ├── ToolRegistryTest.java
│           └── llm/
│               └── ChatModelFactoryTest.java
│
├── tool-etag/                           # ETag tool plugin (first tool)
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/io/agenttoolbox/tool/etag/
│       │   │   ├── EtagToolProvider.java        # Implements ToolProvider SPI
│       │   │   │                                # @Tool methods for 4 operations
│       │   │   ├── service/
│       │   │   │   ├── DeltaSyncService.java
│       │   │   │   ├── UploadValidationService.java
│       │   │   │   ├── ConcurrencyControlService.java
│       │   │   │   └── CacheValidationService.java
│       │   │   └── storage/
│       │   │       ├── LocalStorageAdapter.java  # Implements StorageAdapter from agent-common
│       │   │       └── GcsStorageAdapter.java    # Implements StorageAdapter (Phase B)
│       │   └── resources/
│       │       └── META-INF/services/
│       │           └── io.agenttoolbox.core.ToolProvider
│       └── test/java/io/agenttoolbox/tool/etag/
│           ├── service/
│           │   ├── DeltaSyncServiceTest.java
│           │   ├── UploadValidationServiceTest.java
│           │   ├── ConcurrencyControlServiceTest.java
│           │   └── CacheValidationServiceTest.java
│           └── storage/
│               └── LocalStorageAdapterTest.java
│
├── agent-cli/                           # CLI entry point — thin shell
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/io/agenttoolbox/cli/
│       │   │   ├── AgentCli.java               # Picocli main, interactive REPL
│       │   │   ├── OutputFormatter.java        # Adaptive: concise ↔ verbose
│       │   │   └── VerbosityMode.java          # Enum: CONCISE, VERBOSE, AUTO
│       │   └── resources/
│       │       ├── application.yaml            # Default config (lives in CLI, not core)
│       │       └── logback.xml                 # Logback binding (CLI module only)
│       └── test/java/io/agenttoolbox/cli/
│           └── OutputFormatterTest.java
│
├── agent-integration-tests/             # Cross-module integration tests
│   ├── pom.xml
│   └── src/test/java/io/agenttoolbox/it/
│       ├── AgentEtagIntegrationTest.java       # Full loop: CLI → LLM → tool → result
│       └── MockOllamaServer.java               # WireMock-based fake Ollama
│
└── docs/
    ├── model-recommendations.md         # Ollama model recs per RAM tier
    └── adding-a-new-tool.md             # Guide for plugin developers
```

### Key Structural Decisions

| Decision | Rationale |
|----------|-----------|
| `application.yaml` in `agent-cli`, not `agent-core` | The CLI is the entry point that owns runtime config. Core is a library. |
| `logback.xml` only in `agent-cli` | Library modules use SLF4J API only. The application (CLI) picks the binding. |
| No `tool-descriptor.txt` | LangChain4j auto-generates tool descriptions from `@Tool` and `@P` annotations. |
| No custom `LlmClient` interface | LangChain4j's `ChatLanguageModel` is the abstraction. A wrapper adds nothing. |
| `ConditionalReadResult` instead of `Optional<byte[]>` | Need to distinguish "not modified" from "empty file" with proper status. |

---

## 4. The Four ETag Tools

Each tool is a `@Tool`-annotated method on `EtagToolProvider`. LangChain4j auto-generates tool descriptions for the LLM from the `@Tool` value and `@P` parameter descriptions.

### 4.1 Delta Sync

**Purpose:** Sync a local directory to a storage bucket, uploading only files whose MD5 hash has changed.

**Tool signature:**
```java
@Tool("Sync files from a local directory to a storage bucket. Only uploads files whose content has changed by comparing MD5 hashes. Use when the user wants to sync, backup, or upload a folder efficiently.")
String deltaSync(
    @P("Absolute path to the local directory to sync from") String localPath,
    @P("Name of the target storage bucket") String bucketName
)
```

**Flow:**
1. List all files in `localPath` recursively
2. For each file, compute local MD5 via `Md5Hasher`
3. Fetch remote `FileMetadata` (including stored MD5) from `StorageAdapter`
4. If hashes differ or remote file missing → upload
5. Return summary: total files, synced count, skipped count, bytes transferred

**Local FS mock behavior:** The "bucket" is a directory (e.g., `~/.agent-toolbox/buckets/{bucketName}/`). Each file has a companion `.metadata.json` sidecar storing its MD5 hash and last-modified timestamp, simulating GCS object metadata.

### 4.2 Upload Validation

**Purpose:** Upload a file with MD5 integrity check. Reject the upload if the file was corrupted during transfer.

**Tool signature:**
```java
@Tool("Upload a file to storage with integrity validation. Computes MD5 before upload and verifies the storage backend received an identical copy. Use for large files or unreliable networks.")
String uploadWithValidation(
    @P("Absolute path to the local file to upload") String localFilePath,
    @P("Name of the target storage bucket") String bucketName,
    @P("Destination key/path within the bucket") String destinationKey
)
```

**Flow:**
1. Compute local MD5 of `localFilePath`
2. Upload file to `StorageAdapter` with MD5 attached in request
3. Storage adapter verifies MD5 on receipt
4. If mismatch → throw `HashMismatchException`, file is rejected
5. Return: uploaded file path, size, verified MD5

**Local FS mock behavior:** On copy, independently re-hash the destination file and compare. To simulate corruption for testing, support a `--simulate-corruption` flag that flips a byte during copy.

### 4.3 Concurrency Control (Optimistic Locking)

**Purpose:** Update a file in storage only if it hasn't been modified since it was last read. Prevents overwrite race conditions.

**Tool signature:**
```java
@Tool("Update a file in storage with concurrency protection. Uses ETags to ensure no one else modified the file since you last read it. Use when multiple users or processes might edit the same file.")
String conditionalUpdate(
    @P("Name of the storage bucket") String bucketName,
    @P("Key/path of the file in the bucket") String fileKey,
    @P("Absolute path to the local file containing updated content") String localFilePath,
    @P("ETag from when the file was originally read (used for conflict detection)") String knownEtag
)
```

**Flow:**
1. The caller provides `knownEtag` — the ETag obtained when the file was originally read/downloaded
2. Read updated content from `localFilePath`
3. Attempt upload with `If-Match: {knownEtag}` condition via `StorageAdapter.conditionalWrite()`
4. If ETag matches current → upload succeeds, return new ETag
5. If ETag changed → throw `PreconditionFailedException` with message: "File was modified by another process. Current ETag: {current}. Your ETag: {knownEtag}. Please reload and retry."

**Why `knownEtag` is a parameter:** The tool must not read-then-write in one call — that would always succeed and defeat the purpose of optimistic locking. The ETag must come from a prior read (potentially minutes or hours ago) to detect intervening changes.

**Local FS mock behavior:** Store ETag (MD5 of content) in `.metadata.json` sidecar. On conditional update, compare provided ETag against current sidecar value before overwriting.

### 4.4 Cache Validation

**Purpose:** Check if a remote file has changed without downloading it. Returns 304 (not modified) or the new content.

**Tool signature:**
```java
@Tool("Check if a remote file has changed without downloading it. Uses ETags to avoid unnecessary data transfer. Use for caching, polling for updates, or bandwidth-sensitive clients.")
String cacheValidation(
    @P("Name of the storage bucket") String bucketName,
    @P("Key/path of the file in the bucket") String fileKey,
    @P("ETag from the last known version of the file") String knownEtag
)
```

**Flow:**
1. Send request to `StorageAdapter.conditionalRead()` with `If-None-Match: {knownEtag}`
2. Returns `ConditionalReadResult` with status:
   - `NOT_MODIFIED` → ETag matches, zero bytes transferred
   - `MODIFIED` → new content, new ETag, byte count
3. Format result as string for LLM response

**Local FS mock behavior:** Compute current MD5 from file on disk, compare to `knownEtag`. If same → return not-modified. If different → return file content and new hash.

---

## 5. Agent Loop Design

### LangChain4j AiServices Pattern

The agent uses LangChain4j's `AiServices` which handles the full tool-calling loop internally: send messages to LLM, parse tool-call responses, invoke `@Tool` methods, feed results back to LLM.

```
1. USER:    "Sync my downloads folder to the backup bucket"
2. REASON:  LLM analyzes intent → "User wants delta sync between local dir and bucket"
3. ACT:     LLM emits tool call → deltaSync(localPath="/Users/x/Downloads", bucketName="backup")
4. OBSERVE: LangChain4j invokes @Tool method → returns result string
5. RESPOND: LLM receives tool result, formats final answer for user
```

### LangChain4j Wiring

```java
interface AgentService {
    @SystemMessage(fromResource = "prompts/system-prompt.txt")
    String chat(String userMessage);
}

AgentService agent = AiServices.builder(AgentService.class)
    .chatLanguageModel(chatModel)        // From ChatModelFactory
    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
    .tools(toolRegistry.discoverTools()) // All @Tool objects from SPI
    .build();
```

**Key points:**
- `@SystemMessage(fromResource = ...)` loads the prompt from classpath — no custom `Resource.load()` needed
- `AiServices` auto-discovers `@Tool`-annotated methods and generates tool specifications for the LLM
- `@P` annotations on parameters provide descriptions the LLM uses for parameter extraction
- Tool return type is `String` — LangChain4j passes it directly to the LLM for final response formatting
- `MessageWindowChatMemory` requires at least 3 messages capacity for tool calling to work (system + user + assistant + tool result cycle)

### Conversation Memory

- Uses LangChain4j's `MessageWindowChatMemory` directly (no custom wrapper needed)
- Window size: 20 messages (configurable via `agent.memory.max-messages`)
- Session-scoped: new instance per CLI session, cleared on exit
- Enables follow-up questions: "now check just the .csv files" references the prior sync context

### System Prompt

```
You are Agent Toolbox, a helpful assistant that solves file storage problems.
You have access to tools for working with files and ETags/MD5 hashes.

When a user describes a problem:
1. Identify which tool best solves it
2. Extract the required parameters from their message
3. If parameters are missing, ask the user to provide them
4. Execute the tool and report results

Respond concisely unless the user asks for explanation.
When the user asks "why", "explain", or "how does this work", provide
educational detail about the ETag/MD5 mechanism being used.
```

Note: Tool descriptions are NOT listed in the system prompt. LangChain4j auto-injects tool specifications from `@Tool` annotations into the LLM request.

### AgentRunner Role

`AgentRunner` is a thin orchestrator, not a custom ReAct loop:
1. Calls `ChatModelFactory.create()` to build the `ChatLanguageModel`
2. Calls `ToolRegistry.discoverTools()` to collect SPI-discovered tool objects
3. Builds the `AiServices` instance with model, memory, and tools
4. Exposes a `chat(String message) → String` method for the CLI to call
5. Handles startup health check via `OllamaHealthCheck` (if provider is Ollama)

---

## 6. Storage Abstraction

### StorageAdapter Interface (in `agent-common`)

The interface lives in `agent-common` so any future storage tool (`tool-s3`, `tool-azure-blob`) can implement it without depending on `tool-etag`. Concrete implementations live in their respective tool modules.

```java
public interface StorageAdapter {

    FileMetadata getMetadata(String bucket, String key);

    byte[] read(String bucket, String key);

    FileMetadata write(String bucket, String key, byte[] content, String expectedMd5);

    FileMetadata conditionalWrite(String bucket, String key, byte[] content, String ifMatchEtag);

    ConditionalReadResult conditionalRead(String bucket, String key, String ifNoneMatchEtag);

    List<FileMetadata> list(String bucket, String prefix);

    boolean exists(String bucket, String key);
}
```

### ConditionalReadResult

```java
public record ConditionalReadResult(
    boolean modified,
    byte[] content,       // null if not modified
    String etag,          // current ETag (always present)
    long contentLength    // 0 if not modified
) {
    public static ConditionalReadResult notModified(String etag) {
        return new ConditionalReadResult(false, null, etag, 0);
    }

    public static ConditionalReadResult modified(byte[] content, String etag) {
        return new ConditionalReadResult(true, content, etag, content.length);
    }
}
```

### LocalStorageAdapter (Phase C)

Simulates GCS bucket semantics on the local filesystem:

- **Bucket root:** `~/.agent-toolbox/buckets/{bucketName}/`
- **File storage:** Files stored at their key path under bucket root
- **Metadata:** `.metadata.json` sidecar files alongside each stored file containing:
  ```json
  {
    "md5Hash": "d41d8cd98f00b204e9800998ecf8427e",
    "etag": "d41d8cd98f00b204e9800998ecf8427e",
    "size": 1024,
    "lastModified": "2026-06-04T10:30:00Z",
    "contentType": "application/octet-stream"
  }
  ```
- **Conditional operations:** Read sidecar ETag, compare, succeed or throw
- **MD5 validation on write:** Hash content after write, compare to `expectedMd5`
- **Thread safety:** File-level locking via `java.nio.channels.FileLock` for concurrent access

### GcsStorageAdapter (Phase B)

- Wraps `com.google.cloud.storage.Storage` client
- Maps `StorageAdapter` methods to GCS API calls
- Uses `BlobInfo.newBuilder().setMd5Hash()` for upload validation
- Uses `Storage.BlobTargetOption.ifGenerationMatch()` for conditional writes
- Uses `Storage.BlobSourceOption.ifGenerationNotMatch()` for conditional reads
- Credentials via `SecretProvider` (service account JSON or Application Default Credentials)

### Switching Adapters

Configured in `application.yaml`:

```yaml
storage:
  provider: local    # "local" or "gcs"
  local:
    bucket-root: ${user.home}/.agent-toolbox/buckets
  gcs:
    project-id: my-project
    credentials: ${GCS_CREDENTIALS_PATH}
```

`StorageAdapter` implementation is selected at startup based on `storage.provider`.

---

## 7. LLM Abstraction

### ChatModelFactory

No custom `LlmClient` interface. LangChain4j's `ChatLanguageModel` is the abstraction. `ChatModelFactory` creates the correct implementation from config:

```java
public class ChatModelFactory {

    public static ChatLanguageModel create(AgentConfig config, SecretProvider secrets) {
        return switch (config.getLlm().getProvider()) {
            case "ollama" -> OllamaChatModel.builder()
                .baseUrl(config.getLlm().getOllama().getBaseUrl())
                .modelName(config.getLlm().getOllama().getModel())
                .temperature(config.getLlm().getOllama().getTemperature())
                .timeout(Duration.ofSeconds(config.getLlm().getOllama().getTimeoutSeconds()))
                .logRequests(config.getLogging().getLevel().equals("DEBUG"))
                .logResponses(config.getLogging().getLevel().equals("DEBUG"))
                .build();
            case "openai" -> OpenAiChatModel.builder()
                .apiKey(secrets.get("OPENAI_API_KEY").orElseThrow(
                    () -> new LlmException("OPENAI_API_KEY not configured")))
                .modelName(config.getLlm().getOpenai().getModel())
                .build();
            case "anthropic" -> AnthropicChatModel.builder()
                .apiKey(secrets.get("ANTHROPIC_API_KEY").orElseThrow(
                    () -> new LlmException("ANTHROPIC_API_KEY not configured")))
                .modelName(config.getLlm().getAnthropic().getModel())
                .build();
            default -> throw new LlmException(
                "Unknown LLM provider: " + config.getLlm().getProvider()
                + ". Supported: ollama, openai, anthropic")
        };
    }
}
```

### Startup Health Check

```java
public class OllamaHealthCheck {
    /**
     * Verifies Ollama is reachable before building the agent.
     * Called at startup when llm.provider=ollama.
     * Hits GET {baseUrl}/api/tags to confirm the server is running
     * and the configured model is pulled.
     */
    public static void verify(AgentConfig.OllamaConfig config) { ... }
}
```

Fails fast with a clear error if Ollama is not running:
```
ERROR: Cannot connect to Ollama at http://localhost:11434
  Is Ollama running? Start it with: ollama serve
  Then pull a model: ollama pull llama3.1:8b
```

### Configuration

```yaml
llm:
  provider: ollama        # "ollama", "openai", "anthropic"
  ollama:
    base-url: http://localhost:11434
    model: llama3.1:8b
    temperature: 0.1      # Low temp for tool selection accuracy
    timeout-seconds: 30
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o-mini
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-sonnet-4-20250514
```

### Model Recommendations (docs/model-recommendations.md)

| RAM | Recommended Model | Notes |
|-----|------------------|-------|
| 8GB or less | `llama3.2:3b`, `phi3:mini` | Fast responses, basic tool selection |
| 16GB | `llama3.1:8b`, `mistral:7b`, `gemma2:9b` | Good balance for agentic reasoning |
| 32GB+ | `llama3.1:70b`, `mixtral:8x7b` | Best reasoning, slower startup |

---

## 8. CLI Design

### Entry Point

```
$ agent-toolbox
Agent Toolbox v0.1.0 (Ollama: llama3.1:8b)
Type your request, or 'help' for commands. 'quit' to exit.

> sync my downloads folder to the backup bucket
Synced 12/1,847 files. 1,835 unchanged. 2.3MB transferred.

> why did it skip the rest?
Those 1,835 files were skipped because their MD5 hashes matched the
copies already in the backup bucket. Delta sync computes an MD5 hash
of each local file and compares it to the stored hash in the bucket's
metadata. When they match, the file content is identical — no upload
needed. This saves bandwidth and API costs.

> quit
```

### Picocli Structure

```java
@Command(name = "agent-toolbox", mixinStandardHelpOptions = true,
         version = "0.1.0",
         description = "LLM-powered agent for file storage operations")
public class AgentCli implements Runnable {

    @Option(names = {"-v", "--verbose"}, description = "Always use verbose output")
    boolean verbose;

    @Option(names = {"--model"}, description = "Override Ollama model name")
    String model;

    @Option(names = {"--config"}, description = "Path to config YAML")
    Path configPath;

    @Parameters(description = "One-shot query (skip REPL)")
    String[] query;
}
```

### Two Modes

1. **REPL mode** (no args): Interactive session with conversation memory
2. **One-shot mode** (args provided): `agent-toolbox "sync ~/data to backup"` — execute and exit

### Verbosity (VerbosityMode)

| Mode | Trigger | Behavior |
|------|---------|----------|
| `CONCISE` | Default | Results only: counts, sizes, status |
| `VERBOSE` | `--verbose` flag | Full ETag explanations, step-by-step narration |
| `AUTO` | Default in REPL | Concise until user asks "why?", "explain", "how does this work?" — then switches to verbose for that response only |

### Graceful Shutdown

Register a JVM shutdown hook to handle SIGINT (Ctrl+C) and SIGTERM:
- Print a clean exit message instead of a stack trace
- Flush any pending log output
- Close the Ollama HTTP connection pool

```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    System.out.println("\nGoodbye.");
}));
```

---

## 9. Error Handling

### Exception Hierarchy

```
AgentException (base, unchecked RuntimeException)
├── LlmException
│   ├── LlmTimeoutException          # Ollama didn't respond in time
│   ├── LlmParseException            # Couldn't parse tool call from LLM output
│   └── LlmUnavailableException      # Ollama not running / model not pulled
├── StorageException
│   ├── BucketNotFoundException       # Bucket/directory doesn't exist
│   ├── FileNotFoundException         # Key not found in bucket
│   ├── HashMismatchException         # MD5 validation failed on upload
│   └── PreconditionFailedException   # ETag mismatch (412 equivalent)
└── ToolException
    ├── ToolNotFoundException         # LLM requested unknown tool
    └── ToolExecutionException        # Tool threw during execution
```

### CLI Error Display

Errors surface as user-friendly messages, not stack traces:

```
> upload corrupted-file.dat to archive
ERROR: Upload rejected — MD5 mismatch.
  Local MD5:  a1b2c3d4e5f6...
  Remote MD5: f6e5d4c3b2a1...
  The file was corrupted during transfer. Try again.

> update config.json in shared-config
ERROR: Conflict — file was modified by another process.
  Your ETag:    abc123 (from 2 minutes ago)
  Current ETag: def456
  Please reload the file and retry your changes.
```

In `--verbose` mode, the full stack trace is appended after the user-friendly message.

---

## 10. Configuration

### application.yaml (default, in agent-cli/src/main/resources/)

```yaml
agent:
  name: Agent Toolbox
  version: 0.1.0
  memory:
    max-messages: 20

llm:
  provider: ollama
  ollama:
    base-url: http://localhost:11434
    model: llama3.1:8b
    temperature: 0.1
    timeout-seconds: 30

storage:
  provider: local
  local:
    bucket-root: ${user.home}/.agent-toolbox/buckets

output:
  verbosity: auto    # auto, concise, verbose
  color: true

logging:
  level: INFO
  file: ${user.home}/.agent-toolbox/logs/agent.log
```

### Config Loading

`AgentConfig` is a POJO loaded via SnakeYAML. At startup:
1. Load default `application.yaml` from classpath (bundled in JAR)
2. Overlay with `~/.agent-toolbox/config.yaml` if present
3. Overlay with file at `--config` path if provided
4. Apply CLI flag overrides (`--model`, `--verbose`)
5. Resolve `${ENV_VAR}` placeholders from environment

### Configuration Precedence

1. CLI flags (`--model`, `--verbose`) — highest
2. Environment variables (`AGENT_LLM_PROVIDER`, `OLLAMA_BASE_URL`)
3. User config file (`~/.agent-toolbox/config.yaml`)
4. Default `application.yaml` in JAR — lowest

### SecretProvider

```java
public interface SecretProvider {
    Optional<String> get(String key);
}
```

Implementations (checked in order):
1. `EnvVarSecretProvider` — reads `OPENAI_API_KEY`, `GCS_CREDENTIALS_PATH`, etc.
2. `FileSecretProvider` — reads from `~/.agent-toolbox/secrets.yaml` (git-ignored)
3. Future: `VaultSecretProvider`, `GcpSecretManagerProvider`

---

## 11. Logging Strategy

### Principle: SLF4J Facade in Libraries, Binding in Application

| Module | Dependency | Scope |
|--------|-----------|-------|
| `agent-common` | `slf4j-api` | compile |
| `agent-core` | `slf4j-api` | compile |
| `tool-etag` | `slf4j-api` | compile |
| `agent-cli` | `logback-classic` | runtime (brings in slf4j-api transitively) |
| `agent-integration-tests` | `logback-classic` | test |

This follows the SLF4J best practice: library modules never depend on a concrete logging implementation. Only the application entry point (`agent-cli`) binds SLF4J to Logback.

---

## 12. Testing Strategy

### Unit Tests (per module)

| Module | What's Tested | Approach |
|--------|--------------|----------|
| `agent-common` | `Md5Hasher`, `ConditionalReadResult`, exceptions | Pure JUnit 5. No mocks needed. |
| `agent-core` | `AgentRunner`, `ToolRegistry`, `ChatModelFactory` | Mock `ChatLanguageModel` and `ToolProvider`. Verify wiring. |
| `tool-etag` | All 4 services | Mock `StorageAdapter`. Verify hash comparison logic, conditional logic, error paths. |
| `agent-cli` | `OutputFormatter` | Verify concise vs verbose output for same result string. |

### Integration Tests (`agent-integration-tests`)

| Test | What It Validates |
|------|------------------|
| `AgentEtagIntegrationTest` | Full loop: user query → LLM → tool selection → execution → response |
| `MockOllamaServer` | WireMock server returning canned tool-call responses. No real LLM needed for CI. |
| Storage integration | `LocalStorageAdapter` with real filesystem I/O. Temp directories via JUnit `@TempDir`. |

### Test Pyramid

```
         /  E2E  \         agent-integration-tests (MockOllama + real FS)
        /----------\
       / Integration \      tool-etag services + LocalStorageAdapter
      /----------------\
     /    Unit Tests     \   Md5Hasher, OutputFormatter, ToolRegistry, ChatModelFactory
    /______________________\
```

### CI Considerations

- All unit and integration tests run without Ollama or network access
- `MockOllamaServer` (WireMock) provides deterministic LLM responses
- Real Ollama tests are opt-in via Maven profile: `./mvnw test -Preal-ollama`

---

## 13. Infrastructure Phases

### Phase C — Local-First (Initial)

Everything runs on developer's machine, zero cloud dependencies.

| Component | Implementation |
|-----------|---------------|
| LLM | Ollama running locally (`localhost:11434`) |
| Storage | `LocalStorageAdapter` — filesystem-backed bucket simulation |
| Secrets | Environment variables only |
| Distribution | Fat JAR via Maven Shade plugin |

**Setup steps:**
1. Install Ollama: `brew install ollama`
2. Pull a model: `ollama pull llama3.1:8b`
3. Start Ollama: `ollama serve`
4. Build: `./mvnw clean install`
5. Run: `java -jar agent-cli/target/agent-toolbox.jar`

### Phase B — Incremental Cloud Adoption

Swap components one at a time. Each swap is a config change, not a code rewrite.

| Step | Change | Config |
|------|--------|--------|
| B1 | Real GCS storage | `storage.provider: gcs` + service account |
| B2 | Cloud LLM | `llm.provider: openai` + API key |
| B3 | Remote secret management | Add Vault/GCP Secret Manager provider |
| B4 | Native binary | GraalVM native-image via `native-maven-plugin` |

Each step is independently deployable. You can run cloud LLM + local storage, or Ollama + real GCS.

---

## 14. Tool Plugin System (Adding Future Tools)

### SPI Contract

Every tool module must:

1. Implement `ToolProvider` interface from `agent-core`:
   ```java
   public interface ToolProvider {
       String name();
       String description();
       Object toolInstance();  // Object with @Tool-annotated methods
   }
   ```

2. Register via `META-INF/services/io.agenttoolbox.core.ToolProvider`

3. Declare dependency on `agent-common` and `agent-core` in its `pom.xml`

### Discovery

`ToolRegistry` uses `ServiceLoader<ToolProvider>` at startup:
1. Scans classpath for `META-INF/services/io.agenttoolbox.core.ToolProvider`
2. Instantiates each `ToolProvider`
3. Collects `toolInstance()` objects (which have `@Tool`-annotated methods)
4. Passes them to LangChain4j's `AiServices.builder().tools(...)`

### Adding a New Tool (e.g., `tool-redis`)

1. Create `tool-redis/` module with `pom.xml`
2. Implement `ToolProvider` with `@Tool` methods
3. Add `META-INF/services/` file
4. Add module to parent `pom.xml` `<modules>` list
5. The agent discovers it automatically on next build

Documented in `docs/adding-a-new-tool.md`.

---

## 15. Key Dependencies (Parent POM)

Managed via LangChain4j BOM for consistent versioning across all LangChain4j modules.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>${langchain4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

| Dependency | Version | Purpose | Module |
|-----------|---------|---------|--------|
| `dev.langchain4j:langchain4j-bom` | 1.0.0+ | BOM for all LangChain4j modules | parent POM |
| `dev.langchain4j:langchain4j` | (from BOM) | Core: `@Tool`, `@P`, `AiServices` | agent-core |
| `dev.langchain4j:langchain4j-ollama` | (from BOM) | `OllamaChatModel` | agent-core |
| `dev.langchain4j:langchain4j-open-ai` | (from BOM) | `OpenAiChatModel` (Phase B) | agent-core, optional |
| `dev.langchain4j:langchain4j-anthropic` | (from BOM) | `AnthropicChatModel` (Phase B) | agent-core, optional |
| `info.picocli:picocli` | 4.7+ | CLI framework | agent-cli |
| `info.picocli:picocli-codegen` | 4.7+ | Compile-time annotation processing | agent-cli, provided |
| `com.google.cloud:google-cloud-storage` | 2.x | GCS SDK (Phase B) | tool-etag, optional |
| `org.yaml:snakeyaml` | 2.x | YAML config parsing | agent-core |
| `org.slf4j:slf4j-api` | 2.x | Logging facade | agent-common, agent-core, tool-etag |
| `ch.qos.logback:logback-classic` | 1.5+ | Logging runtime binding | agent-cli only |
| `org.junit.jupiter:junit-jupiter` | 5.10+ | Testing | all, test scope |
| `org.mockito:mockito-core` | 5.x | Mocking | agent-core, tool-etag, test scope |
| `org.wiremock:wiremock` | 3.x | HTTP mocking for Ollama | integration-tests, test scope |
| `org.assertj:assertj-core` | 3.x | Fluent test assertions | all, test scope |

---

## 16. .gitignore

```gitignore
# Maven build output
target/
!.mvn/wrapper/maven-wrapper.jar

# IDE files
.idea/
*.iml
.project
.classpath
.settings/
.vscode/
*.swp
*.swo

# Compiled Java classes
*.class

# Package files
*.jar
*.war
*.ear

# Log files
*.log
logs/

# OS files
.DS_Store
Thumbs.db

# Environment & secrets (NEVER commit)
.env
.env.*
application-local.yaml
application-dev.yaml
secrets.yaml
secrets.json
*.pem
*.key

# Agent Toolbox runtime data
.agent-toolbox/
```

---

## 17. Build & Run Commands

All build operations use Maven wrapper (`./mvnw`). No Makefile needed.

### Build Commands

```bash
./mvnw clean install                          # Build all modules
./mvnw clean install -DskipTests              # Build without tests
./mvnw test                                   # Run unit tests
./mvnw verify -pl agent-integration-tests     # Run integration tests
./mvnw versions:display-dependency-updates    # Check for dependency updates
./mvnw -T 1C clean install                    # Parallel build (1 thread per core)
```

### Run Commands

```bash
java -jar agent-cli/target/agent-toolbox.jar                # REPL mode
java -jar agent-cli/target/agent-toolbox.jar --verbose      # REPL, verbose
java -jar agent-cli/target/agent-toolbox.jar "sync my data" # One-shot mode
```

### Ollama Setup (documented in README.md)

```bash
brew install ollama        # Install Ollama (macOS)
ollama serve               # Start Ollama server
ollama pull llama3.1:8b    # Pull recommended model
```

### Native Build (Phase B4, via Maven plugin)

```bash
./mvnw -Pnative package    # Builds native binary via native-maven-plugin
```

---

## Decisions Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Agent framework | LangChain4j 1.x | Best Java tool-calling support, native Ollama provider, active community |
| CLI framework | Picocli | Lightweight, no Spring overhead, industry standard for Java CLIs |
| Project structure | Maven multi-module | Matches existing ecosystem, clean module boundaries |
| Base package | `io.agenttoolbox` | Follows Oracle reversed-domain convention, avoids generic collision |
| Storage abstraction | `StorageAdapter` interface in common | Swap local ↔ GCS via config; shareable across future storage tools |
| Tool discovery | Java SPI (ServiceLoader) | No framework coupling, standard Java mechanism |
| LLM abstraction | LangChain4j `ChatLanguageModel` directly | No unnecessary wrapper — use the framework's own abstraction |
| Tool return type | `String` (not custom `ToolResult`) | LangChain4j passes String directly to LLM; custom objects get JSON-serialized |
| Output verbosity | Adaptive (auto mode) | Concise by default, verbose on demand |
| Phase strategy | C → B (local first) | Zero cloud cost to start, incremental adoption |
| Logging | SLF4J API in libraries, Logback in CLI | Industry standard; library modules never bind a logging implementation |
| Config location | `application.yaml` in agent-cli | Entry point owns runtime config; core is a library |
| Dependency versioning | LangChain4j BOM import | Single place to manage all LangChain4j module versions |

---

## References & Best Practices

| Topic | Reference |
|-------|-----------|
| LangChain4j Tools | https://docs.langchain4j.dev/tutorials/tools/ |
| LangChain4j AiServices | https://docs.langchain4j.dev/tutorials/ai-services/ |
| LangChain4j Chat Memory | https://docs.langchain4j.dev/tutorials/chat-memory/ |
| LangChain4j Ollama Integration | https://docs.langchain4j.dev/integrations/language-models/ollama/ |
| Picocli User Guide | https://picocli.info/ |
| Java Package Naming | https://docs.oracle.com/javase/tutorial/java/package/namingpkgs.html |
| Maven Multi-Module Guide | https://maven.apache.org/guides/mini/guide-multiple-modules.html |
| Java SPI (ServiceLoader) | https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html |
| SLF4J Best Practices | https://www.slf4j.org/manual.html |
| GCS Java Client | https://cloud.google.com/storage/docs/reference/libraries#client-libraries-install-java |
| HTTP ETags (RFC 7232) | https://datatracker.ietf.org/doc/html/rfc7232 |
| Ollama API Reference | https://github.com/ollama/ollama/blob/main/docs/api.md |
