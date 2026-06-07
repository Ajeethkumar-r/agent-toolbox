package io.agenttoolbox.api.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserInfo user
) {
}
