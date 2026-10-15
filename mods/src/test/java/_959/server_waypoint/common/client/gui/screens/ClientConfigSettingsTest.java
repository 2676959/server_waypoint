package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rendering setters load {@code OptimizedWaypointRenderer}, which reads the Minecraft font when
 * its class loads, so only the auto-sync settings are reset here. Rendering resets are checked in game.
 */
class ClientConfigSettingsTest {
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    @AfterEach
    void clearLoadedMapMods() {
        ClientConfig.isXaerosMinimapLoaded = false;
        ClientConfig.isVoxelMapLoaded = false;
    }

    @Test
    void f1RenderingIsExposedAndResetsToHidden() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        ClientConfigSettings.BooleanSetting setting = ClientConfigSettings.RENDER_UNDER_F1;
        assertTrue(ClientConfigSettings.RENDERING.contains(setting));
        setting.set(config, true);
        assertTrue(config.isRenderWaypointsUnderF1());
        assertFalse(ClientConfigSettings.allDefault(config, ClientConfigSettings.RENDERING));

        setting.reset(config);

        assertFalse(config.isRenderWaypointsUnderF1());
        assertTrue(ClientConfigSettings.allDefault(config, ClientConfigSettings.RENDERING));
        assertTranslation(setting.defaultText(), "server_waypoint.config.off");
    }

    @Test
    void everyIntegerDefaultIsInsideItsRange() {
        for (ClientConfigSettings.Setting setting : ClientConfigSettings.RENDERING) {
            if (setting instanceof ClientConfigSettings.IntSetting intSetting) {
                assertTrue(intSetting.min() <= intSetting.defaultValue()
                        && intSetting.defaultValue() <= intSetting.max(), intSetting.text().labelKey());
            }
        }
    }

    @Test
    void aConfigParsedFromEmptyJsonIsAtEveryDefault() {
        ClientConfig.isXaerosMinimapLoaded = true;
        ClientConfig.isVoxelMapLoaded = true;
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        List<ClientConfigSettings.Setting> settings =
                ClientConfigSettings.forScreen(Set.of(UploadTarget.XAERO, UploadTarget.VOXELMAP));

        for (ClientConfigSettings.Setting setting : settings) {
            assertTrue(setting.isDefault(config), setting.text().labelKey());
        }
        assertTrue(ClientConfigSettings.allDefault(config, settings));
    }

    @Test
    void aChangedValueIsNotAtItsDefault() {
        ClientConfig config = GSON.fromJson("{\"waypointScalingFactor\": 150}", ClientConfig.class);

        assertFalse(ClientConfigSettings.SCALE.isDefault(config));
        assertTrue(ClientConfigSettings.LOCAL_WAYPOINT_RANGE.isDefault(config));
        assertFalse(ClientConfigSettings.allDefault(config, ClientConfigSettings.RENDERING));
    }

    @Test
    void resettingAutoSyncRestoresItAndKeepsTheManagerState() {
        ClientConfig.isXaerosMinimapLoaded = true;
        ClientConfig config = GSON.fromJson("""
                {
                  "autoSyncToXaerosMinimap": false,
                  "waypointManagerSortMode": "NAME",
                  "waypointManagerSortReversed": true,
                  "waypointManagerGroupByLists": false,
                  "waypointManagerShowAllDimensions": true
                }
                """, ClientConfig.class);
        ClientConfigSettings.BooleanSetting autoSync = ClientConfigSettings.autoSync(UploadTarget.XAERO);
        assertFalse(autoSync.isDefault(config));

        ClientConfigSettings.resetAll(config, List.of(autoSync));

        assertTrue(autoSync.isDefault(config));
        assertEquals(WaypointSorting.SortMode.NAME, config.getWaypointManagerSortMode());
        assertTrue(config.isWaypointManagerSortReversed());
        assertFalse(config.isWaypointManagerGroupByLists());
        assertTrue(config.isWaypointManagerShowAllDimensions());
    }

    @Test
    void settingsOfMissingMapModsAreLeftOut() {
        assertEquals(ClientConfigSettings.RENDERING, ClientConfigSettings.forScreen(Set.of()));

        List<ClientConfigSettings.Setting> withXaero = ClientConfigSettings.forScreen(Set.of(UploadTarget.XAERO));

        assertEquals(ClientConfigSettings.RENDERING.size() + 2, withXaero.size());
        assertEquals("server_waypoint.map_mod.xaeros_minimap",
                withXaero.get(ClientConfigSettings.RENDERING.size()).text().argumentKey());
    }

    @Test
    void defaultSetPreferencePersistsAndResetsWithoutChangingAutoSync() {
        ClientConfig config = GSON.fromJson("{\"autoSyncToXaerosMinimap\":false}", ClientConfig.class);
        var setting = ClientConfigSettings.XAERO_DEFAULT_LIST_DIRECT_SYNC;
        assertTrue(ClientConfigSettings.forScreen(Set.of(UploadTarget.XAERO)).contains(setting));
        assertFalse(ClientConfigSettings.forScreen(Set.of(UploadTarget.VOXELMAP)).contains(setting));
        setting.set(config, true);
        ClientConfig restored = GSON.fromJson(GSON.toJson(config), ClientConfig.class);
        assertTrue(setting.get(restored));
        assertFalse(setting.isDefault(restored));

        setting.reset(restored);
        assertTranslation(setting.defaultText(), "server_waypoint.config.off");

        assertFalse(setting.get(restored));
        assertTrue(setting.isDefault(restored));
        ClientConfig.isXaerosMinimapLoaded = true;
        assertFalse(restored.isAutoSyncToXaerosMinimap());
    }

    @Test
    void defaultsAreFormattedWithTheirUnits() {
        assertTranslation(ClientConfigSettings.SCALE.defaultText(), "server_waypoint.config.value.percent", 100);
        assertTranslation(ClientConfigSettings.LOCAL_WAYPOINT_RANGE.defaultText(), "server_waypoint.config.value.chunks", 12);
        assertEquals("128", ClientConfigSettings.BACKGROUND_OPACITY.defaultText().getString());
        assertTranslation(ClientConfigSettings.SHOW_WAYPOINTS.defaultText(), "server_waypoint.config.on");
        assertTranslation(ClientConfigSettings.ValueFormat.CHUNKS.unit(), "server_waypoint.config.unit.chunks");
    }

    @Test
    void mapModTextNamesTheMapMod() {
        Component label = ClientConfigSettings.autoSync(UploadTarget.VOXELMAP).text().label();
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, label.getContents());

        assertEquals("server_waypoint.config.map_mod.auto_sync", contents.getKey());
        Component name = assertInstanceOf(Component.class, contents.getArgs()[0]);
        assertTranslation(name, "server_waypoint.map_mod.voxelmap");
    }

    private static void assertTranslation(Component component, String key, Object... args) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, component.getContents());
        assertEquals(key, contents.getKey());
        assertArrayEquals(args, contents.getArgs());
    }
}
