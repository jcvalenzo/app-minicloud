package dev.minicloud.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FilenameValidatorTest {

    private final FilenameValidator validator = new FilenameValidator();

    @ParameterizedTest
    @ValueSource(strings = {"informe.pdf", "año 2024.txt", "foto (1).JPG", "sin-extension"})
    void shouldAcceptValidNames(String name) {
        assertThat(validator.validate(name)).isEqualTo(name);
    }

    @Test
    void shouldNormalizeToNfc() {
        String nfd = Normalizer.normalize("ñandú.txt", Normalizer.Form.NFD);

        assertThat(validator.validate(nfd)).isEqualTo("ñandú.txt");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", ".", "..", "../secret.txt", "../../secret.txt", "..\\..\\secret.txt",
            "/etc/passwd", "C:\\x.txt", "a/b.txt", "a:b.txt", "a\0b.txt", "a\r\nb.txt", "a\tb.txt",
            "gpj.\u202Eexe", " lead.txt", "trail.txt ", ".hidden", "trailing."})
    void shouldRejectUnsafeNames(String name) {
        assertThatThrownBy(() -> validator.validate(name))
                .isInstanceOfSatisfying(UploadRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(UploadRejectedException.Reason.INVALID_NAME));
    }

    @Test
    void shouldRejectNamesLongerThan255Bytes() {
        String name = "ñ".repeat(128) + ".txt"; // 256 + 4 bytes in UTF-8

        assertThatThrownBy(() -> validator.validate(name)).isInstanceOf(UploadRejectedException.class);
        assertThat(validator.validate("a".repeat(251) + ".txt")).hasSize(255);
    }
}
