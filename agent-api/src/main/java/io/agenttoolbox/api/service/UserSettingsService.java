package io.agenttoolbox.api.service;

import io.agenttoolbox.api.dto.UpdateSettingsRequest;
import io.agenttoolbox.api.dto.UserSettingsDto;
import io.agenttoolbox.api.entity.UserSettings;
import io.agenttoolbox.api.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;

    public UserSettingsService(UserSettingsRepository userSettingsRepository) {
        this.userSettingsRepository = userSettingsRepository;
    }

    @Transactional(readOnly = true)
    public UserSettingsDto getSettings(UUID userId) {
        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));

        return toDto(settings);
    }

    @Transactional
    public UserSettingsDto updateSettings(UUID userId, UpdateSettingsRequest request) {
        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));

        if (request.preferredModel() != null) {
            settings.setPreferredModel(request.preferredModel());
        }
        if (request.dailyQueryLimit() != null) {
            settings.setDailyQueryLimit(request.dailyQueryLimit());
        }
        if (request.preferences() != null) {
            settings.setPreferences(request.preferences());
        }

        settings = userSettingsRepository.save(settings);
        return toDto(settings);
    }

    private UserSettings createDefaultSettings(UUID userId) {
        UserSettings settings = new UserSettings(userId);
        return userSettingsRepository.save(settings);
    }

    private UserSettingsDto toDto(UserSettings settings) {
        return new UserSettingsDto(
                settings.getPreferredModel(),
                settings.getDailyQueryLimit(),
                settings.getPreferences());
    }
}
