package dev.minicloud.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import dev.minicloud.TestProperties;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock();
    // max-failures 3, lockout 5m
    private final LoginAttemptService service = new LoginAttemptService(TestProperties.withUsers("juan"), clock);

    @Test
    void shouldLockAfterMaxFailures() {
        service.onFailure("juan");
        service.onFailure("juan");
        assertThat(service.isLocked("juan")).isFalse();

        service.onFailure("juan");

        assertThat(service.isLocked("juan")).isTrue();
    }

    @Test
    void shouldUnlockAfterLockout() {
        failTimes(3);

        clock.advance(Duration.ofMinutes(5));

        assertThat(service.isLocked("juan")).isFalse();
    }

    @Test
    void shouldStartCountingAgainAfterLockoutExpires() {
        failTimes(3);
        clock.advance(Duration.ofMinutes(5));

        service.onFailure("juan");

        assertThat(service.isLocked("juan")).isFalse();
    }

    @Test
    void shouldResetOnSuccess() {
        failTimes(2);
        service.onSuccess("juan");
        failTimes(2);

        assertThat(service.isLocked("juan")).isFalse();
    }

    @Test
    void shouldIgnoreUnknownUsers() {
        for (int i = 0; i < 10; i++) {
            service.onFailure("intruder");
        }
        assertThat(service.isLocked("intruder")).isFalse();
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            service.onFailure("juan");
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
