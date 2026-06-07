package io.agenttoolbox.api.service;

import io.agenttoolbox.api.entity.UsageLog;
import io.agenttoolbox.api.repository.UsageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tracks per-user daily usage: query count, input tokens, output tokens.
 * Uses atomic upsert pattern — one row per user per day in usage_logs.
 */
@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);

    private final UsageLogRepository usageLogRepository;

    public UsageService(UsageLogRepository usageLogRepository) {
        this.usageLogRepository = usageLogRepository;
    }

    /**
     * Records a completed chat query's usage.
     * Increments query_count by 1 and adds token counts for today's row.
     */
    @Transactional
    public void recordUsage(UUID userId, int inputTokens, int outputTokens, String modelUsed) {
        LocalDate today = LocalDate.now();

        UsageLog usage = usageLogRepository.findByUserIdAndQueryDate(userId, today)
                .orElseGet(() -> {
                    UsageLog newLog = new UsageLog(userId, today);
                    newLog.setModelUsed(modelUsed);
                    return newLog;
                });

        usage.setQueryCount(usage.getQueryCount() + 1);
        usage.setInputTokens(usage.getInputTokens() + inputTokens);
        usage.setOutputTokens(usage.getOutputTokens() + outputTokens);
        if (modelUsed != null) {
            usage.setModelUsed(modelUsed);
        }

        usageLogRepository.save(usage);
        log.debug("Usage recorded for userId={}: queries={}, inputTokens={}, outputTokens={}",
                userId, usage.getQueryCount(), usage.getInputTokens(), usage.getOutputTokens());
    }

    /**
     * Gets today's usage for a user. Returns null if no usage today.
     */
    @Transactional(readOnly = true)
    public UsageLog getTodayUsage(UUID userId) {
        return usageLogRepository.findByUserIdAndQueryDate(userId, LocalDate.now())
                .orElse(null);
    }
}
