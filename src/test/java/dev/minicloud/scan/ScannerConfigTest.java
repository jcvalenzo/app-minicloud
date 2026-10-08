package dev.minicloud.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import dev.minicloud.config.MiniCloudProperties.MalwareBazaar;
import dev.minicloud.config.MiniCloudProperties.Scanner;
import org.junit.jupiter.api.Test;

class ScannerConfigTest {

    @Test
    void shouldUseNoOpByDefault() {
        assertThat(ScannerConfig.create(Scanner.NONE)).isInstanceOf(NoOpMalwareScanner.class);
    }

    @Test
    void shouldCreateMalwareBazaarScannerWithoutContactingService() {
        Scanner scanner = new Scanner(Scanner.Type.MALWAREBAZAAR,
                new MalwareBazaar("key", URI.create("https://mb-api.abuse.ch/api/v1/"), Duration.ofSeconds(5)));

        assertThat(ScannerConfig.create(scanner)).isInstanceOf(MalwareBazaarMalwareScanner.class);
    }
}
