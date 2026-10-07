package dev.minicloud.security;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Caps concurrent password checks so that login floods (including unknown usernames,
 * which still run a dummy BCrypt) cannot saturate the CPU.
 * Rejection is a service error, not bad credentials, so it never counts towards account lockout.
 */
public class BoundedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final Semaphore permits;
    private final Duration wait;

    public BoundedPasswordEncoder(PasswordEncoder delegate, int maxConcurrent, Duration wait) {
        this.delegate = delegate;
        this.permits = new Semaphore(maxConcurrent);
        this.wait = wait;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthenticationServiceException("Interrupted");
        }
        if (!acquired) {
            throw new AuthenticationServiceException("Too many concurrent login attempts");
        }
        try {
            return delegate.matches(rawPassword, encodedPassword);
        } finally {
            permits.release();
        }
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
