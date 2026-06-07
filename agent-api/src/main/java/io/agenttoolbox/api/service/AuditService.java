package io.agenttoolbox.api.service;

import io.agenttoolbox.api.entity.AuditLog;
import io.agenttoolbox.api.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Logs audit-worthy events: chat requests, tool calls, auth actions.
 * Each log entry is immutable (append-only).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Logs a chat request.
     */
    @Transactional
    public void logChatRequest(UUID userId, String action) {
        AuditLog entry = new AuditLog(userId, action);
        auditLogRepository.save(entry);
    }

    /**
     * Logs a tool call with its name, arguments, and result status.
     */
    @Transactional
    public void logToolCall(UUID userId, String toolName, String arguments, String result) {
        AuditLog entry = new AuditLog(userId, "tool_call");
        entry.setToolName(toolName);
        entry.setArguments(truncate(arguments, 1000));
        entry.setResult(result);
        auditLogRepository.save(entry);
        log.debug("Audit: userId={}, tool={}, result={}", userId, toolName, result);
    }

    /**
     * Logs an auth-related action (login, logout, consent, revoke).
     */
    @Transactional
    public void logAuthAction(UUID userId, String action, String ipAddress) {
        AuditLog entry = new AuditLog(userId, action);
        entry.setIpAddress(ipAddress);
        auditLogRepository.save(entry);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength
                ? value.substring(0, maxLength) + "..."
                : value;
    }
}
