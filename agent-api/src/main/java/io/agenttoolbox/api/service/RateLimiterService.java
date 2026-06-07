package io.agenttoolbox.api.service;

import io.agenttoolbox.api.entity.UsageLog;
import io.agenttoolbox.api.repository.UsageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Enforces daily query limit and daily token budget per user.
 * Reads from usage_logs to check current day's consumption.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final UsageLogRepository usageLogRepository;

    @Value("${rate-limit.daily-query-limit:50}")
    private int dailyQueryLimit;

    @Value("${rate-limit.daily-token-budget:500000}")
    private long dailyTokenBudget;

    public RateLimiterService(UsageLogRepository usageLogRepository) {
        this.usageLogRepository = usageLogRepository;
    }

    /**
     * Checks if the user has exceeded their daily rate limit or token budget.
     *
     * @param userId the user to check
     * @return null if allowed, or an error message if rate limited
     */
    @Transactional(readOnly = true)
    public String checkRateLimit(UUID userId) {
        LocalDate today = LocalDate.now();

        var usageOpt = usageLogRepository.findByUserIdAndQueryDate(userId, today);
        if (usageOpt.isEmpty()) {
            return null; // No usage today — allowed
        }

        UsageLog usage = usageOpt.get();

        // Check daily query limit
        if (usage.getQueryCount() >= dailyQueryLimit) {
            log.warn("Rate limit hit for userId={}: {} queries today (limit={})",
                    userId, usage.getQueryCount(), dailyQueryLimit);
            return "Daily query limit reached (" + dailyQueryLimit
                    + " queries). Resets at midnight UTC.";
        }

        // Check daily token budget
        long totalTokens = usage.getInputTokens() + usage.getOutputTokens();
        if (totalTokens >= dailyTokenBudget) {
            log.warn("Token budget hit for userId={}: {} tokens today (budget={})",
                    userId, totalTokens, dailyTokenBudget);
            return "Daily token budget reached (" + dailyTokenBudget
                    + " tokens). Resets at midnight UTC.";
        }

        return null;
    }
}
