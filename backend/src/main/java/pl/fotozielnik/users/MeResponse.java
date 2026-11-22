package pl.fotozielnik.users;

import java.util.UUID;

/** The signed-in user, as the frontend needs it (navigation, forced password change). */
record MeResponse(UUID id, String email, String displayName, String locale, Role role, boolean mustChangePassword) {

    static MeResponse of(AppUser u) {
        return new MeResponse(
                u.getId(), u.getEmail(), u.getDisplayName(), u.getLocale(), u.getRole(), u.isMustChangePassword());
    }
}
