# Agent Toolbox — Request Flow

How a user command flows through the system from input to output.

**Example:** `> sync my documents to backup`

---

## Step-by-Step Flow

### Step 1: JVM Entry Point

```
File: agent-cli/src/main/java/io/agenttoolbox/cli/AgentCli.java:36-38
```
```java
public static void main(String[] args) {
    int exitCode = new CommandLine(new AgentCli()).execute(args);
    System.exit(exitCode);
}
```
Picocli parses args, calls `call()` method.

---

### Step 2: Startup & Wiring

```
File: agent-cli/src/main/java/io/agenttoolbox/cli/AgentCli.java:42-53
```
```java
public Integer call() {
    AgentConfig config = ConfigLoader.loadFromClasspath();  // loads application.yaml
    applyOverrides(config);                                  // --model, --verbose flags
    VerbosityMode verbosityMode = verbose ? VerbosityMode.VERBOSE : VerbosityMode.AUTO;
    OutputFormatter formatter = new OutputFormatter(verbosityMode);
    ToolRegistry registry = new ToolRegistry();              // discovers EtagToolProvider via SPI
    AgentRunner runner = new AgentRunner(config, new EnvVarSecretProvider(), registry);
    ...
    return runRepl(runner, formatter, config);                // starts REPL loop
}
```

| What happens | File |
|--|--|
| Load YAML config | `agent-core/.../ConfigLoader.java` reads `application.yaml` |
| SPI discovery | `agent-core/.../ToolRegistry.java:18` calls `ServiceLoader.load(ToolProvider.class)` |
| Finds EtagToolProvider | `tool-etag/.../META-INF/services/io.agenttoolbox.core.ToolProvider` points to `EtagToolProvider` |

---

### Step 3: REPL Reads User Input

```
File: agent-cli/src/main/java/io/agenttoolbox/cli/AgentCli.java:62-81
```
```java
private int runRepl(...) {
    System.out.printf("%s v%s (Ollama: %s)%n", ...);   // prints banner
    while (true) {
        System.out.print("> ");                          // prints prompt
        String line = reader.readLine();                 // WAITS for user input
        ...
        AgentResponse response = runner.chat(input);     // Step 4
        System.out.println(formatter.format(response));  // Step 9
    }
}
```

User types: `sync my documents to backup` then runner.chat() is called.

---

### Step 4: AgentRunner — Lazy Init + Send to LLM

```
File: agent-core/src/main/java/io/agenttoolbox/core/AgentRunner.java:72-80
```
```java
public AgentResponse chat(String userMessage) {
    try {
        initialize();                              // first call: wires everything
        String reply = agentService.chat(userMessage); // Step 5
        return AgentResponse.ok(reply);            // Step 8
    } catch (Exception e) {
        return AgentResponse.error(e.getMessage());
    }
}
```

**First call only** — `initialize()` does:

```
File: agent-core/src/main/java/io/agenttoolbox/core/AgentRunner.java:40-66
```
```java
public void initialize() {
    OllamaHealthCheck.verify(...);          // pings http://localhost:11434/api/tags
    ChatModel chatModel = ChatModelFactory.create(config, secrets);  // builds OllamaChatModel
    List<Object> tools = toolRegistry.discoverTools();               // calls EtagToolProvider.toolInstance()

    agentService = AiServices.builder(AgentService.class)
        .chatModel(chatModel)                                        // Ollama connection
        .chatMemory(MessageWindowChatMemory.withMaxMessages(20))     // conversation history
        .tools(tools)                                                // EtagTools with @Tool methods
        .build();
}
```

| What happens | File |
|--|--|
| Health check | `agent-core/.../OllamaHealthCheck.java` hits GET `localhost:11434/api/tags` |
| Build LLM client | `agent-core/.../ChatModelFactory.java` calls `OllamaChatModel.builder()` |
| Create tool instance | `tool-etag/.../EtagToolProvider.java:25-34` creates `LocalStorageAdapter` + 4 services + `EtagTools` |
| Wire agent | LangChain4j `AiServices.builder()` scans `@Tool` annotations on `EtagTools` |

---

### Step 5: LangChain4j Sends to Ollama

```
File: agent-core/src/main/java/io/agenttoolbox/core/AgentService.java:5-8
```
```java
public interface AgentService {
    @SystemMessage(fromResource = "prompts/system-prompt.txt")
    String chat(String userMessage);
}
```

LangChain4j internally:

1. Loads `system-prompt.txt` as system message
2. Adds tool descriptions (auto-generated from `@Tool` annotations on `EtagTools`)
3. Sends to Ollama: `POST http://localhost:11434/api/chat`
   ```json
   {
     "system": "You are Agent Toolbox...",
     "messages": [{ "role": "user", "content": "sync my documents to backup" }],
     "tools": [
       { "name": "deltaSync", "description": "Sync a local directory...", "parameters": {} },
       { "name": "uploadWithValidation", "..." : "..." },
       { "name": "conditionalUpdate", "..." : "..." },
       { "name": "cacheValidation", "..." : "..." }
     ]
   }
   ```
4. Ollama LLM reasons and responds with a tool call:
   ```json
   { "tool_call": { "name": "deltaSync", "arguments": { "localPath": "/Users/.../documents", "bucketName": "backup" } } }
   ```

---

### Step 6: LangChain4j Invokes the @Tool Method

LangChain4j parses the LLM's tool call and invokes the matching method:

```
File: tool-etag/src/main/java/io/agenttoolbox/tool/etag/EtagTools.java:27-32
```
```java
@Tool("Sync a local directory to a storage bucket, uploading only changed files based on MD5 comparison")
public String deltaSync(
        @P("Path to the local directory to sync") String localPath,
        @P("Name of the target storage bucket") String bucketName) {
    return deltaSyncService.sync(localPath, bucketName);  // Step 7
}
```

---

### Step 7: Service Executes the Actual Work

```
File: tool-etag/src/main/java/io/agenttoolbox/tool/etag/service/DeltaSyncService.java:20-61
```
```java
public String sync(String localPath, String bucketName) {
    Path localDir = Path.of(localPath);
    // For each file in localDir:
    //   1. Read file bytes
    //   2. Compute MD5 via Md5Hasher.hashBytes(content)
    //   3. Check storageAdapter.exists(bucket, key)
    //   4. If exists: compare MD5 with storageAdapter.getMetadata().md5Hash()
    //   5. If different or new: storageAdapter.write(bucket, key, content, md5)
    //   6. Track synced/skipped counts
    return "Synced 12/1847 files. 1835 skipped (unchanged). 2.3MB transferred.";
}
```

| What happens | File |
|--|--|
| Compute MD5 | `agent-common/.../crypto/Md5Hasher.java` uses `MessageDigest.getInstance("MD5")` |
| Check remote | `tool-etag/.../storage/LocalStorageAdapter.java` checks `~/.agent-toolbox/buckets/{bucket}/{key}` |
| Compare hashes | `LocalStorageAdapter.getMetadata()` reads `.metadata.json` sidecar file |
| Upload if changed | `LocalStorageAdapter.write()` copies file + writes sidecar to `~/.agent-toolbox/buckets/` |

---

### Step 8: Result Flows Back to LLM

```
DeltaSyncService returns String
  -> EtagTools.deltaSync() returns String
    -> LangChain4j sends tool result back to Ollama
      -> Ollama formats final human-readable response
        -> AgentService.chat() returns the LLM's final answer
          -> AgentRunner wraps it: AgentResponse.ok("Synced 12/1847 files...")
```

---

### Step 9: Output to User

```
File: agent-cli/src/main/java/io/agenttoolbox/cli/AgentCli.java:80-81
```
```java
AgentResponse response = runner.chat(input);
System.out.println(formatter.format(response));
```

```
File: agent-cli/src/main/java/io/agenttoolbox/cli/OutputFormatter.java:13-18
```
```java
public String format(AgentResponse response) {
    if (!response.success()) return "ERROR: " + response.message();
    return response.message();
}
```

User sees:
```
> sync my documents to backup
Synced 12/1847 files. 1835 skipped (unchanged). 2.3MB transferred.
```

---

## Visual Flow Map

```
USER INPUT                         FILES INVOLVED
----------                         --------------
"sync my documents to backup"
        |
        v
+-- AgentCli.runRepl() -----------  agent-cli/.../AgentCli.java:80
|   reader.readLine()
|       |
|       v
+-- AgentRunner.chat() -----------  agent-core/.../AgentRunner.java:72
|   initialize() [first time]
|       |
|       +-- OllamaHealthCheck ----  agent-core/.../OllamaHealthCheck.java
|       +-- ChatModelFactory -----  agent-core/.../ChatModelFactory.java
|       +-- ToolRegistry ---------  agent-core/.../ToolRegistry.java
|       |   +-- ServiceLoader
|       |       +-- EtagToolProvider  tool-etag/.../EtagToolProvider.java
|       |           +-- LocalStorageAdapter + 4 services
|       +-- AiServices.build()
|       |
|       v
+-- agentService.chat() ----------  agent-core/.../AgentService.java
|   |   @SystemMessage -----------  agent-core/.../prompts/system-prompt.txt
|   |
|   v [HTTP to Ollama]
|   Ollama LLM reasons -> tool_call: deltaSync(path, bucket)
|   |
|   v [LangChain4j invokes]
+-- EtagTools.deltaSync() --------  tool-etag/.../EtagTools.java:28
|       |
|       v
+-- DeltaSyncService.sync() -----  tool-etag/.../service/DeltaSyncService.java:20
|   |
|   +-- Md5Hasher.hashBytes() ---  agent-common/.../crypto/Md5Hasher.java
|   +-- storageAdapter.exists() -  tool-etag/.../storage/LocalStorageAdapter.java
|   +-- storageAdapter.getMetadata()  (reads .metadata.json sidecar)
|   +-- storageAdapter.write() --  (writes file + sidecar to ~/.agent-toolbox/buckets/)
|       |
|       v
|   returns "Synced 12/1847 files..."
|       |
|       v [back to LLM for final formatting]
|   Ollama -> final answer string
|       |
|       v
+-- AgentResponse.ok(reply) -----  agent-core/.../model/AgentResponse.java
|       |
|       v
+-- OutputFormatter.format() ----  agent-cli/.../OutputFormatter.java
|       |
|       v
+-- System.out.println() --------  Terminal output
        |
        v
"Synced 12/1847 files. 1835 skipped (unchanged). 2.3MB transferred."
```

---

## Files Involved (12 total)

| # | File | Module | Role |
|---|------|--------|------|
| 1 | `AgentCli.java` | agent-cli | CLI entry point, REPL loop |
| 2 | `OutputFormatter.java` | agent-cli | Formats response for terminal |
| 3 | `application.yaml` | agent-cli | Default configuration |
| 4 | `AgentRunner.java` | agent-core | Orchestrator: wires LLM + tools + memory |
| 5 | `AgentService.java` | agent-core | LangChain4j interface with @SystemMessage |
| 6 | `system-prompt.txt` | agent-core | Agent persona and instructions |
| 7 | `ChatModelFactory.java` | agent-core | Creates OllamaChatModel from config |
| 8 | `ToolRegistry.java` | agent-core | Discovers tool providers via SPI |
| 9 | `EtagToolProvider.java` | tool-etag | SPI: creates EtagTools instance |
| 10 | `EtagTools.java` | tool-etag | @Tool methods (4 operations) |
| 11 | `DeltaSyncService.java` | tool-etag | Business logic: delta sync |
| 12 | `LocalStorageAdapter.java` | tool-etag | Fake GCS: local filesystem + sidecar metadata |
| 13 | `Md5Hasher.java` | agent-common | MD5 hash computation |
| 14 | `AgentResponse.java` | agent-core | Response wrapper (ok/error) |
