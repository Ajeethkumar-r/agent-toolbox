package io.agenttoolbox.api.repository;

import io.agenttoolbox.api.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID conversationId);

    List<Message> findTop20ByConversationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID conversationId);

    void deleteByConversationId(UUID conversationId);
}
