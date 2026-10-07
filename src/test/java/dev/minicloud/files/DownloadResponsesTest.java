package dev.minicloud.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.regex.Pattern;

import dev.minicloud.scan.ScanResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class DownloadResponsesTest {

    @Test
    void shouldServeAsAttachmentWithSafeHeaders() {
        ResponseEntity<Resource> response = DownloadResponses.attachment(download("informe año.pdf"));
        HttpHeaders headers = response.getHeaders();

        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''informe%20a%C3%B1o.pdf");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Content-Security-Policy")).isEqualTo("sandbox");
        assertThat(headers.getCacheControl()).contains("no-store").contains("private");
        assertThat(headers.getContentLength()).isEqualTo(42);
    }

    @Test
    void shouldStreamFromDiskInsteadOfBuffering() {
        ResponseEntity<Resource> response = DownloadResponses.attachment(download("a.txt"));

        assertThat(response.getBody()).isInstanceOf(FileSystemResource.class);
    }

    @Test
    void shouldNotAllowHeaderInjectionThroughQuotes() {
        String original = "a\"; filename=evil.html";
        String disposition = DownloadResponses.attachment(download(original))
                .getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(disposition).doesNotContain("\r", "\n");
        ContentDisposition parsed = ContentDisposition.parse(disposition);
        assertThat(parsed.isAttachment()).isTrue();
        assertThat(parsed.getFilename()).isEqualTo(original);
        assertThat(Pattern.compile("(?i)(?:^|;)\\s*filename=").matcher(withoutQuotedStrings(disposition)).results())
                .hasSize(1);
    }

    /** Removes quoted-string values so only real header parameters remain. */
    private static String withoutQuotedStrings(String header) {
        return header.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"");
    }

    private static Download download(String name) {
        FileMetadata meta = new FileMetadata(FileId.generate(), "alice", name, 42, "00", "pdf",
                Instant.parse("2026-01-01T00:00:00Z"), ScanResult.NOT_SCANNED);
        return new Download(Path.of("/data/files/alice/" + meta.id() + ".bin"), meta);
    }
}
