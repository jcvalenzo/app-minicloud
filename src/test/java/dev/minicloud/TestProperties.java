package dev.minicloud;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import dev.minicloud.config.MiniCloudProperties;
import dev.minicloud.config.MiniCloudProperties.Login;
import dev.minicloud.config.MiniCloudProperties.Storage;
import dev.minicloud.config.MiniCloudProperties.Upload;
import dev.minicloud.config.MiniCloudProperties.UserEntry;
import org.springframework.util.unit.DataSize;

public final class TestProperties {

    /** BCrypt (cost 10) hash used only in tests. */
    public static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private TestProperties() {
    }

    public static MiniCloudProperties withUsers(String... usernames) {
        List<UserEntry> users = java.util.Arrays.stream(usernames).map(u -> new UserEntry(u, HASH)).toList();
        return new MiniCloudProperties(
                new Storage(Path.of("/data")),
                new Upload(DataSize.ofMegabytes(100), DataSize.ofMegabytes(1), true),
                new Login(3, Duration.ofMinutes(5)),
                users);
    }
}
