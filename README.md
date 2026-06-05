# Agent Toolbox

An LLM-powered Java CLI agent that provides intelligent file storage operations using ETag-based tools. Built with LangChain4j, Picocli, and Ollama, it enables natural-language interaction with local (and future cloud) storage through delta sync, upload validation, concurrency control, and cache validation.

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+ (or use the included Maven wrapper)
- [Ollama](https://ollama.com/) running locally with a pulled model

### Setup

```bash
# 1. Clone and build
git clone <repo-url> && cd agent-toolbox
./mvnw clean package -DskipTests

# 2. Pull an Ollama model
ollama pull llama3.1:8b

# 3. Start Ollama (if not already running)
ollama serve

# 4. Run the agent
java -jar agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar
```

### Usage Examples

**Interactive REPL:**
```bash
java -jar agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar
> Sync my ~/documents folder to the backup bucket
> Upload report.pdf to the reports bucket with integrity validation
> Check if my cached catalog.json is still current
```

**One-shot mode:**
```bash
java -jar agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar "sync ~/data to my-bucket"
```

**With options:**
```bash
java -jar agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar --model mistral:7b -v "upload config.yaml to settings bucket"
```

## Available Tools

| Tool | Description |
|---|---|
| `deltaSync` | Sync a local directory to a storage bucket, uploading only changed files based on MD5 comparison |
| `uploadWithValidation` | Upload a file with MD5 validation to ensure data integrity |
| `conditionalUpdate` | Update a file only if the known ETag matches, preventing concurrent modification conflicts |
| `cacheValidation` | Validate if a cached file is still current by comparing ETags, avoiding unnecessary data transfer |

## Configuration

Default configuration is in `agent-cli/src/main/resources/application.yaml`:

```yaml
agent:
  name: Agent Toolbox
  version: "0.1.0"
  memory:
    max-messages: 20

llm:
  provider: ollama
  ollama:
    base-url: http://localhost:11434
    model: llama3.1:8b
    temperature: 0.7
    timeout-seconds: 60

storage:
  provider: local
  local:
    bucket-root: /tmp/agent-buckets

output:
  verbosity: auto
  color: true

logging:
  level: INFO
```

Override at runtime with CLI flags or by providing a custom config file via `--config`.

## CLI Options

```
Usage: agent-toolbox [-hvV] [--config=<path>] [--model=<model>] [<query>...]

Options:
  -h, --help             Show help message and exit
  -V, --version          Print version information and exit
  -v, --verbose          Always use verbose output
      --model=<model>    Override Ollama model name
      --config=<path>    Path to config YAML
      [<query>...]       One-shot query (skip REPL)
```

## Project Structure

```
agent-toolbox/
  agent-common/           Shared utilities: exceptions, Md5Hasher, FileMetadata,
                          StorageAdapter interface, SecretProvider
  agent-core/             Core framework: AgentConfig, ConfigLoader, ToolProvider SPI,
                          ToolRegistry, ChatModelFactory, OllamaHealthCheck, AgentRunner
  tool-etag/              ETag-based storage tools: LocalStorageAdapter, DeltaSyncService,
                          UploadValidationService, ConcurrencyControlService,
                          CacheValidationService, EtagTools (@Tool), EtagToolProvider (SPI)
  agent-cli/              CLI entry point: AgentCli (Picocli), OutputFormatter,
                          VerbosityMode, logback config, fat JAR packaging
  agent-integration-tests/ Integration tests exercising full tool pipelines
  docs/                   Additional documentation
```

## Documentation

- [Model Recommendations](docs/model-recommendations.md) -- which Ollama model to use based on your hardware
- [Adding a New Tool](docs/adding-a-new-tool.md) -- step-by-step guide to extending the agent with new tools

## References

- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [Picocli](https://picocli.info/)
- [RFC 7232 -- Conditional Requests (ETags)](https://www.rfc-editor.org/rfc/rfc7232)
- [SLF4J](https://www.slf4j.org/)
- [Maven Multi-Module Projects](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [Ollama API](https://github.com/ollama/ollama/blob/main/docs/api.md)
- [Google Cloud Storage Java Client](https://cloud.google.com/storage/docs/reference/libraries#client-libraries-install-java)
