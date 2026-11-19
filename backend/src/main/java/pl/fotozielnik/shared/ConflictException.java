package pl.fotozielnik.shared;

/**
 * The request clashes with the current state (e.g. an email that is already taken); rendered as HTTP 409.
 * {@code code} is a stable machine-readable key the frontend translates (e.g. {@code user.email-taken}).
 */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
