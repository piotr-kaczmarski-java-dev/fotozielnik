package pl.fotozielnik.users;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fotozielnik.jobs.JobQueue;
import pl.fotozielnik.shared.BadRequestException;
import pl.fotozielnik.shared.ConflictException;
import pl.fotozielnik.shared.NotFoundException;

/** Accounts: own profile and password, admin user management, first-admin bootstrap (FR-001..FR-003). */
@Service
class UserService {

    static final int MIN_PASSWORD_LENGTH = 8;

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionTerminator sessions;
    private final JobQueue jobs;

    UserService(AppUserRepository users, PasswordEncoder passwordEncoder, SessionTerminator sessions, JobQueue jobs) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    MeResponse me(UUID userId) {
        return MeResponse.of(find(userId));
    }

    /** Changes the own password and signs out every other session of the user (the current one stays). */
    @Transactional
    void changeOwnPassword(UUID userId, ChangePasswordRequest request, @Nullable String currentSessionId) {
        AppUser user = find(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("auth.wrong-current-password", "The current password is wrong.");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BadRequestException("auth.password-unchanged", "The new password must differ from the old one.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()), false);
        sessions.terminateAll(userId, currentSessionId);
    }

    @Transactional(readOnly = true)
    UserPage list(int page, int size) {
        Page<AppUser> result = users.findAll(PageRequest.of(page, size, Sort.by("email")));
        return new UserPage(result.map(UserResponse::of).getContent(), page, size, result.getTotalElements());
    }

    @Transactional
    UserResponse create(UUID adminId, CreateUserRequest request) {
        String email = AppUser.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("user.email-taken", "A user with this email already exists.");
        }
        AppUser user = users.save(new AppUser(
                email,
                passwordEncoder.encode(request.temporaryPassword()),
                request.displayName(),
                request.role(),
                true));
        log.info("Admin {} created user {} with role {}", adminId, user.getId(), user.getRole());
        return UserResponse.of(user);
    }

    /** Sets a temporary password (to be changed at next sign-in) and signs the user out everywhere. */
    @Transactional
    UserResponse resetPassword(UUID adminId, UUID userId, ResetPasswordRequest request) {
        AppUser user = find(userId);
        user.changePassword(passwordEncoder.encode(request.temporaryPassword()), true);
        sessions.terminateAll(userId, null);
        log.info("Admin {} reset the password of user {}", adminId, userId);
        return UserResponse.of(user);
    }

    @Transactional
    UserResponse setActive(UUID adminId, UUID userId, boolean active) {
        if (adminId.equals(userId) && !active) {
            throw new BadRequestException("user.cannot-deactivate-self", "You cannot deactivate your own account.");
        }
        AppUser user = find(userId);
        user.setActive(active);
        if (!active) {
            sessions.terminateAll(userId, null);
        }
        log.info("Admin {} set user {} active={}", adminId, userId, active);
        return UserResponse.of(user);
    }

    /**
     * Deletes an account and its whole library (NFR-006): the database rows go at once (ON DELETE CASCADE from
     * app_user: imports, photos, their jobs), the stored photo objects in a retried background job. The job has
     * no owner on purpose, so the cascade does not delete it too.
     */
    @Transactional
    void delete(UUID adminId, UUID userId) {
        if (adminId.equals(userId)) {
            throw new BadRequestException("user.cannot-delete-self", "You cannot delete your own account.");
        }
        AppUser user = find(userId);
        sessions.terminateAll(userId, null);
        users.delete(user);
        jobs.enqueue(
                UserObjectsCleanup.JOB_TYPE, null, new UserObjectsCleanup.Payload(userId), JobQueue.PRIORITY_NORMAL);
        log.info("Admin {} deleted user {} and their library", adminId, userId);
    }

    /**
     * Creates the first admin unless an admin already exists (FR-003). Idempotent, so it is safe on every start;
     * changing the env vars later does not change an existing account.
     */
    @Transactional
    boolean bootstrapAdmin(String email, String initialPassword) {
        if (users.existsByRole(Role.ADMIN)) {
            return false;
        }
        String normalized = AppUser.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new IllegalStateException("ADMIN_EMAIL belongs to an existing non-admin user");
        }
        AppUser admin = users.save(
                new AppUser(normalized, passwordEncoder.encode(initialPassword), "Administrator", Role.ADMIN, true));
        log.info("Created the first admin account {}", admin.getId());
        return true;
    }

    private AppUser find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found."));
    }
}
