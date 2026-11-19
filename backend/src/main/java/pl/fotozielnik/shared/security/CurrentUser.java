package pl.fotozielnik.shared.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;

/**
 * The signed-in user's id. The session principal's name is the user's UUID (not the email), so controllers
 * of every feature can scope queries by owner without touching the users feature.
 *
 * <p>Usage: {@code PhotoPage list(Authentication auth) { var owner = CurrentUser.id(auth); ... }}
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
