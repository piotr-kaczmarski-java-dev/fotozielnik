package pl.fotozielnik.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The admin hands the temporary password over in person; the user must change it at first sign-in. */
record CreateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String displayName,
        @NotNull Role role,

        @NotBlank @Size(min = UserService.MIN_PASSWORD_LENGTH, max = 200)
        String temporaryPassword) {}
