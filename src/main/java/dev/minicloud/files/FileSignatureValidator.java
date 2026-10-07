package dev.minicloud.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import dev.minicloud.files.UploadRejectedException.Reason;

/**
 * Checks magic bytes against the declared extension and blocks native executables.
 * This is a best-effort check, not content analysis: downloads are always served as
 * attachments with nosniff, which is the main defense against active content.
 */
public class FileSignatureValidator {

    static final int HEADER_BYTES = 8192;
    public static final String UNKNOWN = "binary";

    private static final Map<String, Predicate<byte[]>> SIGNATURES = Map.ofEntries(
            Map.entry("pdf", h -> startsWith(h, "%PDF-".getBytes())),
            Map.entry("png", h -> startsWith(h, bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))),
            Map.entry("jpg", FileSignatureValidator::isJpeg),
            Map.entry("jpeg", FileSignatureValidator::isJpeg),
            Map.entry("gif", h -> startsWith(h, "GIF87a".getBytes()) || startsWith(h, "GIF89a".getBytes())),
            Map.entry("webp", h -> startsWith(h, "RIFF".getBytes()) && regionMatches(h, 8, "WEBP".getBytes())),
            Map.entry("zip", FileSignatureValidator::isZip),
            Map.entry("docx", FileSignatureValidator::isZip),
            Map.entry("xlsx", FileSignatureValidator::isZip),
            Map.entry("pptx", FileSignatureValidator::isZip),
            Map.entry("odt", FileSignatureValidator::isZip),
            Map.entry("ods", FileSignatureValidator::isZip),
            Map.entry("gz", h -> startsWith(h, bytes(0x1F, 0x8B))),
            Map.entry("txt", FileSignatureValidator::isText),
            Map.entry("csv", FileSignatureValidator::isText),
            Map.entry("md", FileSignatureValidator::isText),
            Map.entry("json", FileSignatureValidator::isText));

    private static final byte[][] EXECUTABLE_MAGIC = {
            bytes('M', 'Z'),
            bytes(0x7F, 'E', 'L', 'F'),
            bytes(0xFE, 0xED, 0xFA, 0xCE),
            bytes(0xFE, 0xED, 0xFA, 0xCF),
            bytes(0xCE, 0xFA, 0xED, 0xFE),
            bytes(0xCF, 0xFA, 0xED, 0xFE),
            bytes(0xCA, 0xFE, 0xBA, 0xBE),
    };

    private final boolean rejectExecutables;

    public FileSignatureValidator(boolean rejectExecutables) {
        this.rejectExecutables = rejectExecutables;
    }

    /**
     * @return the detected type: the verified extension, or {@link #UNKNOWN}
     */
    public String validate(Path file, String filename) throws IOException {
        byte[] header;
        try (InputStream in = Files.newInputStream(file)) {
            header = in.readNBytes(HEADER_BYTES);
        }
        if (rejectExecutables && isExecutable(header)) {
            throw new UploadRejectedException(Reason.EXECUTABLE);
        }
        String extension = extensionOf(filename);
        Predicate<byte[]> signature = SIGNATURES.get(extension);
        if (signature == null) {
            return UNKNOWN;
        }
        if (!signature.test(header)) {
            throw new UploadRejectedException(Reason.TYPE_MISMATCH);
        }
        return extension;
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isExecutable(byte[] header) {
        for (byte[] magic : EXECUTABLE_MAGIC) {
            if (startsWith(header, magic)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isJpeg(byte[] h) {
        return startsWith(h, bytes(0xFF, 0xD8, 0xFF));
    }

    private static boolean isZip(byte[] h) {
        return startsWith(h, bytes('P', 'K', 0x03, 0x04)) || startsWith(h, bytes('P', 'K', 0x05, 0x06));
    }

    private static boolean isText(byte[] h) {
        for (byte b : h) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return regionMatches(data, 0, prefix);
    }

    private static boolean regionMatches(byte[] data, int offset, byte[] expected) {
        return data.length >= offset + expected.length
                && Arrays.equals(data, offset, offset + expected.length, expected, 0, expected.length);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
