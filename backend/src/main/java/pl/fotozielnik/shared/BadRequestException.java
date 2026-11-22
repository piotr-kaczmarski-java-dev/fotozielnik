package pl.fotozielnik.shared;

/**
 * A business rule rejected an otherwise well-formed request (e.g. wrong current password); rendered as
 * HTTP 400. {@code code} is a stable key the frontend translates.
 */
public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
