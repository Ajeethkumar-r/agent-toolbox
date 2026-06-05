# Model Recommendations

## Choosing an Ollama Model

The right model depends on your available RAM. Larger models produce better tool-calling accuracy but require more memory.

| RAM | Recommended Model | Notes |
|---|---|---|
| 8 GB | `llama3.2:3b`, `phi3:mini` | Lightweight; suitable for simple tool calls. May struggle with multi-step reasoning. |
| 16 GB | `llama3.1:8b`, `mistral:7b`, `gemma2:9b` | Good balance of quality and speed. `llama3.1:8b` is the default. |
| 32 GB+ | `llama3.1:70b`, `mixtral:8x7b` | Best tool-calling accuracy. Slower inference but handles complex queries well. |

## How to Change the Model

### CLI Flag (per invocation)

```bash
java -jar agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar --model mistral:7b "sync my files"
```

### Configuration File (persistent)

Edit `agent-cli/src/main/resources/application.yaml`:

```yaml
llm:
  provider: ollama
  ollama:
    model: mistral:7b       # change this line
    base-url: http://localhost:11434
    temperature: 0.7
    timeout-seconds: 60
```

Then rebuild the JAR:

```bash
./mvnw clean package -DskipTests -pl agent-cli -am
```

### Pulling a New Model

Before using a model, make sure it is pulled locally:

```bash
ollama pull mistral:7b
ollama list   # verify it appears
```

## Tips

- Start with the default (`llama3.1:8b`) and only switch if you see poor tool-calling behavior.
- Lower `temperature` (e.g., `0.3`) can improve determinism for tool calls at the cost of creativity.
- If Ollama is running on a different machine, update `base-url` accordingly.
