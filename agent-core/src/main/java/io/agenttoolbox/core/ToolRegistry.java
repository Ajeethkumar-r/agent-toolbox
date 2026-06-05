package io.agenttoolbox.core;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Registry that discovers and manages {@link ToolProvider} instances.
 * Providers can be registered manually or discovered via {@link ServiceLoader}.
 */
public class ToolRegistry {

    private final List<ToolProvider> providers = new ArrayList<>();

    public ToolRegistry() {
        // Auto-discover providers registered via META-INF/services
        ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class);
        loader.forEach(providers::add);
    }

    /** Manually register a tool provider. */
    public void registerProvider(ToolProvider provider) {
        providers.add(provider);
    }

    /** Returns tool instances from all registered providers. */
    public List<Object> discoverTools() {
        return providers.stream()
                .map(ToolProvider::toolInstance)
                .collect(Collectors.toList());
    }

    /** Returns names of all registered providers. */
    public List<String> getProviderNames() {
        return providers.stream()
                .map(ToolProvider::name)
                .collect(Collectors.toList());
    }
}
