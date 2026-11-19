package pl.fotozielnik.users;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.fotozielnik.shared.security.CurrentUser;

/** Account management (FR-002, UC-05). Admins only - enforced for {@code /api/admin/**} in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/users")
class AdminUserController {

    private final UserService users;

    AdminUserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    UserPage list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return users.list(page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Valid @RequestBody CreateUserRequest body, Authentication authentication) {
        return users.create(CurrentUser.id(authentication), body);
    }

    @PostMapping("/{id}/password-reset")
    UserResponse resetPassword(
            @PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest body, Authentication authentication) {
        return users.resetPassword(CurrentUser.id(authentication), id, body);
    }

    @PostMapping("/{id}/deactivate")
    UserResponse deactivate(@PathVariable UUID id, Authentication authentication) {
        return users.setActive(CurrentUser.id(authentication), id, false);
    }

    @PostMapping("/{id}/activate")
    UserResponse activate(@PathVariable UUID id, Authentication authentication) {
        return users.setActive(CurrentUser.id(authentication), id, true);
    }
}
