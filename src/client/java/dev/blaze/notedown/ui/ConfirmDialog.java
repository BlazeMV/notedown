package dev.blaze.notedown.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.util.List;

public final class ConfirmDialog extends Screen {

    private static final int BOX_W = 260;

    private final Screen parent;
    private final Component body;
    private final Component yesLabel;
    private final Runnable onYes;
    private List<FormattedCharSequence> bodyLines = List.of();
    private int boxH;

    public ConfirmDialog(Screen parent, Component title, Component body, Component yesLabel, Runnable onYes) {
        super(title);
        this.parent = parent;
        this.body = body;
        this.yesLabel = yesLabel;
        this.onYes = onYes;
    }

    public static void openLink(Screen parent, String url) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ConfirmDialog(parent, Messages.t("dialog.link_title"), Messages.t("dialog.link_body", url),
                Messages.t("button.open"), () -> Util.getPlatform().openUri(url)));
    }

    @Override
    protected void init() {
        bodyLines = font.split(body, BOX_W - 2 * Theme.PAD);
        boxH = Theme.PAD + font.lineHeight + 6 + bodyLines.size() * (font.lineHeight + 2) + 10 + Theme.BUTTON_HEIGHT + Theme.PAD;
        int x = (width - BOX_W) / 2;
        int y = (height - boxH) / 2;
        int by = y + boxH - Theme.PAD - Theme.BUTTON_HEIGHT;
        int bw = (BOX_W - 3 * Theme.PAD) / 2;
        addRenderableWidget(new FlatButton(x + Theme.PAD, by, bw, Theme.BUTTON_HEIGHT, yesLabel, this::confirm));
        addRenderableWidget(new FlatButton(x + 2 * Theme.PAD + bw, by, bw, Theme.BUTTON_HEIGHT, Messages.t("button.cancel"), this::onClose));
    }

    private void confirm() {
        onYes.run();
        if (minecraft.gui.screen() == this) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = (width - BOX_W) / 2;
        int y = (height - boxH) / 2;
        g.fill(x, y, x + BOX_W, y + boxH, Theme.PANEL_DARK);
        Theme.border(g, x, y, x + BOX_W, y + boxH, Theme.BORDER);
        g.text(font, title, x + Theme.PAD, y + Theme.PAD, Theme.TEXT);
        int ty = y + Theme.PAD + font.lineHeight + 6;
        for (FormattedCharSequence line : bodyLines) {
            g.text(font, line, x + Theme.PAD, ty, Theme.TEXT_MUTED);
            ty += font.lineHeight + 2;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
