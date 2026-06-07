package io.agenttoolbox.api.dto;

public record UserSettingsDto(String preferredModel, int dailyQueryLimit, String preferences) {
}
