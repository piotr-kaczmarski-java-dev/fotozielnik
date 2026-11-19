package pl.fotozielnik.shared.web;

import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pl.fotozielnik.shared.BadRequestException;
import pl.fotozielnik.shared.ConflictException;
import pl.fotozielnik.shared.NotFoundException;
import pl.fotozielnik.shared.TooManyRequestsException;

/**
 * Every API error is an RFC 9457 {@link ProblemDetail}. Spring MVC's own exceptions (validation, 405, 415...)
 * are handled by the superclass; add a handler here for each domain exception type.
 *
 * <p>Domain errors carry a {@code code} property (e.g. {@code auth.bad-credentials}): the frontend shows a
 * translated message for it and falls back to {@code detail}.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String CODE = "code";

    @ExceptionHandler
    ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.code(), ex.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handleBadRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.code(), ex.getMessage());
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfter().toSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, ex.code(), ex.getMessage()));
    }

    /** Safety net: constraint violations outside Spring MVC's own validation are client errors, not 500s. */
    @ExceptionHandler
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation", ex.getMessage());
    }

    /**
     * Failed sign-in (wrong password, unknown email, deactivated account): one answer for all, so the response
     * does not reveal which accounts exist.
     */
    @ExceptionHandler
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "auth.bad-credentials", "Wrong email or password.");
    }

    private static ProblemDetail problem(HttpStatus status, String code, @Nullable String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }
}
