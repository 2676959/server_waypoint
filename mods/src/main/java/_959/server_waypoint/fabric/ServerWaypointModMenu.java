//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.client.gui.screens.ClientConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's config button opens the client settings. Mod Menu loads this class only when installed. */
public class ServerWaypointModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClientConfigScreen::new;
    }
}
//?}
