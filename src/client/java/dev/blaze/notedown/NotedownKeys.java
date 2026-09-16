package dev.blaze.notedown;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class NotedownKeys {
    public static KeyMapping OPEN;
    public static KeyMapping INTERACT;
    public static KeyMapping QUICK_EDIT;
    public static KeyMapping TOGGLE_PINNED;
    public static KeyMapping NEW_NOTE;

    private NotedownKeys() {}

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(Notedown.id("main"));
        OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.notedown.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category));
        INTERACT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.notedown.interact", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category));
        QUICK_EDIT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.notedown.quick_edit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        TOGGLE_PINNED = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.notedown.toggle_pinned", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        NEW_NOTE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.notedown.new_note", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
    }

    public static List<KeyMapping> all() {
        return List.of(OPEN, INTERACT, QUICK_EDIT, TOGGLE_PINNED, NEW_NOTE);
    }

    /** True while the bound keyboard key is held, even when a screen is open. */
    public static boolean isPhysicallyDown(Minecraft mc, KeyMapping key) {
        InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
        return bound.getType() == InputConstants.Type.KEYSYM && !key.isUnbound()
                && InputConstants.isKeyDown(mc.getWindow(), bound.getValue());
    }
}
