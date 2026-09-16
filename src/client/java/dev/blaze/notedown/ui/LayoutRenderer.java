package dev.blaze.notedown.ui;

import dev.blaze.notedown.markdown.Layout;
import dev.blaze.notedown.markdown.Layouter;
import dev.blaze.notedown.markdown.TextStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class LayoutRenderer {

    private LayoutRenderer() {}

    /** Draws layout with its origin at (originX, originY), scaled by scale, clipped to the given screen rect. */
    public static void draw(GuiGraphicsExtractor g, Font font, Layout layout, int originX, int originY, float scale,
                            int clipX1, int clipY1, int clipX2, int clipY2) {
        g.enableScissor(clipX1, clipY1, clipX2, clipY2);
        g.pose().pushMatrix();
        g.pose().translate(originX, originY);
        g.pose().scale(scale, scale);
        for (Layout.Line line : layout.lines()) {
            float top = originY + line.y() * scale;
            float bottom = top + (font.lineHeight * line.scale() + Layouter.LINE_GAP) * scale;
            if (bottom < clipY1 || top > clipY2) {
                continue;
            }
            drawLine(g, font, line);
        }
        g.pose().popMatrix();
        g.disableScissor();
    }

    private static void drawLine(GuiGraphicsExtractor g, Font font, Layout.Line line) {
        int lh = Math.round(font.lineHeight * line.scale());
        int x = line.x();
        int y = line.y();
        if (line.quoted()) {
            int bx = x - Layouter.QUOTE_INDENT + 2;
            g.fill(bx, y - 1, bx + 2, y + lh + Layouter.LINE_GAP, Theme.ACCENT_DARK);
        }
        switch (line.deco().kind()) {
            case BULLET, NUMBER -> {
                String label = line.deco().label();
                g.text(font, label, x - 4 - font.width(label), y, Theme.TEXT_MUTED);
            }
            case TASK -> {
                int bx = x - Layouter.MARKER_WIDTH;
                int by = y + (font.lineHeight - Layouter.TASK_BOX) / 2;
                boolean checked = line.deco().checked();
                Theme.border(g, bx, by, bx + Layouter.TASK_BOX, by + Layouter.TASK_BOX, checked ? Theme.ACCENT : Theme.TEXT_MUTED);
                if (checked) {
                    g.fill(bx + 2, by + 2, bx + Layouter.TASK_BOX - 2, by + Layouter.TASK_BOX - 2, Theme.ACCENT);
                }
            }
            case CODE -> g.fill(x - Layouter.CODE_PAD, y - 1, x + line.width() + Layouter.CODE_PAD, y + lh + Layouter.LINE_GAP + 1, Theme.PANEL_DARK);
            case RULE -> {
                g.fill(x, y + lh / 2, x + line.width(), y + lh / 2 + 1, Theme.TEXT_DISABLED);
                return;
            }
            case NONE -> { }
        }
        if (line.scale() != 1f) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(line.scale(), line.scale());
            drawRuns(g, font, line, 0, 0);
            g.pose().popMatrix();
        } else {
            drawRuns(g, font, line, x, y);
        }
    }

    private static void drawRuns(GuiGraphicsExtractor g, Font font, Layout.Line line, int baseX, int baseY) {
        for (Layout.Run run : line.runs()) {
            int rx = baseX + run.x();
            Component text = styled(run);
            if (run.style().code()) {
                g.fill(rx - 1, baseY - 1, rx + font.width(text) + 1, baseY + font.lineHeight, Theme.PANEL_LIGHT);
            }
            g.text(font, text, rx, baseY, color(run.style(), line.quoted()));
        }
    }

    private static Component styled(Layout.Run run) {
        TextStyle s = run.style();
        Style style = Style.EMPTY.withBold(s.bold()).withItalic(s.italic()).withStrikethrough(s.strike())
                .withUnderlined(s.underline() || s.link() != null);
        return Component.literal(run.text()).withStyle(style);
    }

    private static int color(TextStyle s, boolean quoted) {
        if (s.link() != null) {
            return Theme.ACCENT;
        }
        if (s.muted() || quoted) {
            return Theme.TEXT_MUTED;
        }
        return s.code() ? Theme.TEXT_CODE : Theme.TEXT;
    }
}
