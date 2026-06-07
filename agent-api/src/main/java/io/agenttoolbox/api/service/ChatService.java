package io.agenttoolbox.api.service;

import io.agenttoolbox.api.entity.Conversation;
import io.agenttoolbox.api.entity.Message;
import io.agenttoolbox.api.repository.ConversationRepository;
import io.agenttoolbox.api.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service handling chat interactions with SSE streaming support.
 * <p>
 * Uses short-lived transactions for message persistence rather than holding
 * a transaction open for the entire duration of the SSE stream.
 * The actual LLM integration will replace the placeholder response in a future phase.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final TransactionTemplate txTemplate;

    /** Tracks active generations per user so they can be stopped on demand. */
    private final ConcurrentHashMap<UUID, AtomicBoolean> activeGenerations = new ConcurrentHashMap<>();

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       PlatformTransactionManager txManager) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    /**
     * Streams a chat response to the client via SSE.
     * <p>
     * Persists the user message, generates a response (placeholder for now),
     * streams it token-by-token, then persists the AI response.
     * Each database write runs in its own short transaction.
     *
     * @param userId         the authenticated user's ID
     * @param conversationId the conversation to add messages to
     * @param userMessage    the user's input message
     * @param emitter        the SSE emitter to stream tokens through
     */
    public void streamChat(UUID userId, UUID conversationId, String userMessage, SseEmitter emitter) {
        // 1. Verify conversation ownership and save user message in a short transaction
        Conversation conversation = txTemplate.execute(status -> {
            Conversation conv = conversationRepository
                    .findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Conversation not found"));

            Message userMsg = new Message(conversationId, "user", userMessage);
            messageRepository.save(userMsg);

            return conv;
        });

        // 2. Set up stop flag for this user's generation
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        activeGenerations.put(userId, stopFlag);

        try {
            // 3. Generate response (placeholder -- real LLM integration in a future phase)
            String response = generatePlaceholderResponse(userMessage);

            // 4. Stream response word-by-word
            String[] words = response.split(" ");
            StringBuilder fullResponse = new StringBuilder();

            for (String word : words) {
                if (stopFlag.get()) {
                    emitter.send(SseEmitter.event()
                            .name("stopped")
                            .data("{\"reason\": \"User requested stop\"}"));
                    break;
                }

                fullResponse.append(word).append(" ");
                emitter.send(SseEmitter.event()
                        .name("token")
                        .data("{\"content\": \"" + escapeJson(word) + " \"}"));

                Thread.sleep(50); // simulate streaming delay
            }

            String finalContent = fullResponse.toString().trim();
            int estimatedTokens = words.length;

            // 5. Save AI response and auto-title in a short transaction
            Message savedAiMsg = txTemplate.execute(status -> {
                Message aiMsg = new Message(conversationId, "assistant", finalContent);
                aiMsg.setTokenCount(estimatedTokens);
                messageRepository.save(aiMsg);

                // Auto-generate title from first user message if conversation has none
                if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
                    String title = userMessage.length() > 50
                            ? userMessage.substring(0, 50) + "..."
                            : userMessage;
                    conversation.setTitle(title);
                    conversationRepository.save(conversation);
                }

                return aiMsg;
            });

            // 6. Send completion event
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data("{\"messageId\": \"" + savedAiMsg.getId()
                            + "\", \"tokenCount\": " + estimatedTokens + "}"));

            emitter.complete();
            log.debug("Chat stream completed for userId={}, conversationId={}", userId, conversationId);

        } catch (Exception e) {
            log.error("Chat stream failed for userId={}, conversationId={}: {}",
                    userId, conversationId, e.getMessage(), e);
            emitter.completeWithError(e);
        } finally {
            activeGenerations.remove(userId);
        }
    }

    /**
     * Signals the current generation for the given user to stop.
     *
     * @param userId the user whose generation should be stopped
     */
    public void stopGeneration(UUID userId) {
        AtomicBoolean flag = activeGenerations.get(userId);
        if (flag != null) {
            flag.set(true);
            log.info("Stop requested for userId={}", userId);
        }
    }

    /**
     * Placeholder response generator.
     * Will be replaced by AgentRunner / LangChain4j streaming integration.
     */
    private String generatePlaceholderResponse(String userMessage) {
        return "I received your message. "
                + "This is a placeholder response -- LLM integration will be added in a future phase. "
                + "Your message was: \"" + userMessage + "\"";
    }

    /**
     * Minimal JSON string escaping for embedding values in hand-built JSON.
     */
    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
