package dev.minicloud.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.minicloud.TestProperties;
import jakarta.servlet.MultipartConfigElement;
import dev.minicloud.files.FileMetadata;
import dev.minicloud.files.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final Path ROOT = createRoot();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("minicloud.storage.root", ROOT::toString);
        registry.add("minicloud.users[0].username", () -> "alice");
        registry.add("minicloud.users[0].password-hash", () -> TestProperties.HASH);
        registry.add("minicloud.users[1].username", () -> "bob");
        registry.add("minicloud.users[1].password-hash", () -> TestProperties.HASH);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FileService fileService;

    @Autowired
    MultipartConfigElement multipartConfig;

    @Test
    void shouldDeriveMultipartLimitsFromUploadLimit() throws IOException {
        long maxFile = 100L * 1024 * 1024;
        assertThat(multipartConfig.getMaxFileSize()).isEqualTo(maxFile);
        assertThat(multipartConfig.getMaxRequestSize()).isEqualTo(maxFile + 1024 * 1024);
        assertThat(multipartConfig.getFileSizeThreshold()).isZero();
        assertThat(Path.of(multipartConfig.getLocation())).startsWith(ROOT.toRealPath());
    }

    @Test
    void shouldRedirectAnonymousToLogin() throws Exception {
        mvc.perform(get("/files"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldRejectAnonymousMultipartWithoutReadingIt() throws Exception {
        mvc.perform(multipart("/files").file(new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectPostWithoutCsrf() throws Exception {
        mvc.perform(multipart("/files").file(new MockMultipartFile("file", "a.txt", "text/plain", "abc".getBytes()))
                        .with(user("alice")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldUploadAndRedirect() throws Exception {
        mvc.perform(multipart("/files").file(new MockMultipartFile("file", "subida.txt", "text/plain", "abc".getBytes()))
                        .with(user("alice")).with(csrf()))
                .andExpect(redirectedUrl("/files"))
                .andExpect(flash().attributeExists("message"));

        assertThat(fileService.list("alice")).extracting(FileMetadata::originalName).contains("subida.txt");
    }

    @Test
    void shouldReportRejectedUploadWithoutInternalDetails() throws Exception {
        mvc.perform(multipart("/files").file(new MockMultipartFile("file", "x.png", "image/png", "not a png".getBytes()))
                        .with(user("alice")).with(csrf()))
                .andExpect(redirectedUrl("/files"))
                .andExpect(flash().attribute("error", "El contenido del archivo no coincide con su extensión."));
    }

    @Test
    void shouldDownloadOwnFileAsAttachment() throws Exception {
        FileMetadata meta = store("alice");

        mvc.perform(get("/files/{id}/download", meta.id().value()).with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
    }

    @Test
    void shouldReturnNotFoundForAnotherUsersFile() throws Exception {
        FileMetadata meta = store("alice");

        mvc.perform(get("/files/{id}/download", meta.id().value()).with(user("bob")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/files/{id}/delete", meta.id().value()).with(user("bob")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectEncodedTraversalInId() throws Exception {
        // The Spring Security firewall may reject this before it reaches the controller; any 4xx is a safe outcome.
        mvc.perform(get("/files/{id}/download", "..%2F..%2Fetc%2Fpasswd").with(user("alice")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldReturnNotFoundForMalformedId() throws Exception {
        mvc.perform(get("/files/{id}/download", "not-a-uuid").with(user("alice")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/files/{id}/delete", "not-a-uuid").with(user("alice")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSendSecurityHeaders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    private FileMetadata store(String owner) throws IOException {
        return fileService.upload(owner, "doc.txt", 3, new ByteArrayInputStream("abc".getBytes()));
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("minicloud-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
