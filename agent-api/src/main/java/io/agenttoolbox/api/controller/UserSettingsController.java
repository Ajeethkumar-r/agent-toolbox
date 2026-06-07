package io.agenttoolbox.api.controller;

import io.agenttoolbox.api.dto.UpdateSettingsRequest;
import io.agenttoolbox.api.dto.UserSettingsDto;
import io.agenttoolbox.api.service.UserSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings")
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    public UserSettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    @GetMapping
    public ResponseEntity<UserSettingsDto> getSettings() {
        UUID userId = getAuthenticatedUserId();
        UserSettingsDto settings = userSettingsService.getSettings(userId);
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    public ResponseEntity<UserSettingsDto> updateSettings(@RequestBody UpdateSettingsRequest request) {
        UUID userId = getAuthenticatedUserId();
        UserSettingsDto settings = userSettingsService.updateSettings(userId, request);
        return ResponseEntity.ok(settings);
    }

    private UUID getAuthenticatedUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
