package pl.fotozielnik.users;

import java.time.Instant;
import java.util.UUID;

/** An account as the admin sees it (no password hash, no library content). */
record UserResponse(
        UUID id,
        String email,
        String displayName,
        Role role,
        boolean active,
        boolean mustChangePassword,
        Instant createdAt) {

    static UserResponse of(AppUser u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole(),
                u.isActive(),
                u.isMustChangePassword(),
                u.getCreatedAt());
    }
}
