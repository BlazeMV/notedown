package dev.blaze.notedown.gui;

import dev.blaze.notedown.Notedown;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FileDialogs {

    private FileDialogs() {}

    /** Native open-file dialog for .md/.txt files; empty when cancelled. Runs on the calling (render) thread. */
    public static List<Path> openNoteFiles() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(2);
            filters.put(stack.UTF8("*.md"));
            filters.put(stack.UTF8("*.txt"));
            filters.flip();
            String result = TinyFileDialogs.tinyfd_openFileDialog("Import notes", (CharSequence) null, filters,
                    "Markdown or text (*.md, *.txt)", true);
            if (result == null || result.isBlank()) {
                return List.of();
            }
            List<Path> paths = new ArrayList<>();
            for (String part : result.split("\\|")) {
                if (!part.isBlank()) {
                    paths.add(Path.of(part));
                }
            }
            return paths;
        } catch (RuntimeException e) {
            Notedown.LOGGER.error("File dialog failed", e);
            return List.of();
        }
    }
}
