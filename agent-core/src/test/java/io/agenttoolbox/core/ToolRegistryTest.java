package io.agenttoolbox.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    @Test
    void discoverTools_returnsEmptyListWhenNoProvidersRegistered() {
        ToolRegistry registry = new ToolRegistry();
        List<Object> tools = registry.discoverTools();
        assertThat(tools).isNotNull();
    }

    @Test
    void discoverTools_collectsToolInstancesFromProviders() {
        ToolRegistry registry = new ToolRegistry();
        Object fakeTool = new Object();
        registry.registerProvider(new ToolProvider() {
            @Override
            public String name() { return "test-tool"; }

            @Override
            public String description() { return "A test tool"; }

            @Override
            public Object toolInstance() { return fakeTool; }
        });
        assertThat(registry.discoverTools()).containsExactly(fakeTool);
    }

    @Test
    void registeredProviderNamesAreAccessible() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new ToolProvider() {
            @Override
            public String name() { return "my-tool"; }

            @Override
            public String description() { return "desc"; }

            @Override
            public Object toolInstance() { return new Object(); }
        });
        assertThat(registry.getProviderNames()).containsExactly("my-tool");
    }
}
