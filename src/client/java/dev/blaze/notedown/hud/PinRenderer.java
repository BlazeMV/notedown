package dev.blaze.notedown.hud;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.config.NotedownConfig;
import dev.blaze.notedown.markdown.Layout;
import dev.blaze.notedown.markdown.Layouter;
import dev.blaze.notedown.markdown.TaskToggler;
import dev.blaze.notedown.ui.FontMeasure;
import dev.blaze.notedown.ui.LayoutRenderer;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public final class PinRenderer {

    public enum HitKind { HEADER, CLOSE, HANDLE, TASK, LINK, BODY }

    public record Hit(PinnedNotes.Entry entry, HitKind kind, int sourceLine, String url, PinGeometry.Rect rect) {}

    private static final int WHEEL_STEP = 10;

    private PinRenderer() {}

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, boolean interact, double mouseX, double mouseY) {
        NotedownConfig cfg = ConfigHolder.get();
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        Font font = mc.font;
        FontMeasure measure = new FontMeasure(font);
        for (PinnedNotes.Entry e : PinnedNotes.entries()) {
            PinGeometry.Rect r = PinGeometry.rect(e.pin, sw, sh);
            Layout layout = layout(e, r, cfg, measure);
            int maxScroll = PinGeometry.maxScroll(layout.height(), e.pin.scale(), r, interact);
            e.scroll = Math.max(0, Math.min(maxScroll, e.scroll));
            g.fill(r.x(), r.y(), r.right(), r.bottom(), Theme.withAlpha(0x000000, cfg.pinnedBackgroundOpacity));
            int contentTop = r.y() + PinGeometry.PAD + (interact ? PinGeometry.HEADER : 0);
            LayoutRenderer.draw(g, font, layout, r.x() + PinGeometry.PAD, contentTop - e.scroll, e.pin.scale(),
                    r.x(), contentTop, r.right(), r.bottom() - PinGeometry.PAD);
            if (interact) {
                drawChrome(g, font, e, r, maxScroll, r.contains(mouseX, mouseY));
            }
        }
    }

    private static Layout layout(PinnedNotes.Entry e, PinGeometry.Rect r, NotedownConfig cfg, FontMeasure measure) {
        int innerW = Math.max(Layouter.MIN_WIDTH, Math.round((r.w() - 2 * PinGeometry.PAD) / e.pin.scale()));
        return Notedown.layouts().get(e.note.body(), innerW, cfg.checkedTaskStyle, measure);
    }

    private static void drawChrome(GuiGraphicsExtractor g, Font font, PinnedNotes.Entry e, PinGeometry.Rect r, int maxScroll, boolean hovered) {
        PinGeometry.Rect header = PinGeometry.header(r);
        g.fill(header.x(), header.y(), header.right(), header.bottom(), Theme.PANEL_DARK);
        g.text(font, Theme.ellipsize(font, e.note.title(), r.w() - PinGeometry.HEADER - 6), r.x() + 3, r.y() + 2, Theme.TEXT_MUTED);
        PinGeometry.Rect close = PinGeometry.closeBox(r);
        g.centeredText(font, "×", close.x() + close.w() / 2, close.y() + 2, Theme.TEXT);
        PinGeometry.Rect handle = PinGeometry.handle(r);
        g.fill(handle.right() - 2, handle.bottom() - 6, handle.right(), handle.bottom(), Theme.ACCENT);
        g.fill(handle.right() - 6, handle.bottom() - 2, handle.right(), handle.bottom(), Theme.ACCENT);
        Theme.border(g, r.x(), r.y(), r.right(), r.bottom(), hovered ? Theme.ACCENT : Theme.BORDER);
        if (maxScroll > 0) {
            int track = r.h() - PinGeometry.HEADER - 2 * PinGeometry.PAD;
            int thumb = Math.max(6, (int) ((long) track * track / (track + maxScroll)));
            int ty = r.y() + PinGeometry.HEADER + PinGeometry.PAD + (int) ((long) (track - thumb) * e.scroll / maxScroll);
            g.fill(r.right() - 3, ty, r.right() - 1, ty + thumb, Theme.TEXT_MUTED);
        }
    }

    public static Optional<Hit> hitTest(double mx, double my, int screenW, int screenH, boolean interact) {
        NotedownConfig cfg = ConfigHolder.get();
        FontMeasure measure = new FontMeasure(Minecraft.getInstance().font);
        List<PinnedNotes.Entry> entries = PinnedNotes.entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            PinnedNotes.Entry e = entries.get(i);
            PinGeometry.Rect r = PinGeometry.rect(e.pin, screenW, screenH);
            if (!r.contains(mx, my)) {
                continue;
            }
            if (interact) {
                if (PinGeometry.closeBox(r).contains(mx, my)) {
                    return Optional.of(new Hit(e, HitKind.CLOSE, -1, null, r));
                }
                if (PinGeometry.header(r).contains(mx, my)) {
                    return Optional.of(new Hit(e, HitKind.HEADER, -1, null, r));
                }
                if (PinGeometry.handle(r).contains(mx, my)) {
                    return Optional.of(new Hit(e, HitKind.HANDLE, -1, null, r));
                }
            }
            Layout layout = layout(e, r, cfg, measure);
            int contentTop = r.y() + PinGeometry.PAD + (interact ? PinGeometry.HEADER : 0);
            double lx = (mx - (r.x() + PinGeometry.PAD)) / e.pin.scale();
            double ly = (my - contentTop + e.scroll) / e.pin.scale();
            for (Layout.HitBox h : layout.hitBoxes()) {
                if (h.contains(lx, ly)) {
                    HitKind kind = h.kind() == Layout.HitKind.TASK ? HitKind.TASK : HitKind.LINK;
                    return Optional.of(new Hit(e, kind, h.sourceLine(), h.url(), r));
                }
            }
            return Optional.of(new Hit(e, HitKind.BODY, -1, null, r));
        }
        return Optional.empty();
    }

    public static void toggleTask(PinnedNotes.Entry e, int line) {
        try {
            String toggled = TaskToggler.toggleLine(e.note.body(), line);
            Notedown.store().save(e.note, e.note.title(), toggled, e.scope);
            PinnedNotes.reload();
        } catch (IOException ex) {
            Notedown.LOGGER.error("Failed to save note", ex);
            Messages.chat(Minecraft.getInstance(), Messages.t("message.save_failed"));
        }
    }

    public static void scroll(PinnedNotes.Entry e, double vertical, int screenW, int screenH, boolean interact) {
        PinGeometry.Rect r = PinGeometry.rect(e.pin, screenW, screenH);
        Layout layout = layout(e, r, ConfigHolder.get(), new FontMeasure(Minecraft.getInstance().font));
        int max = PinGeometry.maxScroll(layout.height(), e.pin.scale(), r, interact);
        e.scroll = Math.max(0, Math.min(max, e.scroll - (int) Math.round(vertical * WHEEL_STEP)));
    }
}
