package dev.minicloud.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Application configuration. Invalid values fail application startup.
 */
@ConfigurationProperties("minicloud")
public record MiniCloudProperties(Storage storage, Upload upload, Login login, List<UserEntry> users) {

    public static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,31}$");
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$(\\d{2})\\$[./A-Za-z0-9]{53}$");
    private static final int MIN_BCRYPT_COST = 10;

    public MiniCloudProperties {
        Objects.requireNonNull(storage, "minicloud.storage is required");
        Objects.requireNonNull(upload, "minicloud.upload is required");
        Objects.requireNonNull(login, "minicloud.login is required");
        if (users == null || users.isEmpty()) {
            throw new IllegalArgumentException("At least one user must be configured (MINICLOUD_USERS_0_USERNAME / MINICLOUD_USERS_0_PASSWORD_HASH)");
        }
        Set<String> seen = new HashSet<>();
        for (UserEntry user : users) {
            if (!seen.add(user.username())) {
                throw new IllegalArgumentException("Duplicate username: " + user.username());
            }
        }
        users = List.copyOf(users);
    }

    /**
     * Highest BCrypt cost among configured users. The password encoder uses it so that
     * the dummy check for unknown usernames takes as long as a real one.
     */
    public int maxBcryptCost() {
        return users.stream().mapToInt(UserEntry::bcryptCost).max().orElseThrow();
    }

    public record Storage(Path root) {
        public Storage {
            if (root == null || !root.isAbsolute()) {
                throw new IllegalArgumentException("minicloud.storage.root must be an absolute path");
            }
            root = root.normalize();
        }
    }

    public record Upload(DataSize maxFileSize, DataSize minFreeSpace, boolean rejectExecutables) {
        public Upload {
            if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
                throw new IllegalArgumentException("minicloud.upload.max-file-size must be > 0");
            }
            if (minFreeSpace == null || minFreeSpace.toBytes() < 0) {
                throw new IllegalArgumentException("minicloud.upload.min-free-space must be >= 0");
            }
        }
    }

    public record Login(int maxFailures, Duration lockout) {
        public Login {
            if (maxFailures <= 0) {
                throw new IllegalArgumentException("minicloud.login.max-failures must be > 0");
            }
            if (lockout == null || lockout.isNegative() || lockout.isZero()) {
                throw new IllegalArgumentException("minicloud.login.lockout must be > 0");
            }
        }
    }

    public record UserEntry(String username, String passwordHash) {
        public UserEntry {
            if (username == null || !USERNAME.matcher(username).matches()) {
                throw new IllegalArgumentException("Invalid username; expected " + USERNAME.pattern());
            }
            var matcher = passwordHash == null ? null : BCRYPT.matcher(passwordHash);
            if (matcher == null || !matcher.matches()) {
                throw new IllegalArgumentException("Password hash for user '" + username + "' is not a BCrypt hash");
            }
            if (Integer.parseInt(matcher.group(1)) < MIN_BCRYPT_COST) {
                throw new IllegalArgumentException("BCrypt cost for user '" + username + "' must be >= " + MIN_BCRYPT_COST);
            }
        }

        public int bcryptCost() {
            return Integer.parseInt(passwordHash.substring(4, 6));
        }

        @Override
        public String toString() {
            return "UserEntry[username=" + username + ", passwordHash=***]";
        }
    }
}
