package dev.minicloud.files;

import java.nio.file.Path;

public record Download(Path path, FileMetadata metadata) {
}
