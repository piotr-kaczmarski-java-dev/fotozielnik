package pl.fotozielnik.users;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

/**
 * Signs a user out on every device by deleting their server-side sessions (password reset, deactivation,
 * password change). This is what session cookies give us over JWTs (ADR-0005).
 */
@Component
class SessionTerminator {

    private final FindByIndexNameSessionRepository<?> sessions;

    SessionTerminator(FindByIndexNameSessionRepository<?> sessions) {
        this.sessions = sessions;
    }

    /** The session principal name is the user id (see {@link AppUserDetailsService}). */
    void terminateAll(UUID userId, @Nullable String exceptSessionId) {
        sessions.findByPrincipalName(userId.toString()).keySet().stream()
                .filter(id -> !id.equals(exceptSessionId))
                .forEach(sessions::deleteById);
    }
}
