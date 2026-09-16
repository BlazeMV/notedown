package dev.blaze.notedown.ui;

import dev.blaze.notedown.markdown.TextMeasure;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public record FontMeasure(Font font) implements TextMeasure {

    private static final Style BOLD = Style.EMPTY.withBold(true);

    @Override
    public int width(String text, boolean bold) {
        return bold ? font.width(Component.literal(text).withStyle(BOLD)) : font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }
}
