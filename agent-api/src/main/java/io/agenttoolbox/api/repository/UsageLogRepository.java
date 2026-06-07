package io.agenttoolbox.api.repository;

import io.agenttoolbox.api.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface UsageLogRepository extends JpaRepository<UsageLog, UUID> {

    Optional<UsageLog> findByUserIdAndQueryDate(UUID userId, LocalDate queryDate);
}
