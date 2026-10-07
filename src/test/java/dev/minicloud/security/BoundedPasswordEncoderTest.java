package dev.minicloud.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;

class BoundedPasswordEncoderTest {

    @Test
    void shouldDelegateWhenPermitAvailable() {
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(new PlainEncoder(null), 1, Duration.ofMillis(10));

        assertThat(encoder.matches("secret", "secret")).isTrue();
        assertThat(encoder.matches("wrong", "secret")).isFalse();
        // permits are released after each check
        assertThat(encoder.matches("secret", "secret")).isTrue();
    }

    @Test
    void shouldRejectAsServiceErrorWhenSaturated() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(new PlainEncoder(() -> {
            inside.countDown();
            await(release);
        }), 1, Duration.ofMillis(10));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> busy = executor.submit(() -> encoder.matches("secret", "secret"));
            inside.await();

            // A service exception (not bad credentials) so the account lockout counter is not touched.
            assertThatThrownBy(() -> encoder.matches("secret", "secret"))
                    .isInstanceOf(AuthenticationServiceException.class);

            release.countDown();
            assertThat(busy.get()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record PlainEncoder(Runnable onMatch) implements PasswordEncoder {
        @Override
        public String encode(CharSequence raw) {
            return raw.toString();
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            if (onMatch != null) {
                onMatch.run();
            }
            return raw.toString().equals(encoded);
        }
    }
}
