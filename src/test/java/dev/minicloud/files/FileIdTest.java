package dev.minicloud.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FileIdTest {

    @Test
    void shouldParseGeneratedId() {
        FileId id = FileId.generate();

        assertThat(FileId.parse(id.value())).isEqualTo(id);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../x", "..%2f..%2fsecret", "%2e%2e", "0F8FAD5B-D9CB-469F-A165-70867728950E",
            "0f8fad5b-d9cb-469f-a165-70867728950e/../x", "0f8fad5b-d9cb-469f-a165-70867728950e0",
            "0f8fad5b-d9cb-469f-a165-70867728950e.bin"})
    void shouldRejectMalformedIds(String raw) {
        assertThatThrownBy(() -> FileId.parse(raw)).isInstanceOf(StoredFileNotFoundException.class);
    }
}
