package dev.blaze.notedown.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class NoteStore {

    public static final long MAX_EDITABLE_BYTES = 1024 * 1024;

    private static final Pattern MARKERS = Pattern.compile("^\\s*(?:#{1,6}\\s+|>\\s*|(?:[-*+]|\\d+[.)])\\s+(?:\\[[ xX]\\]\\s+)?)|[*_`~]");

    private final Path root;
    private final Consumer<String> warn;

    public NoteStore(Path root, Consumer<String> warn) {
        this.root = root;
        this.warn = warn;
    }

    public Path root() {
        return root;
    }

    public ScopeDir dir(NoteScope kind, String name) {
        Path dir = kind == NoteScope.GLOBAL
                ? root.resolve(kind.folder())
                : root.resolve(kind.folder()).resolve(NoteFiles.sanitize(name));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ScopeDir(kind, dir);
    }

    public List<Note> list(ScopeDir scope) {
        List<Note> notes = new ArrayList<>();
        if (!Files.isDirectory(scope.dir())) {
            return notes;
        }
        try (Stream<Path> files = Files.list(scope.dir())) {
            files.filter(NoteFiles::isNote).forEach(file -> load(scope, file).ifPresent(notes::add));
        } catch (IOException e) {
            warn.accept("Cannot list " + scope.dir() + ": " + e.getMessage());
        }
        notes.sort(Comparator.comparingLong(Note::lastModified).reversed());
        return notes;
    }

    public Optional<Note> read(ScopeDir scope, String title) {
        Path file = scope.dir().resolve(title + NoteFiles.EXT);
        return NoteFiles.isNote(file) ? load(scope, file) : Optional.empty();
    }

    public Note create(ScopeDir scope, String title, String body) throws IOException {
        Path file = NoteFiles.uncolliding(scope.dir(), NoteFiles.sanitize(title), null);
        return write(scope, file, body);
    }

    public Note save(Note note, String newTitle, String newBody, ScopeDir newScope) throws IOException {
        boolean sameScope = newScope.dir().equals(note.scope().dir());
        Path target = NoteFiles.uncolliding(newScope.dir(), NoteFiles.sanitize(newTitle), sameScope ? note.file() : null);
        if (!target.equals(note.file())) {
            Optional<PinIndex.Pin> pin = index(note.scope()).get(note.fileName());
            Files.createDirectories(newScope.dir());
            if (sameScope && target.getFileName().toString().equalsIgnoreCase(note.fileName())) {
                Path viaTmp = target.resolveSibling(target.getFileName() + ".renaming");
                Files.move(note.file(), viaTmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(viaTmp, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(note.file(), target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (pin.isPresent()) {
                PinIndex from = index(note.scope());
                from.remove(note.fileName());
                if (!sameScope) {
                    from.save();
                }
                PinIndex to = sameScope ? from : index(newScope);
                to.put(target.getFileName().toString(), pin.get());
                to.save();
            }
        }
        return write(newScope, target, newBody);
    }

    public Note duplicate(Note note) throws IOException {
        return create(note.scope(), note.title(), note.body());
    }

    public void delete(Note note) throws IOException {
        Files.deleteIfExists(note.file());
        PinIndex idx = index(note.scope());
        if (idx.isPinned(note.fileName())) {
            idx.remove(note.fileName());
            idx.save();
        }
    }

    public Note importFile(ScopeDir scope, Path source) throws IOException {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        String body = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        return create(scope, title, body);
    }

    public PinIndex index(ScopeDir scope) {
        return PinIndex.load(scope.dir(), warn);
    }

    public static List<Note> search(List<Note> notes, String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return notes;
        }
        return notes.stream()
                .filter(n -> n.title().toLowerCase(Locale.ROOT).contains(q) || n.body().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    public static String normalize(String body) {
        return body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static String preview(String body) {
        for (String line : normalize(body).split("\n")) {
            String cleaned = MARKERS.matcher(line).replaceAll("").strip();
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return "";
    }

    private Note write(ScopeDir scope, Path file, String body) throws IOException {
        String normalized = normalize(body);
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, normalized, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Note(scope, file, NoteFiles.titleOf(file), normalized, Files.getLastModifiedTime(file).toMillis());
    }

    private Optional<Note> load(ScopeDir scope, Path file) {
        try {
            String body = normalize(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            return Optional.of(new Note(scope, file, NoteFiles.titleOf(file), body, Files.getLastModifiedTime(file).toMillis()));
        } catch (IOException e) {
            warn.accept("Skipping unreadable note " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }
}
