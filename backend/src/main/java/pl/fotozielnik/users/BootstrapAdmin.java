package pl.fotozielnik.users;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates the first admin on startup if none exists (FR-003). */
@Component
class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final BootstrapAdminProperties properties;
    private final UserService users;

    BootstrapAdmin(BootstrapAdminProperties properties, UserService users) {
        this.properties = properties;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.debug("ADMIN_EMAIL / ADMIN_INITIAL_PASSWORD not set; skipping admin bootstrap");
            return;
        }
        if (!users.bootstrapAdmin(
                Objects.requireNonNull(properties.email()), Objects.requireNonNull(properties.initialPassword()))) {
            log.debug("An admin already exists; skipping admin bootstrap");
        }
    }
}
