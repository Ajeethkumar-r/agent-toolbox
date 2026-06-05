package io.agenttoolbox.core;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import io.agenttoolbox.common.config.SecretProvider;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.core.llm.ChatModelFactory;
import io.agenttoolbox.core.llm.OllamaHealthCheck;
import io.agenttoolbox.core.model.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

            AiServices<AgentService> builder = AiServices.builder(AgentService.class)
                    .chatModel(chatModel)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(
                            config.getAgent().getMemory().getMaxMessages()));

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

    /** Returns the names of all registered tool providers. */
    public List<String> getToolNames() {
        return toolRegistry.getProviderNames();
    }

    /** Returns the current configuration. */
    public AgentConfig getConfig() {
        return config;
    }
}
