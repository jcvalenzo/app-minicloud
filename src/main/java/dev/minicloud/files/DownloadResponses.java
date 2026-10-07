package dev.minicloud.files;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Builds download responses that stream from disk and never let the browser render untrusted content.
 */
public final class DownloadResponses {

    private DownloadResponses() {
    }

    public static ResponseEntity<Resource> attachment(Download download) {
        FileMetadata meta = download.metadata();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(meta.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(meta.size())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .body(new FileSystemResource(download.path()));
    }
}
