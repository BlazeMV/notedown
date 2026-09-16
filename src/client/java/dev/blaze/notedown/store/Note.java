package dev.blaze.notedown.store;

import java.nio.file.Path;

public record Note(ScopeDir scope, Path file, String title, String body, long lastModified) {

    public String fileName() {
        return file.getFileName().toString();
    }

    public boolean sameFile(Note other) {
        return other != null && file.toAbsolutePath().normalize().equals(other.file.toAbsolutePath().normalize());
    }
}
