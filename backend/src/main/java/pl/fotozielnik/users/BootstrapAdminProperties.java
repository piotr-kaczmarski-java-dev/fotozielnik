package pl.fotozielnik.users;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First admin account (FR-003), from env vars {@code ADMIN_EMAIL} / {@code ADMIN_INITIAL_PASSWORD} (mapped in
 * application.yml). Only used while no admin exists; the password must be changed at first sign-in.
 */
@ConfigurationProperties("fotozielnik.bootstrap-admin")
record BootstrapAdminProperties(
        @Nullable String email, @Nullable String initialPassword) {

    boolean isConfigured() {
        return email != null && !email.isBlank() && initialPassword != null && !initialPassword.isBlank();
    }
}
