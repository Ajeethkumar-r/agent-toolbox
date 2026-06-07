package io.agenttoolbox.api.repository;

import io.agenttoolbox.api.entity.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    Optional<UserToken> findByUserIdAndProviderAndDeletedAtIsNull(UUID userId, String provider);

    Optional<UserToken> findByUserIdAndProvider(UUID userId, String provider);
}
