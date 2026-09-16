package dev.blaze.notedown.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class NoteFiles {

    public static final String EXT = ".md";
    public static final String UNTITLED = "Untitled";

    private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern TRAILING_DOTS = Pattern.compile("\\.+$");

    private NoteFiles() {}

    public static String sanitize(String title) {
        if (title == null) {
            return UNTITLED;
        }
        String s = CONTROL.matcher(title).replaceAll(" ");
        s = ILLEGAL.matcher(s).replaceAll("_").strip();
        s = TRAILING_DOTS.matcher(s).replaceAll("").strip();
        return s.isEmpty() ? UNTITLED : s;
    }

    public static String titleOf(Path file) {
        String name = file.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(EXT) ? name.substring(0, name.length() - EXT.length()) : name;
    }

    public static boolean isNote(Path file) {
        return Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXT);
    }

    public static Path uncolliding(Path dir, String title, Path except) {
        Path candidate = dir.resolve(title + EXT);
        int n = 2;
        while (existsIgnoreCase(dir, candidate) && !isSame(candidate, except)) {
            candidate = dir.resolve(title + " " + n++ + EXT);
        }
        return candidate;
    }

    private static boolean isSame(Path candidate, Path except) {
        if (except == null) {
            return false;
        }
        if (Files.exists(candidate)) {
            try {
                return Files.isSameFile(candidate, except);
            } catch (IOException e) {
                return false;
            }
        }
        return candidate.getFileName().toString().equalsIgnoreCase(except.getFileName().toString())
                && candidate.toAbsolutePath().getParent().equals(except.toAbsolutePath().getParent());
    }

    private static boolean existsIgnoreCase(Path dir, Path candidate) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        String wanted = candidate.getFileName().toString();
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(wanted));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
