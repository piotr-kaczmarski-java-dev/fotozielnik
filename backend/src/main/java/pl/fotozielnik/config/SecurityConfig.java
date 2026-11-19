package pl.fotozielnik.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Deny by default (ADR-0001): every new public endpoint must be listed here on purpose.
 *
 * <p>Authentication (ADR-0005): server-side session (Spring Session JDBC) started by the JSON endpoint
 * {@code POST /api/auth/login} in the users feature. CSRF is on: the SPA reads the {@code XSRF-TOKEN} cookie
 * and sends it back as the {@code X-XSRF-TOKEN} header on unsafe requests.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository)
            throws Exception {
        // Load the token on every request (attribute name null), so the first response already sets the cookie.
        var csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http.authorizeHttpRequests(auth -> auth
                        // error dispatches render the original status (e.g. 403 for a missing CSRF token)
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/api/public/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf")
                        .permitAll()
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        // OpenAPI spec + UI; disabled outside dev by springdoc.* properties (see application.yml).
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // The SPA shell and its assets (prod: served from the jar by SpaConfig). They contain no
                        // user data; every /api call they make is authorized on its own.
                        .requestMatchers(FRONTEND_PAGE)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(csrfRequestHandler))
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // Unauthenticated API calls get 401 (not a login redirect or 403).
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    /**
     * A GET for a frontend file or client-side route: anything outside the API, actuator and OpenAPI paths.
     * Built from path patterns (not raw string checks) so it decodes the path exactly like MVC routing does:
     * {@code /%61pi/photos} counts as {@code /api/**} here too and stays protected.
     */
    private static final RequestMatcher FRONTEND_PAGE = new AndRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/**"),
            new NegatedRequestMatcher(new OrRequestMatcher(
                    PathPatternRequestMatcher.pathPattern("/api"),
                    PathPatternRequestMatcher.pathPattern("/api/**"),
                    PathPatternRequestMatcher.pathPattern("/actuator/**"),
                    PathPatternRequestMatcher.pathPattern("/v3/**"))));

    /** Readable by the SPA (not HttpOnly) by design: the double-submit cookie pattern. */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    /** Shared with the login endpoint, which stores the authenticated context in the session itself. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
