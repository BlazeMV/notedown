package dev.blaze.notedown.store;

import java.nio.file.Path;

public record ScopeDir(NoteScope kind, Path dir) {

    public boolean isGlobal() {
        return kind == NoteScope.GLOBAL;
    }

    public String label() {
        return isGlobal() ? "" : dir.getFileName().toString();
    }
}
