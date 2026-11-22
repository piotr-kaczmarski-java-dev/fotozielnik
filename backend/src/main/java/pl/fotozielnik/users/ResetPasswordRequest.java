package pl.fotozielnik.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ResetPasswordRequest(
        @NotBlank @Size(min = UserService.MIN_PASSWORD_LENGTH, max = 200)
        String temporaryPassword) {}
