package io.agenttoolbox.api.dto;

public record UpdateSettingsRequest(String preferredModel, Integer dailyQueryLimit, String preferences) {
}
