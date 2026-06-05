package io.agenttoolbox.core;

import io.agenttoolbox.common.config.EnvVarSecretProvider;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.core.model.AgentResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunnerTest {

    @Test
    void chat_returnsErrorWhenOllamaUnavailable() {
        AgentConfig config = new AgentConfig();
        config.getLlm().getOllama().setBaseUrl("http://localhost:99999");
        AgentRunner runner = new AgentRunner(config, new EnvVarSecretProvider(), new ToolRegistry());
        AgentResponse response = runner.chat("hello");
        assertThat(response.success()).isFalse();
    }

    @Test
    void getToolNames_returnsRegisteredTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new ToolProvider() {
            @Override
            public String name() { return "test"; }

            @Override
            public String description() { return "test tool"; }

            @Override
            public Object toolInstance() { return new Object(); }
        });
        AgentConfig config = new AgentConfig();
        AgentRunner runner = new AgentRunner(config, new EnvVarSecretProvider(), registry);
        assertThat(runner.getToolNames()).containsExactly("test");
    }
}
