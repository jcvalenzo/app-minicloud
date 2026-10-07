package dev.minicloud.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import dev.minicloud.config.MiniCloudProperties;

/**
 * Locks a configured user for a short period after repeated login failures.
 * A locked account is rejected before BCrypt runs, so brute force does not burn CPU.
 * Only configured usernames are tracked, which keeps the map bounded.
 */
public class LoginAttemptService {

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Set<String> knownUsers;
    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(MiniCloudProperties properties, Clock clock) {
        this.knownUsers = properties.users().stream()
                .map(MiniCloudProperties.UserEntry::username)
                .collect(Collectors.toUnmodifiableSet());
        this.maxFailures = properties.login().maxFailures();
        this.lockout = properties.login().lockout();
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        Attempts current = attempts.get(username);
        return current != null && current.lockedUntil() != null && clock.instant().isBefore(current.lockedUntil());
    }

    public void onFailure(String username) {
        if (username == null || !knownUsers.contains(username)) {
            return;
        }
        attempts.compute(username, (user, current) -> {
            Instant now = clock.instant();
            int failures = current == null || isExpired(current, now) ? 1 : current.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockout) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void onSuccess(String username) {
        if (username != null) {
            attempts.remove(username);
        }
    }

    private static boolean isExpired(Attempts current, Instant now) {
        return current.lockedUntil() != null && !now.isBefore(current.lockedUntil());
    }
}
