package io.agenttoolbox.api.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AOP aspect that sets the PostgreSQL RLS context variable at the start of
 * every {@code @Transactional} method. This ensures that Row-Level Security
 * policies can identify the current user via {@code current_setting('app.current_user_id')}.
 *
 * <p>Uses {@code set_config('app.current_user_id', userId, true)} where the
 * third parameter {@code true} means the setting is local to the current
 * transaction, equivalent to {@code SET LOCAL}. This is safe with connection
 * pooling (HikariCP) because the setting is automatically cleared when the
 * transaction completes.</p>
 */
@Aspect
@Component
public class TransactionRlsAspect {

    private static final Logger logger = LoggerFactory.getLogger(TransactionRlsAspect.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Before any method annotated with {@code @Transactional}, set the
     * RLS user context if an authenticated user is present.
     */
    @Before("@annotation(org.springframework.transaction.annotation.Transactional) || "
            + "@within(org.springframework.transaction.annotation.Transactional)")
    public void setRlsContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UUID userId) {
            logger.debug("Setting RLS context for user: {}", userId);
            entityManager.createNativeQuery("SELECT set_config('app.current_user_id', :userId, true)")
                    .setParameter("userId", userId.toString())
                    .getSingleResult();
        }
    }
}
