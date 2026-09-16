package dev.blaze.notedown.gui;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.store.Note;
import dev.blaze.notedown.store.ScopeDir;
import dev.blaze.notedown.ui.Messages;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;

public final class NoteImport {

    public record Result(int count, Note last) {}

    private NoteImport() {}

    /** Opens the native file dialog and imports every chosen file into scope, chatting the outcome. */
    public static Result run(Minecraft mc, ScopeDir scope) {
        int count = 0;
        Note last = null;
        for (Path file : FileDialogs.openNoteFiles()) {
            try {
                last = Notedown.store().importFile(scope, file);
                count++;
            } catch (IOException e) {
                Notedown.LOGGER.error("Failed to import {}", file, e);
                Messages.chat(mc, Messages.t("message.import_failed"));
            }
        }
        if (count > 0) {
            Messages.chat(mc, Messages.t("message.imported", count));
        }
        return new Result(count, last);
    }
}
