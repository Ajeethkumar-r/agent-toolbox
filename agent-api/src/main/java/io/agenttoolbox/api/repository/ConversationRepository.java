package io.agenttoolbox.api.repository;

import io.agenttoolbox.api.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId);

    Optional<Conversation> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
