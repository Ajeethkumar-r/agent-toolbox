package io.agenttoolbox.common.config;

import java.util.Optional;

public class EnvVarSecretProvider implements SecretProvider {
    @Override public Optional<String> get(String key) { return Optional.ofNullable(System.getenv(key)); }
}
