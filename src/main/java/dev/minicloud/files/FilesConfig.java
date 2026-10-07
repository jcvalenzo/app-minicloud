package dev.minicloud.files;

import java.io.IOException;
import java.time.Clock;

import dev.minicloud.config.MiniCloudProperties;
import dev.minicloud.scan.MalwareScanner;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
class FilesConfig {

    /** Room for multipart boundaries and the other form fields. */
    static final DataSize MULTIPART_OVERHEAD = DataSize.ofMegabytes(1);

    @Bean
    FileStorage fileStorage(MiniCloudProperties properties) throws IOException {
        return new FileStorage(properties.storage().root());
    }

    @Bean
    FileService fileService(FileStorage storage, MalwareScanner scanner, MiniCloudProperties properties, Clock clock) {
        var upload = properties.upload();
        return new FileService(storage, new FilenameValidator(),
                new FileSignatureValidator(upload.rejectExecutables()), scanner,
                upload.maxFileSize().toBytes(), upload.minFreeSpace().toBytes(), clock);
    }

    /**
     * Multipart limits derived from minicloud.upload.max-file-size so the two can never disagree.
     * Parts always spool to disk (threshold 0) inside the storage volume.
     */
    @Bean
    MultipartConfigElement multipartConfigElement(FileStorage storage, MiniCloudProperties properties) {
        DataSize maxFile = properties.upload().maxFileSize();
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setLocation(storage.root().resolve("tmp").toString());
        factory.setMaxFileSize(maxFile);
        factory.setMaxRequestSize(DataSize.ofBytes(maxFile.toBytes() + MULTIPART_OVERHEAD.toBytes()));
        factory.setFileSizeThreshold(DataSize.ofBytes(0));
        return factory.createMultipartConfig();
    }
}
