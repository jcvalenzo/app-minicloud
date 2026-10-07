package dev.minicloud.files;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Server-generated file identifier. Parsing is strict so a client value can never form a path.
 */
public record FileId(String value) {

    private static final Pattern FORMAT =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    public FileId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new StoredFileNotFoundException();
        }
    }

    public static FileId generate() {
        return new FileId(UUID.randomUUID().toString());
    }

    public static FileId parse(String raw) {
        return new FileId(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
