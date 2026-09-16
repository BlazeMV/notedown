package dev.blaze.notedown.gui;

import dev.blaze.notedown.ui.Messages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.ChunkPos;

import java.util.Locale;

public final class GameInfo {

    private GameInfo() {}

    public static boolean inWorld(Minecraft mc) {
        return mc.level != null && mc.player != null;
    }

    public static String coords(Minecraft mc) {
        return mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
    }

    public static String chunk(Minecraft mc) {
        ChunkPos c = mc.player.chunkPosition();
        return c.x() + ", " + c.z();
    }

    public static String biome(Minecraft mc) {
        return mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(key -> {
            String langKey = key.identifier().toLanguageKey("biome");
            String name = I18n.get(langKey);
            return name.equals(langKey) ? prettify(key.identifier().getPath()) : name;
        }).orElseGet(() -> Messages.t("biome.unknown").getString());
    }

    static String prettify(String path) {
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
