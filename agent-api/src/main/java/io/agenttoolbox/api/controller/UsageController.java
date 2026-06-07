package io.agenttoolbox.api.controller;

import io.agenttoolbox.api.entity.UsageLog;
import io.agenttoolbox.api.service.UsageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.beans.factory.annotation.Value;

import java.util.Map;
import java.util.UUID;

/**
 * Exposes usage stats for the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final UsageService usageService;

    @Value("${rate-limit.daily-query-limit:50}")
    private int dailyQueryLimit;

    @Value("${rate-limit.daily-token-budget:500000}")
    private long dailyTokenBudget;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping
    public ResponseEntity<?> getUsage() {
        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UsageLog usage = usageService.getTodayUsage(userId);

        if (usage == null) {
            return ResponseEntity.ok(Map.of(
                    "queryCount", 0,
                    "inputTokens", 0L,
                    "outputTokens", 0L,
                    "dailyQueryLimit", dailyQueryLimit,
                    "dailyTokenBudget", dailyTokenBudget
            ));
        }

        return ResponseEntity.ok(Map.of(
                "queryCount", usage.getQueryCount(),
                "inputTokens", usage.getInputTokens(),
                "outputTokens", usage.getOutputTokens(),
                "modelUsed", usage.getModelUsed() != null ? usage.getModelUsed() : "",
                "dailyQueryLimit", dailyQueryLimit,
                "dailyTokenBudget", dailyTokenBudget
        ));
    }
}
