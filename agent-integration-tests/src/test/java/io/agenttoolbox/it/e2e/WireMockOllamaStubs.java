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
