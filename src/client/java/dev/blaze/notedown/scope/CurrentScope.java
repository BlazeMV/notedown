package dev.blaze.notedown.scope;

import dev.blaze.notedown.store.NoteScope;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.store.ScopeDir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.util.ArrayList;
import java.util.List;

public final class CurrentScope {

    private CurrentScope() {}

    /** Current world/server scope first (when in one), global last. */
    public static List<ScopeDir> dirs(NoteStore store, Minecraft mc) {
        List<ScopeDir> out = new ArrayList<>(2);
        if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
            String world = mc.getSingleplayerServer().getWorldPath(LevelResource.ICON_FILE).getParent().getFileName().toString();
            out.add(store.dir(NoteScope.LOCAL, world));
        } else if (mc.getCurrentServer() != null) {
            ServerData server = mc.getCurrentServer();
            boolean byName = server.type() == ServerData.Type.LAN || server.type() == ServerData.Type.REALM;
            out.add(store.dir(NoteScope.SERVER, byName ? server.name : server.ip));
        }
        out.add(global(store));
        return out;
    }

    public static ScopeDir global(NoteStore store) {
        return store.dir(NoteScope.GLOBAL, null);
    }
}
