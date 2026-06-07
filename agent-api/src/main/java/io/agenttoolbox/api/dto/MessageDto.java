package io.agenttoolbox.api.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(UUID id, String role, String content, Integer tokenCount, String toolCalls, Instant createdAt) {
}
