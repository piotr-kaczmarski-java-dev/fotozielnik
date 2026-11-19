package pl.fotozielnik.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import pl.fotozielnik.shared.Ids;

@Entity
@Table(name = "app_user")
class AppUser {

    @Id
    private UUID id;

    @Column
    private String email;

    @Column
    private String passwordHash;

    @Column
    private String displayName;

    @Column
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column
    private Role role;

    @Column
    private boolean active;

    @Column
    private boolean mustChangePassword;

    @Column
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    /** Wrapper type on purpose: null means "not persisted yet", so save() inserts without a select first. */
    @Version
    private Long version;

    protected AppUser() {}

    AppUser(String email, String passwordHash, String displayName, Role role, boolean mustChangePassword) {
        this.id = Ids.newId();
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName.strip();
        this.locale = "pl";
        this.role = role;
        this.active = true;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    void changePassword(String newHash, boolean temporary) {
        this.passwordHash = newHash;
        this.mustChangePassword = temporary;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getDisplayName() {
        return displayName;
    }

    String getLocale() {
        return locale;
    }

    Role getRole() {
        return role;
    }

    boolean isActive() {
        return active;
    }

    boolean isMustChangePassword() {
        return mustChangePassword;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
