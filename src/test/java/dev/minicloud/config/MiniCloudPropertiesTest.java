package dev.minicloud.config;

import static dev.minicloud.TestProperties.HASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import dev.minicloud.TestProperties;
import dev.minicloud.config.MiniCloudProperties.Login;
import dev.minicloud.config.MiniCloudProperties.Storage;
import dev.minicloud.config.MiniCloudProperties.Upload;
import dev.minicloud.config.MiniCloudProperties.UserEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

class MiniCloudPropertiesTest {

    @Test
    void shouldRequireAtLeastOneUser() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MiniCloudProperties(
                new Storage(Path.of("/data")),
                new Upload(DataSize.ofMegabytes(1), DataSize.ofMegabytes(1), true),
                new Login(5, Duration.ofMinutes(5)),
                List.of()));
    }

    @Test
    void shouldRejectDuplicateUsernames() {
        assertThatIllegalArgumentException().isThrownBy(() -> TestProperties.withUsers("juan", "juan"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Juan", "../x", "a/b", "_juan", "a.b", "abcdefghijklmnopqrstuvwxyz0123456789"})
    void shouldRejectInvalidUsername(String username) {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserEntry(username, HASH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret", "{noop}secret", "$2a$04$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
            "$2a$10$tooShort"})
    void shouldRejectNonBcryptOrWeakHash(String hash) {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserEntry("juan", hash));
    }

    @Test
    void shouldRejectRelativeStorageRoot() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Storage(Path.of("data")));
    }

    @Test
    void shouldNotExposePasswordHashInToString() {
        assertThat(new UserEntry("juan", HASH).toString()).doesNotContain(HASH).contains("juan");
        assertThat(TestProperties.withUsers("juan").toString()).doesNotContain(HASH);
    }

    @Test
    void shouldExposeHighestBcryptCost() {
        String cost12 = "$2y$12$" + HASH.substring(7);
        MiniCloudProperties properties = new MiniCloudProperties(
                new Storage(Path.of("/data")),
                new Upload(DataSize.ofMegabytes(1), DataSize.ofMegabytes(1), true),
                new Login(5, Duration.ofMinutes(5)),
                List.of(new UserEntry("juan", HASH), new UserEntry("ana", cost12)));

        assertThat(properties.maxBcryptCost()).isEqualTo(12);
    }
}
