package pl.fotozielnik.users;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.fotozielnik.shared.security.CurrentUser;

/**
 * JSON sign-in for the SPA (ADR-0005, FR-001). Sign-out is {@code POST /api/auth/logout}, handled by Spring
 * Security's logout filter (see {@code SecurityConfig}).
 */
@RestController
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final LoginThrottle throttle;
    private final UserService users;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            LoginThrottle throttle,
            UserService users) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        // New session id against session fixation; new CSRF token so a token seen before sign-in is useless.
        this.sessionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
        this.throttle = throttle;
        this.users = users;
    }

    /**
     * Makes sure the {@code XSRF-TOKEN} cookie is set. The SPA calls it before its first unsafe request when the
     * cookie is missing (normally any earlier response already set it).
     */
    @GetMapping("/api/auth/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // loading the deferred token writes the cookie
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/login")
    MeResponse login(@Valid @RequestBody LoginRequest login, HttpServletRequest request, HttpServletResponse response) {
        String ip = request.getRemoteAddr();
        throttle.check(login.email(), ip);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.email(), login.password()));
        } catch (AuthenticationException e) {
            throttle.recordFailure(login.email(), ip);
            throw e;
        }
        throttle.recordSuccess(login.email());

        request.getSession(); // ChangeSessionIdAuthenticationStrategy needs a session to rotate
        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken(); // send the rotated CSRF cookie with this response
        }
        return users.me(CurrentUser.id(authentication));
    }

    @GetMapping("/api/me")
    MeResponse me(Authentication authentication) {
        return users.me(CurrentUser.id(authentication));
    }

    /** Other sessions of the user are signed out; this one stays signed in. */
    @PutMapping("/api/me/password")
    ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest body, Authentication authentication, HttpSession session) {
        UUID userId = CurrentUser.id(authentication);
        users.changeOwnPassword(userId, body, session.getId());
        return ResponseEntity.noContent().build();
    }
}
