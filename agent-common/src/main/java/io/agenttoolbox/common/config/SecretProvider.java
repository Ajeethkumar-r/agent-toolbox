package io.agenttoolbox.common.config;

import java.util.Optional;

public interface SecretProvider { Optional<String> get(String key); }
