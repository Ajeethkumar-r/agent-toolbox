# RAG Tool Module Design Spec

**Date:** 2026-06-06
**Scope:** New `tool-rag` Maven module for semantic search across bucket contents
**Modules affected:** `tool-rag` (new), `agent-core` (config), `agent-cli` (config YAML)

---

## Overview

Add a `tool-rag` module that gives the agent semantic understanding of file contents stored in buckets. The agent can ingest files (text, PDF, DOCX), convert them to vector embeddings via Ollama, and search them by meaning.

Three `@Tool` methods exposed to the LLM:
1. `ingestBucket` — index all files in a bucket for semantic search
2. `searchContent` — find relevant chunks by meaning across an indexed bucket
3. `askAboutFile` — answer a question about a specific file without requiring prior ingestion

---

## Architecture

### Plugin Pattern

Follows the existing SPI pattern: `RagToolProvider` implements `ToolProvider`, registered via `META-INF/services`. No changes to `agent-core` orchestration, `agent-common` interfaces, or `agent-cli` code — only config additions.

### Data Flow

**Ingestion:**
```
StorageAdapter.list(bucket) → file keys
StorageAdapter.read(bucket, key) → bytes
DocumentParserFactory.forFile(key) → parser (Text / PDF / DOCX)
parser.parse(InputStream) → Document
DocumentSplitters.recursive(500, 50) → List<TextSegment>
OllamaEmbeddingModel.embed(segment) → Embedding vector
InMemoryEmbeddingStore.add(embedding, segment)
InMemoryEmbeddingStore.serializeToFile(~/.agent-toolbox/embeddings/bucket.json)
```

**Search:**
```
OllamaEmbeddingModel.embed(query) → query vector
InMemoryEmbeddingStore.findRelevant(queryVector, maxResults=3, minScore=0.5)
→ List<EmbeddingMatch<TextSegment>> → formatted string for LLM
```

**Ask About File (no prior ingestion needed):**
```
StorageAdapter.read(bucket, key) → bytes
DocumentParserFactory.forFile(key) → parser
parser.parse(InputStream) → Document
DocumentSplitters.recursive(500, 50) → List<TextSegment>
OllamaEmbeddingModel.embedAll(segments) → List<Embedding>
OllamaEmbeddingModel.embed(question) → query vector
Find top matches by cosine similarity in-memory (no store needed)
→ return matched chunks as answer context
```

---

## Components

### New Files (tool-rag module)

| File | Responsibility |
|------|----------------|
| `RagTools.java` | 3 `@Tool` methods: `ingestBucket`, `searchContent`, `askAboutFile` |
| `RagToolProvider.java` | SPI provider — implements `ToolProvider`, creates and configures `RagTools` |
| `IngestionService.java` | Reads files from StorageAdapter, parses, chunks, embeds, stores |
| `SearchService.java` | Queries embedding store, formats ranked results |
| `EmbeddingStoreManager.java` | Load/save `InMemoryEmbeddingStore` per bucket (JSON persistence to disk) |
| `DocumentParserFactory.java` | Selects parser by file extension (text, PDF, DOCX) |

### Modified Files

| File | Change |
|------|--------|
| `pom.xml` (parent) | Add `tool-rag` to `<modules>` |
| `agent-core/.../config/AgentConfig.java` | Add `EmbeddingSection` with nested config |
| `agent-cli/src/main/resources/application.yaml` | Add `embedding` config section |
| `agent-cli/pom.xml` | Add `tool-rag` as runtime dependency |
| `agent-core/src/main/resources/prompts/system-prompt.txt` | Add RAG patterns |
| `agent-core/src/test/resources/test-config.yaml` | Add embedding test config |

---

## Document Parsing

File type is determined by extension:

| Extension | Parser | Dependency |
|-----------|--------|------------|
| `.pdf` | `ApachePdfBoxDocumentParser` | `langchain4j-document-parser-apache-pdfbox` |
| `.docx`, `.doc` | `ApachePoiDocumentParser` | `langchain4j-document-parser-apache-poi` |
| `.xlsx`, `.pptx` | `ApachePoiDocumentParser` | `langchain4j-document-parser-apache-poi` |
| `.txt`, `.md`, `.csv`, `.json`, `.yaml`, others | `TextDocumentParser` | Built into `langchain4j-core` |

```java
public final class DocumentParserFactory {
    public static DocumentParser forFile(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf"))  return new ApachePdfBoxDocumentParser();
        if (lower.endsWith(".docx") || lower.endsWith(".doc"))  return new ApachePoiDocumentParser();
        if (lower.endsWith(".xlsx") || lower.endsWith(".pptx")) return new ApachePoiDocumentParser();
        return new TextDocumentParser();
    }
}
```

Files that fail to parse (e.g., binary files, images) are skipped with a warning logged.

---

## Vector Storage

Uses LangChain4j's `InMemoryEmbeddingStore` with its built-in JSON serialization.

### Storage Layout

```
~/.agent-toolbox/
├── buckets/                      ← existing file storage
│   ├── my-bucket/
│   └── docs/
├── chat-memory-default.json      ← existing chat memory
└── embeddings/                   ← NEW
    ├── my-bucket.json            ← serialized vectors for my-bucket
    └── docs.json                 ← serialized vectors for docs
```

### Lifecycle

- **On ingest:** Vectors are stored in memory AND serialized to `embeddings/{bucket}.json`
- **On search:** If not in memory, load from `embeddings/{bucket}.json`. If no file exists, return "bucket not indexed" error with ACTION hint.
- **On re-ingest:** Existing store for that bucket is replaced (full re-index)
- **Invalidation:** When files are modified via EtagTools/BrowserTools, the embedding store is NOT auto-invalidated. The user must re-ingest. This is a deliberate simplification — auto-invalidation is a Phase B concern.

### EmbeddingStoreManager

```java
public class EmbeddingStoreManager {
    private final Path storePath;
    private final Map<String, InMemoryEmbeddingStore<TextSegment>> stores = new ConcurrentHashMap<>();

    /** Get or load store for a bucket. Returns null if not indexed. */
    public InMemoryEmbeddingStore<TextSegment> getStore(String bucketName) { ... }

    /** Create/replace store for a bucket and persist to disk. */
    public void saveStore(String bucketName, InMemoryEmbeddingStore<TextSegment> store) { ... }
}
```

---

## Tool Definitions

### `ingestBucket`

```java
@Tool("Index all files in a storage bucket for semantic search. Must be called before searchContent.")
public String ingestBucket(
    @P("Name of the storage bucket to index") String bucketName)
```

**Behavior:**
1. List all files in the bucket via `StorageAdapter.list()`
2. For each file: read bytes, parse with appropriate parser, split into chunks
3. Add source file metadata to each `TextSegment` (file key, bucket name)
4. Embed all chunks via `OllamaEmbeddingModel`
5. Store in `InMemoryEmbeddingStore`, serialize to disk
6. Return summary: "Indexed X files (Y chunks) in bucket 'name'"

**Progress:** Print progress per file: `>> Indexing file.txt (3 chunks)...`

**Error handling:** Files that fail to parse are skipped (logged as warning), not fatal.

### `searchContent`

```java
@Tool("Search indexed bucket contents by meaning. Call ingestBucket first if not already indexed.")
public String searchContent(
    @P("The search query describing what you're looking for") String query,
    @P("Name of the storage bucket to search") String bucketName)
```

**Behavior:**
1. Load embedding store for bucket (from memory or disk)
2. If not indexed: return structured error with ACTION hint to call `ingestBucket`
3. Embed the query
4. Find top 3 matches with minScore >= 0.5
5. Return formatted results with source file, relevance score, and chunk text

**Output format:**
```
Found 3 results for "authentication" in bucket "docs":

[1] (score: 0.92) source: auth-guide.txt
    "Authentication is handled via JWT tokens issued by the auth service..."

[2] (score: 0.85) source: api-docs.md
    "All API endpoints require a Bearer token in the Authorization header..."

[3] (score: 0.71) source: config.yaml
    "auth.provider: jwt\nauth.expiry: 3600\nauth.refresh: true..."
```

### `askAboutFile`

```java
@Tool("Answer a question about a specific file's content without requiring prior indexing")
public String askAboutFile(
    @P("Name of the storage bucket") String bucketName,
    @P("Key (path) of the file in the bucket") String fileKey,
    @P("The question to answer about the file") String question)
```

**Behavior:**
1. Read file from StorageAdapter
2. Parse with appropriate parser
3. Split into chunks
4. Embed all chunks + the question
5. Find top 3 most relevant chunks by cosine similarity (in-memory, no store)
6. Return the matched chunks as context

This is a **one-shot operation** — chunks are not persisted. Use `ingestBucket` + `searchContent` for repeated searches.

**Output format:**
```
Most relevant sections from docs/report.txt for "revenue":

[1] (score: 0.94)
    "Q2 revenue reached $4.2M, representing a 15% increase over Q1..."

[2] (score: 0.82)
    "Revenue projections for Q3 indicate continued growth at 12% QoQ..."

[3] (score: 0.68)
    "Operating expenses remained flat at $2.1M, improving margins..."
```

---

## Configuration

### New `EmbeddingSection` in AgentConfig

```java
public static class EmbeddingSection {
    private String provider = "ollama";
    private OllamaEmbeddingConfig ollama = new OllamaEmbeddingConfig();
    private String storePath;
    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private int maxResults = 3;
    private double minScore = 0.5;

    public String getStorePath() {
        if (storePath != null) return storePath;
        return Path.of(System.getProperty("user.home"), ".agent-toolbox", "embeddings").toString();
    }
}

public static class OllamaEmbeddingConfig {
    private String model = "nomic-embed-text";
    // No baseUrl here — RagToolProvider reads it from config.getLlm().getOllama().getBaseUrl()
}
```

### application.yaml Addition

```yaml
embedding:
  provider: ollama
  ollama:
    model: nomic-embed-text
  chunk-size: 500
  chunk-overlap: 50
  max-results: 3
  min-score: 0.5
```

The `storePath` defaults to `~/.agent-toolbox/embeddings` (same pattern as memory and bucket storage).

The Ollama base URL is reused from `llm.ollama.base-url` — the embedding model runs on the same Ollama instance as the chat model.

---

## System Prompt Update

Add to the COMMON PATTERNS section:

```
- "search my files for X" → call searchContent with the topic and bucket name
- "what does file.txt say about X" → call askAboutFile with the bucket, file key, and question
- "index my bucket" → call ingestBucket, then use searchContent to find content
- "find and sync" → call searchContent to find relevant files, then use existing tools to act on them

If searchContent returns "bucket not indexed", call ingestBucket first, then retry searchContent.
```

---

## Error Handling

All errors use `ToolErrorFormatter` (already in `agent-common`):

| Error Case | Message | ACTION Hint |
|------------|---------|-------------|
| Bucket not found | ERROR: Bucket 'x' not found | Call listBuckets to see available bucket names. |
| Bucket not indexed (search) | ERROR: Bucket 'x' has not been indexed | Call ingestBucket to index the bucket before searching. |
| File not found (askAboutFile) | ERROR: File 'x' not found in bucket 'y' | Call listFiles with the bucket name to see available files. |
| Embedding model unavailable | ERROR: Embedding model not available | Ensure Ollama is running and nomic-embed-text model is pulled. |
| No results found | (not an error) | "No results found for 'query' in bucket 'x'. Try a different search term or re-index the bucket." |

New exception needed: `BucketNotIndexedException extends StorageException` in `agent-common`.

---

## Dependencies

### New in parent `pom.xml` (dependencyManagement)

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-poi</artifactId>
</dependency>
```

Both are managed by the LangChain4j BOM so no version needed.

### `tool-rag/pom.xml`

```xml
<dependencies>
    <dependency>
        <groupId>io.agenttoolbox</groupId>
        <artifactId>agent-common</artifactId>
    </dependency>
    <dependency>
        <groupId>io.agenttoolbox</groupId>
        <artifactId>agent-core</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-ollama</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-document-parser-apache-poi</artifactId>
    </dependency>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
</dependencies>
```

---

## Testing Strategy

| Component | Test Type | Approach |
|-----------|-----------|----------|
| `DocumentParserFactory` | Unit test | Verify correct parser returned for each extension |
| `EmbeddingStoreManager` | Unit test | Verify save/load/replace with `@TempDir` |
| `IngestionService` | Unit test | Mock StorageAdapter + EmbeddingModel, verify chunks stored |
| `SearchService` | Unit test | Pre-populate InMemoryEmbeddingStore, verify ranked results |
| `RagTools` | Unit test | Mock services, verify tool output format |
| `ToolErrorFormatter` | Unit test | Add test for new `BucketNotIndexedException` |
| Full RAG flow | Integration test | Real files + mock Ollama embeddings via WireMock |

Embedding model calls are mocked in unit tests — no live Ollama needed for CI.

---

## Build Order

Updated module order: `agent-common` → `agent-core` → `tool-etag` → `tool-rag` → `agent-cli` → `agent-integration-tests`

---

## What This Does NOT Do

- No automatic re-indexing when files change (user must call `ingestBucket` again)
- No incremental indexing (full re-index per bucket each time)
- No cross-bucket search (search one bucket at a time)
- No ContentRetriever integration (pure tool-based, LLM decides when to search)
- No image/audio file support

These are all Phase B enhancements.
