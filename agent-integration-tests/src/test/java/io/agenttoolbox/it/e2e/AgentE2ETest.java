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
        verify(moreThanOrExactly(2), postRequestedFor(urlEqualTo("/api/chat")));
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
