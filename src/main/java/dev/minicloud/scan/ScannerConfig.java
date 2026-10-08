package dev.minicloud.scan;

import dev.minicloud.config.MiniCloudProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ScannerConfig {

    @Bean
    MalwareScanner malwareScanner(MiniCloudProperties properties) {
        return create(properties.scanner());
    }

    static MalwareScanner create(MiniCloudProperties.Scanner scanner) {
        return switch (scanner.type()) {
            case NONE -> new NoOpMalwareScanner();
            case MALWAREBAZAAR -> new MalwareBazaarMalwareScanner(scanner.malwarebazaar().url(),
                    scanner.malwarebazaar().authKey(), scanner.malwarebazaar().timeout());
        };
    }
}
