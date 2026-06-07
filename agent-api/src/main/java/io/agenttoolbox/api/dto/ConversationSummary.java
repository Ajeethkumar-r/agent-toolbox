package io.agenttoolbox.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(UUID id, String title, Instant createdAt, Instant updatedAt) {
}
