package pl.fotozielnik;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to the running app like the browser does: keeps cookies (session + XSRF-TOKEN) and sends the CSRF
 * header on unsafe requests. One instance = one browser.
 */
public final class ApiClient {

    @SuppressWarnings("ArrayRecordComponent") // test helper: raw response bytes, never compared
    public record Response(int status, byte[] bytes, java.net.http.HttpHeaders headers) {

        private static final JsonMapper JSON = JsonMapper.builder().build();

        public String body() {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }

        public JsonNode json() {
            return JSON.readTree(bytes);
        }

        public Optional<String> header(String name) {
            return headers.firstValue(name);
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    /** Signs in and fails the test unless it worked. */
    public ApiClient login(String email, String password) {
        Response r = post("/api/auth/login", Map.of("email", email, "password", password));
        assertThat(r.status()).as("login of %s: %s", email, r.body()).isEqualTo(200);
        return this;
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET(), false);
    }

    public Response getWithHeaders(String path, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).GET();
        headers.forEach(b::header);
        return send(b, false);
    }

    public Response post(String path, Object body) {
        return send(json(HttpRequest.newBuilder(uri(path)).POST(jsonBody(body))), true);
    }

    public Response post(String path) {
        return send(HttpRequest.newBuilder(uri(path)).POST(BodyPublishers.noBody()), true);
    }

    public Response put(String path, Object body) {
        return send(json(HttpRequest.newBuilder(uri(path)).PUT(jsonBody(body))), true);
    }

    public Response putBytes(String path, byte[] body, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).PUT(BodyPublishers.ofByteArray(body));
        b.header("Content-Type", "application/octet-stream");
        headers.forEach(b::header);
        return send(b, true);
    }

    public Response delete(String path) {
        return send(HttpRequest.newBuilder(uri(path)).DELETE(), true);
    }

    /** Sends a request without the CSRF header, e.g. to prove CSRF protection is on. */
    public Response postWithoutCsrf(String path, Object body) {
        return send(json(HttpRequest.newBuilder(uri(path)).POST(jsonBody(body))), false);
    }

    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    private Response send(HttpRequest.Builder builder, boolean withCsrf) {
        if (withCsrf) {
            builder.header("X-XSRF-TOKEN", csrfToken());
        }
        try {
            HttpResponse<byte[]> r = http.send(builder.build(), BodyHandlers.ofByteArray());
            return new Response(r.statusCode(), r.body(), r.headers());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String csrfToken() {
        return cookie("XSRF-TOKEN").orElseGet(() -> {
            send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET(), false);
            return cookie("XSRF-TOKEN").orElseThrow(() -> new IllegalStateException("no XSRF-TOKEN cookie"));
        });
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private static HttpRequest.Builder json(HttpRequest.Builder b) {
        return b.header("Content-Type", "application/json").header("Accept", "application/json");
    }

    private static BodyPublisher jsonBody(Object body) {
        return BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body));
    }
}
