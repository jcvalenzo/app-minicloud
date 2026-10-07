package dev.minicloud.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.minicloud.files.UploadRejectedException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSignatureValidatorTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0};
    private static final byte[] PE = {'M', 'Z', (byte) 0x90, 0};
    private static final byte[] ELF = {0x7F, 'E', 'L', 'F', 2, 1};

    @TempDir
    Path dir;

    private final FileSignatureValidator validator = new FileSignatureValidator(true);

    @Test
    void shouldAcceptMatchingSignatures() throws IOException {
        assertThat(validator.validate(write(PNG), "a.png")).isEqualTo("png");
        assertThat(validator.validate(write(JPEG), "a.JPG")).isEqualTo("jpg");
        assertThat(validator.validate(write(PDF), "a.pdf")).isEqualTo("pdf");
        assertThat(validator.validate(write(ZIP), "a.docx")).isEqualTo("docx");
        assertThat(validator.validate(write("hola\nmundo".getBytes()), "a.txt")).isEqualTo("txt");
    }

    @Test
    void shouldRejectPdfContentWithPngExtension() {
        assertRejected(PDF, "a.png", Reason.TYPE_MISMATCH);
    }

    @Test
    void shouldRejectTextFileWithNulBytes() {
        assertRejected(new byte[] {'a', 0, 'b'}, "a.txt", Reason.TYPE_MISMATCH);
    }

    @Test
    void shouldRejectFileShorterThanExpectedSignature() {
        assertRejected(new byte[] {(byte) 0x89, 'P'}, "a.png", Reason.TYPE_MISMATCH);
    }

    @Test
    void shouldRejectExecutablesRegardlessOfExtension() {
        assertRejected(PE, "setup.exe", Reason.EXECUTABLE);
        assertRejected(PE, "report.pdf", Reason.EXECUTABLE);
        assertRejected(ELF, "notes.bin", Reason.EXECUTABLE);
    }

    @Test
    void shouldAllowExecutablesWhenDisabled() throws IOException {
        assertThat(new FileSignatureValidator(false).validate(write(ELF), "tool"))
                .isEqualTo(FileSignatureValidator.UNKNOWN);
    }

    @Test
    void shouldAcceptUnknownExtensionAsBinary() throws IOException {
        assertThat(validator.validate(write(new byte[] {1, 2, 3}), "data.xyz")).isEqualTo(FileSignatureValidator.UNKNOWN);
        assertThat(validator.validate(write(new byte[] {1, 2, 3}), "noextension")).isEqualTo(FileSignatureValidator.UNKNOWN);
    }

    private void assertRejected(byte[] content, String name, Reason reason) {
        assertThatThrownBy(() -> validator.validate(write(content), name))
                .isInstanceOfSatisfying(UploadRejectedException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    private Path write(byte[] content) throws IOException {
        return Files.write(Files.createTempFile(dir, "f", ".part"), content);
    }
}
