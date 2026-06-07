package io.agenttoolbox.api.dto;

import java.util.UUID;

public record UserInfo(
        UUID id,
        String email,
        String displayName,
        String avatarUrl
) {
}
