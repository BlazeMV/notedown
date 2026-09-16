package dev.blaze.notedown;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.blaze.notedown.gui.SettingsScreen;

public final class NotedownModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<SettingsScreen> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
