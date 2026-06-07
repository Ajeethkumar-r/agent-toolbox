package io.agenttoolbox.api.service;

import io.agenttoolbox.api.dto.ConversationDetail;
import io.agenttoolbox.api.dto.ConversationSummary;
import io.agenttoolbox.api.dto.MessageDto;
import io.agenttoolbox.api.entity.Conversation;
import io.agenttoolbox.api.entity.Message;
import io.agenttoolbox.api.repository.ConversationRepository;
import io.agenttoolbox.api.repository.MessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(UUID userId) {
        List<Conversation> conversations =
                conversationRepository.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId);

        return conversations.stream()
                .map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail getConversation(UUID userId, UUID conversationId) {
        Conversation conversation = conversationRepository
                .findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        List<Message> messages =
                messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(conversationId);

        List<MessageDto> messageDtos = messages.stream()
                .map(m -> new MessageDto(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getTokenCount(),
                        m.getToolCalls(),
                        m.getCreatedAt()))
                .toList();

        return new ConversationDetail(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                messageDtos);
    }

    @Transactional
    public ConversationSummary createConversation(UUID userId, String title) {
        Conversation conversation = new Conversation(userId, title);
        conversation = conversationRepository.save(conversation);

        return new ConversationSummary(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }

    @Transactional
    public void deleteConversation(UUID userId, UUID conversationId) {
        Conversation conversation = conversationRepository
                .findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        conversation.setDeletedAt(Instant.now());
        conversationRepository.save(conversation);
    }
}
