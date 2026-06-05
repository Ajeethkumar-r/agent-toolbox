package io.agenttoolbox.core.llm;

import io.agenttoolbox.common.exception.LlmUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Verifies that an Ollama instance is reachable before attempting to use it.
 */
public final class OllamaHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(OllamaHealthCheck.class);

    private OllamaHealthCheck() {}

    /**
     * Sends a GET request to {@code {baseUrl}/api/tags} to verify Ollama is running.
     *
     * @param baseUrl the Ollama base URL (e.g. {@code http://localhost:11434})
     * @throws LlmUnavailableException if Ollama is not reachable
     */
    public static void verify(String baseUrl) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmUnavailableException(
                        "Ollama returned HTTP " + response.statusCode() + " from " + baseUrl);
            }
            log.debug("Ollama health check passed at {}", baseUrl);
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmUnavailableException(
                    "Ollama is not reachable at " + baseUrl + ": " + e.getMessage(), e);
        }
    }
}
