package pl.fotozielnik.users;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import pl.fotozielnik.shared.TooManyRequestsException;

/**
 * Slows down password guessing (ADR-0005): too many failed sign-ins for one email or from one IP address within
 * a window block further attempts until the oldest failure leaves the window. In memory - one app instance,
 * and a restart forgetting the counters is acceptable.
 */
@Component
class LoginThrottle {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_FAILURES_PER_EMAIL = 5;
    static final int MAX_FAILURES_PER_IP = 30;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Throws {@link TooManyRequestsException} if this email or IP must wait before trying again. */
    void check(String email, String ip) {
        Instant now = clock.instant();
        Duration wait = max(
                waitFor(emailKey(email), MAX_FAILURES_PER_EMAIL, now), waitFor(ipKey(ip), MAX_FAILURES_PER_IP, now));
        if (!wait.isZero()) {
            throw new TooManyRequestsException(
                    "auth.too-many-attempts", "Too many failed sign-in attempts. Try again later.", wait);
        }
    }

    void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        if (failures.size() > MAX_TRACKED_KEYS) {
            failures.values().removeIf(q -> isExpired(q, now));
        }
        add(emailKey(email), now);
        add(ipKey(ip), now);
    }

    void recordSuccess(String email) {
        failures.remove(emailKey(email));
    }

    private Duration waitFor(String key, int limit, Instant now) {
        Deque<Instant> q = failures.get(key);
        if (q == null) {
            return Duration.ZERO;
        }
        synchronized (q) {
            prune(q, now);
            if (q.size() < limit) {
                return Duration.ZERO;
            }
            // blocked until enough failures age out of the window to drop below the limit
            Instant unblockAt =
                    q.stream().skip(q.size() - limit).findFirst().orElseThrow().plus(WINDOW);
            return Duration.between(now, unblockAt).plusSeconds(1);
        }
    }

    private void add(String key, Instant now) {
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, now);
            q.addLast(now);
        }
    }

    private static void prune(Deque<Instant> q, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) {
            q.removeFirst();
        }
    }

    private static boolean isExpired(Deque<Instant> q, Instant now) {
        synchronized (q) {
            prune(q, now);
            return q.isEmpty();
        }
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String emailKey(String email) {
        return "e:" + email.strip().toLowerCase(Locale.ROOT);
    }

    private static String ipKey(String ip) {
        return "i:" + ip;
    }
}
