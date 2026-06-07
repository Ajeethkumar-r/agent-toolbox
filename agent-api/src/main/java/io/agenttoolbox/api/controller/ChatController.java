package io.agenttoolbox.api.controller;

import io.agenttoolbox.api.dto.ChatRequest;
import io.agenttoolbox.api.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for chat interactions.
 * Streams AI responses to the client via Server-Sent Events (SSE).
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 60_000L; // 60 seconds

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Sends a user message and streams the AI response as SSE events.
     * <p>
     * Event types:
     * <ul>
     *   <li>{@code token} -- a chunk of the response: {@code {"content": "word "}}</li>
     *   <li>{@code done} -- generation complete: {@code {"messageId": "...", "tokenCount": N}}</li>
     *   <li>{@code stopped} -- user-initiated stop: {@code {"reason": "User requested stop"}}</li>
     * </ul>
     *
     * @param request the chat request containing conversationId and message
     * @return an SSE emitter streaming the response
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        UUID userId = getAuthenticatedUserId();

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        CompletableFuture.runAsync(() -> {
            try {
                chatService.streamChat(userId, request.conversationId(), request.message(), emitter);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Stops the current AI response generation for the authenticated user.
     *
     * @return 200 OK
     */
    @PostMapping("/stop")
    public ResponseEntity<Void> stopGeneration() {
        UUID userId = getAuthenticatedUserId();
        chatService.stopGeneration(userId);
        return ResponseEntity.ok().build();
    }

    private UUID getAuthenticatedUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
