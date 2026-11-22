package pl.fotozielnik.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import pl.fotozielnik.shared.TooManyRequestsException;

class LoginThrottleTest {

    private final MutableClock clock = new MutableClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    void blocksAnEmailAfterTooManyFailuresUntilTheWindowPasses() {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES_PER_EMAIL; i++) {
            throttle.check("a@x.pl", "1.1.1.1");
            throttle.recordFailure("a@x.pl", "1.1.1.1");
            clock.advance(Duration.ofSeconds(10));
        }
        assertThatThrownBy(() -> throttle.check("A@x.pl ", "2.2.2.2"))
                .isInstanceOfSatisfying(
                        TooManyRequestsException.class,
                        e -> assertThat(e.retryAfter()).isPositive().isLessThanOrEqualTo(LoginThrottle.WINDOW));
        assertThatCode(() -> throttle.check("other@x.pl", "1.1.1.1")).doesNotThrowAnyException();

        clock.advance(LoginThrottle.WINDOW);
        assertThatCode(() -> throttle.check("a@x.pl", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void blocksAnIpTryingManyEmails() {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES_PER_IP; i++) {
            throttle.recordFailure("user" + i + "@x.pl", "3.3.3.3");
        }
        assertThatThrownBy(() -> throttle.check("fresh@x.pl", "3.3.3.3")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void successResetsTheEmailCounter() {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES_PER_EMAIL - 1; i++) {
            throttle.recordFailure("a@x.pl", "1.1.1.1");
        }
        throttle.recordSuccess("a@x.pl");
        throttle.recordFailure("a@x.pl", "1.1.1.1");
        assertThatCode(() -> throttle.check("a@x.pl", "1.1.1.1")).doesNotThrowAnyException();
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-18T20:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
