package pl.fotozielnik.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,

        @NotBlank @Size(min = UserService.MIN_PASSWORD_LENGTH, max = 200)
        String newPassword) {}
