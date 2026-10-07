package dev.minicloud.files;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

import dev.minicloud.files.UploadRejectedException.Reason;

/**
 * Validates the client-supplied display name. Invalid names are rejected, not sanitized.
 * The name is only ever shown and used in Content-Disposition, never as a path.
 */
public class FilenameValidator {

    static final int MAX_BYTES = 255;

    public String validate(String submitted) {
        if (submitted == null) {
            throw invalid();
        }
        String name = Normalizer.normalize(submitted, Normalizer.Form.NFC);
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw invalid();
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid();
        }
        if (name.strip().length() != name.length() || name.startsWith(".") || name.endsWith(".")) {
            throw invalid();
        }
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            if (cp == '/' || cp == '\\' || cp == ':' || isControlOrFormat(cp)) {
                throw invalid();
            }
            i += Character.charCount(cp);
        }
        return name;
    }

    private static boolean isControlOrFormat(int cp) {
        int type = Character.getType(cp);
        return type == Character.CONTROL || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static UploadRejectedException invalid() {
        return new UploadRejectedException(Reason.INVALID_NAME);
    }
}
