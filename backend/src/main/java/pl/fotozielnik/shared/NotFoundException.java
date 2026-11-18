package pl.fotozielnik.shared;

/** Thrown by services when a requested resource does not exist; rendered as HTTP 404 ProblemDetail. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
