package io.agenttoolbox.api.service;

import dev.langchain4j.model.output.TokenUsage;
import io.agenttoolbox.api.entity.Conversation;
import io.agenttoolbox.api.entity.Message;
import io.agenttoolbox.api.repository.ConversationRepository;
import io.agenttoolbox.api.repository.MessageRepository;
import io.agenttoolbox.api.security.InputSanitizer;
import io.agenttoolbox.api.security.OutputFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.agenttoolbox.tool.drive.DriveTools;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service handling chat interactions with SSE streaming support.
 * Integrates with LlmChatService for real LLM responses (Ollama or Gemini).
 * Enforces rate limiting, circuit breaker, and abuse detection before each call.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final TransactionTemplate txTemplate;
    private final InputSanitizer inputSanitizer;
    private final OutputFilter outputFilter;
    private final LlmChatService llmChatService;
    private final UserDriveClientFactory driveClientFactory;
    private final UsageService usageService;
    private final RateLimiterService rateLimiterService;
    private final AbuseDetectionService abuseDetectionService;
    private final AuditService auditService;

    @Value("${circuit-breaker.enabled:false}")
    private boolean circuitBreakerEnabled;

    /** Tracks active generations per user so they can be stopped on demand. */
    private final ConcurrentHashMap<UUID, AtomicBoolean> activeGenerations = new ConcurrentHashMap<>();

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       PlatformTransactionManager txManager,
                       InputSanitizer inputSanitizer,
                       OutputFilter outputFilter,
                       LlmChatService llmChatService,
                       UserDriveClientFactory driveClientFactory,
                       UsageService usageService,
                       RateLimiterService rateLimiterService,
                       AbuseDetectionService abuseDetectionService,
                       AuditService auditService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.txTemplate = new TransactionTemplate(txManager);
        this.inputSanitizer = inputSanitizer;
        this.outputFilter = outputFilter;
        this.llmChatService = llmChatService;
        this.driveClientFactory = driveClientFactory;
        this.usageService = usageService;
        this.rateLimiterService = rateLimiterService;
        this.abuseDetectionService = abuseDetectionService;
        this.auditService = auditService;
    }

    /**
     * Streams a chat response to the client via SSE.
     * Enforces circuit breaker, rate limits, and abuse checks before calling Gemini.
     */
    public void streamChat(UUID userId, UUID conversationId, String userMessage, SseEmitter emitter) {
        // 0. Circuit breaker check
        if (circuitBreakerEnabled) {
            sendError(emitter, "Service temporarily unavailable. Please try again later.");
            return;
        }

        // 1. Sanitize input
        String sanitizedMessage = inputSanitizer.sanitize(userMessage);
        if (inputSanitizer.containsSuspiciousPatterns(sanitizedMessage)) {
            log.warn("Suspicious input from userId={}: {}",
                    userId, sanitizedMessage.substring(0, Math.min(100, sanitizedMessage.length())));
        }

        // 2. Rate limit + abuse checks
        String rateLimitError = rateLimiterService.checkRateLimit(userId);
        if (rateLimitError != null) {
            sendError(emitter, rateLimitError);
            return;
        }

        String abuseError = abuseDetectionService.checkAndRecord(userId, sanitizedMessage);
        if (abuseError != null) {
            sendError(emitter, abuseError);
            return;
        }

        // 3. Verify conversation ownership and save user message
        Conversation conversation = txTemplate.execute(status -> {
            Conversation conv = conversationRepository
                    .findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Conversation not found"));

            Message userMsg = new Message(conversationId, "user", sanitizedMessage);
            messageRepository.save(userMsg);
            return conv;
        });

        // 4. Load conversation history for context
        List<Message> history = messageRepository
                .findTop20ByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(conversationId);
        // Reverse to chronological order (query returns newest first)
        java.util.Collections.reverse(history);

        // 5. Set up stop flag + handle SSE lifecycle
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        activeGenerations.put(userId, stopFlag);

        // If the SSE connection drops (timeout/client disconnect), stop the LLM immediately
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for userId={}, conversationId={}", userId, conversationId);
            stopFlag.set(true);
        });
        emitter.onError(e -> {
            log.debug("SSE error for userId={}: {}", userId, e.getMessage());
            stopFlag.set(true);
        });

        try {
            if (!llmChatService.isAvailable()) {
                // Fallback to placeholder if LLM not configured
                streamPlaceholder(userId, sanitizedMessage, conversationId, conversation, userMessage, stopFlag, emitter);
                return;
            }

            // 6. Build tools (e.g. Google Drive) for the user
            List<Object> tools = new ArrayList<>();
            DriveTools driveTools = driveClientFactory.buildDriveTools(userId);
            if (driveTools != null) {
                tools.add(driveTools);
                log.debug("Drive tools loaded for userId={}", userId);
            }

            // 7. Stream from LLM with tool support
            StringBuilder fullResponse = new StringBuilder();
            List<String> toolCallEntries = new ArrayList<>();

            var usageFuture = llmChatService.streamChat(
                    history,
                    sanitizedMessage,
                    token -> {
                        if (stopFlag.get()) return;
                        String filtered = outputFilter.filter(token);
                        fullResponse.append(filtered);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("token")
                                    .data("{\"content\": \"" + escapeJson(filtered) + "\"}"));
                        } catch (Exception e) {
                            log.debug("Failed to send SSE token: {}", e.getMessage());
                            stopFlag.set(true);
                        }
                    },
                    stopFlag,
                    tools.isEmpty() ? null : tools,
                    toolInfo -> {
                        toolCallEntries.add(toolInfo);
                        String toolName = "unknown";
                        try {
                            // Extract tool name from JSON for SSE/audit
                            int nameStart = toolInfo.indexOf("\"name\":\"") + 8;
                            int nameEnd = toolInfo.indexOf("\"", nameStart);
                            if (nameStart > 7 && nameEnd > nameStart) {
                                toolName = toolInfo.substring(nameStart, nameEnd);
                            }
                        } catch (Exception ignored) {}
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("tool_call")
                                    .data(toolInfo));
                            auditService.logToolCall(userId, toolName, "", "executed");
                        } catch (Exception e) {
                            log.debug("Failed to send tool_call SSE event: {}", e.getMessage());
                        }
                    }
            );

            // Wait for completion (aligned with SSE timeout)
            TokenUsage tokenUsage;
            try {
                tokenUsage = usageFuture.get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("LLM streaming failed for userId={}: {}", userId, e.getMessage());
                stopFlag.set(true);
                tokenUsage = new TokenUsage(0, 0);
            }

            if (stopFlag.get()) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("stopped")
                            .data("{\"reason\": \"User requested stop\"}"));
                } catch (Exception e) {
                    log.debug("SSE already closed, skipping stopped event");
                }
            }

            String finalContent = fullResponse.toString().trim();
            int inputTokens = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0;
            int outputTokens = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0;

            // 8. Save AI response + tool calls + auto-title + bump conversation updatedAt
            String toolCallsJson = toolCallEntries.isEmpty()
                    ? null : "[" + String.join(",", toolCallEntries) + "]";

            Message savedAiMsg = txTemplate.execute(status -> {
                Message aiMsg = new Message(conversationId, "assistant", finalContent);
                aiMsg.setTokenCount(outputTokens);
                aiMsg.setToolCalls(toolCallsJson);
                messageRepository.save(aiMsg);

                if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
                    String title = sanitizedMessage.length() > 50
                            ? sanitizedMessage.substring(0, 50) + "..."
                            : sanitizedMessage;
                    conversation.setTitle(title);
                }
                // Always save to bump updatedAt (via @PreUpdate) so recent chats sort first
                conversationRepository.save(conversation);
                return aiMsg;
            });

            // 9. Track usage + audit
            usageService.recordUsage(userId, inputTokens, outputTokens, llmChatService.getProviderName());
            auditService.logChatRequest(userId, "chat");

            // 10. Send done event (SSE may already be closed on timeout)
            try {
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("{\"messageId\": \"" + savedAiMsg.getId()
                                + "\", \"inputTokens\": " + inputTokens
                                + ", \"outputTokens\": " + outputTokens + "}"));
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE already closed, but response was saved to DB");
            }
            log.debug("Chat stream completed for userId={}, conversationId={}", userId, conversationId);

        } catch (Exception e) {
            log.error("Chat stream failed for userId={}, conversationId={}: {}",
                    userId, conversationId, e.getMessage(), e);
            emitter.completeWithError(e);
        } finally {
            activeGenerations.remove(userId);
        }
    }

    public void stopGeneration(UUID userId) {
        AtomicBoolean flag = activeGenerations.get(userId);
        if (flag != null) {
            flag.set(true);
            log.info("Stop requested for userId={}", userId);
        }
    }

    /**
     * Fallback when Gemini API key is not configured.
     */
    private void streamPlaceholder(UUID userId, String sanitizedMessage, UUID conversationId,
                                   Conversation conversation, String userMessage,
                                   AtomicBoolean stopFlag, SseEmitter emitter) throws Exception {
        String response = outputFilter.filter(
                "I received your message. This is a placeholder response — "
                        + "no LLM provider is configured. "
                        + "Your message was: \"" + sanitizedMessage + "\"");

        String[] words = response.split(" ");
        StringBuilder fullResponse = new StringBuilder();

        for (String word : words) {
            if (stopFlag.get()) break;
            fullResponse.append(word).append(" ");
            emitter.send(SseEmitter.event()
                    .name("token")
                    .data("{\"content\": \"" + escapeJson(word) + " \"}"));
            Thread.sleep(50);
        }

        String finalContent = fullResponse.toString().trim();
        Message savedAiMsg = txTemplate.execute(status -> {
            Message aiMsg = new Message(conversationId, "assistant", finalContent);
            aiMsg.setTokenCount(words.length);
            messageRepository.save(aiMsg);

            if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
                String title = sanitizedMessage.length() > 50
                        ? sanitizedMessage.substring(0, 50) + "..." : sanitizedMessage;
                conversation.setTitle(title);
            }
            conversationRepository.save(conversation);
            return aiMsg;
        });

        usageService.recordUsage(userId, 0, words.length, "placeholder");
        auditService.logChatRequest(userId, "chat");

        emitter.send(SseEmitter.event()
                .name("done")
                .data("{\"messageId\": \"" + savedAiMsg.getId()
                        + "\", \"inputTokens\": 0, \"outputTokens\": " + words.length + "}"));
        emitter.complete();
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\": \"" + escapeJson(message) + "\"}"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
