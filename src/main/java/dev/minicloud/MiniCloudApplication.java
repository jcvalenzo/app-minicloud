package dev.minicloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniCloudApplication.class, args);
    }
}
