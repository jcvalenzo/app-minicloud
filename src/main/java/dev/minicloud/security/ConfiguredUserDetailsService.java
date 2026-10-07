package dev.minicloud.security;

import java.util.Map;
import java.util.stream.Collectors;

import dev.minicloud.config.MiniCloudProperties;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Users come only from configuration (environment variables), never from requests.
 */
@Service
public class ConfiguredUserDetailsService implements UserDetailsService {

    private final Map<String, String> passwordHashes;
    private final LoginAttemptService loginAttempts;

    public ConfiguredUserDetailsService(MiniCloudProperties properties, LoginAttemptService loginAttempts) {
        this.passwordHashes = properties.users().stream()
                .collect(Collectors.toUnmodifiableMap(
                        MiniCloudProperties.UserEntry::username,
                        MiniCloudProperties.UserEntry::passwordHash));
        this.loginAttempts = loginAttempts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String hash = passwordHashes.get(username);
        if (hash == null) {
            throw new UsernameNotFoundException("Unknown user");
        }
        return User.withUsername(username)
                .password(hash)
                .roles("USER")
                .accountLocked(loginAttempts.isLocked(username))
                .build();
    }
}
