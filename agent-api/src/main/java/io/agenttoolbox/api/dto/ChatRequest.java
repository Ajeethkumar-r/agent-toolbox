package io.agenttoolbox.api.dto;

import java.util.UUID;

public record ChatRequest(UUID conversationId, String message) {
}
