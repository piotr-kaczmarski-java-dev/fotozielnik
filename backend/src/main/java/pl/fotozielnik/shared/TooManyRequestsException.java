package pl.fotozielnik.shared;

import java.time.Duration;

/** The caller must slow down (e.g. too many failed sign-ins); rendered as HTTP 429 with Retry-After. */
public class TooManyRequestsException extends RuntimeException {

    private final String code;
    private final Duration retryAfter;

    public TooManyRequestsException(String code, String message, Duration retryAfter) {
        super(message);
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public String code() {
        return code;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
