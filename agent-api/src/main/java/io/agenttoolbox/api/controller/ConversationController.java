package io.agenttoolbox.api.controller;

import io.agenttoolbox.api.dto.ConversationDetail;
import io.agenttoolbox.api.dto.ConversationSummary;
import io.agenttoolbox.api.dto.CreateConversationRequest;
import io.agenttoolbox.api.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ResponseEntity<List<ConversationSummary>> listConversations() {
        UUID userId = getAuthenticatedUserId();
        List<ConversationSummary> conversations = conversationService.listConversations(userId);
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDetail> getConversation(@PathVariable UUID id) {
        UUID userId = getAuthenticatedUserId();
        ConversationDetail detail = conversationService.getConversation(userId, id);
        return ResponseEntity.ok(detail);
    }

    @PostMapping
    public ResponseEntity<ConversationSummary> createConversation(@RequestBody CreateConversationRequest request) {
        UUID userId = getAuthenticatedUserId();
        ConversationSummary summary = conversationService.createConversation(userId, request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        UUID userId = getAuthenticatedUserId();
        conversationService.deleteConversation(userId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID getAuthenticatedUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
