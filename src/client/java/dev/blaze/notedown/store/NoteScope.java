package dev.blaze.notedown.store;

public enum NoteScope {
    GLOBAL("global", "notedown.scope.global"),
    LOCAL("local", "notedown.scope.local"),
    SERVER("server", "notedown.scope.server");

    private final String folder;
    private final String langKey;

    NoteScope(String folder, String langKey) {
        this.folder = folder;
        this.langKey = langKey;
    }

    public String folder() {
        return folder;
    }

    public String langKey() {
        return langKey;
    }
}
