package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.ClientConfig.TeleportMethod;
import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
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
        var withoutMapMods = ClientConfigSettings.forScreen(Set.of());
        assertEquals(Stream.concat(ClientConfigSettings.RENDERING.stream(), ClientConfigSettings.WAYPOINT_MANAGER.stream())
                .toList(), withoutMapMods);

        List<ClientConfigSettings.Setting> withXaero = ClientConfigSettings.forScreen(Set.of(UploadTarget.XAERO));

        assertEquals(withoutMapMods.size() + 2, withXaero.size());
        assertEquals("server_waypoint.map_mod.xaeros_minimap",
                withXaero.get(withoutMapMods.size()).text().argumentKey());
    }

    @Test
    void waypointManagerTeleportSettingsPersistAndResetIndependently() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        var method = ClientConfigSettings.TELEPORT_METHOD;
        var close = ClientConfigSettings.CLOSE_AFTER_TELEPORT;

        assertEquals(TeleportMethod.KEY, config.getTeleportMethod());
        assertTrue(config.isCloseWaypointManagerAfterTeleport());
        assertTranslation(method.defaultText(), "server_waypoint.config.teleport_method.key");
        assertTranslation(close.defaultText(), "server_waypoint.config.on");

        close.set(config, false);
        for (TeleportMethod choice : TeleportMethod.values()) {
            method.set(config, choice);
            ClientConfig saved = GSON.fromJson(GSON.toJson(config), ClientConfig.class);
            assertEquals(choice, saved.getTeleportMethod());
            assertFalse(saved.isCloseWaypointManagerAfterTeleport());
        }
        ClientConfig restored = GSON.fromJson(GSON.toJson(config), ClientConfig.class);

        method.reset(restored);
        assertEquals(TeleportMethod.KEY, restored.getTeleportMethod());
        assertFalse(restored.isCloseWaypointManagerAfterTeleport());
        close.reset(restored);
        assertTrue(restored.isCloseWaypointManagerAfterTeleport());
        assertTrue(ClientConfigSettings.allDefault(restored, ClientConfigSettings.WAYPOINT_MANAGER));
    }

    @Test
    void resettingTheTeleportKeyPreservesTheMethod() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        var setting = ClientConfigSettings.TELEPORT_KEY;
        assertTrue(ClientConfigSettings.WAYPOINT_MANAGER.contains(setting));
        config.setTeleportMethod(TeleportMethod.DOUBLE_CLICK);
        config.setTeleportKey(InputConstants.getKey("key.keyboard.escape"));
        assertFalse(setting.isDefault(config));

        setting.reset(config);

        assertTrue(setting.isDefault(config));
        assertEquals(InputConstants.KEY_T, config.getTeleportKey().getValue());
        assertEquals(TeleportMethod.DOUBLE_CLICK, config.getTeleportMethod());
    }

    @Test
    void teleportDropdownOmitsTheCurrentMethodAndReflectsReset() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        var dropdown = new ClientConfigScreen.TeleportMethodDropdown(config, new TestFont(), () -> { });
        int left = InputConstants.MOUSE_BUTTON_LEFT;
        int rowHeight = dropdown.getHeight();
        assertTranslation(dropdown.triggerLabel(), "server_waypoint.config.teleport_method.key");

        var choices = List.of(TeleportMethod.DOUBLE_CLICK, TeleportMethod.RIGHT_CLICK,
                TeleportMethod.MIDDLE_CLICK, TeleportMethod.KEY, TeleportMethod.MIDDLE_CLICK);
        var keys = List.of("double_click", "right_click", "middle_click", "key", "middle_click");
        int[] popupRows = {1, 2, 3, 1, 3};
        for (int index = 0; index < choices.size(); index++) {
            assertTrue(dropdown.mouseClicked(2, 2, left));
            assertEquals(3, dropdown.getPopupItemCount());
            assertTrue(dropdown.mouseClicked(2, popupRows[index] * rowHeight + 2, left));
            assertFalse(dropdown.isExpanded());
            assertEquals(choices.get(index), config.getTeleportMethod());
            assertTranslation(dropdown.triggerLabel(), "server_waypoint.config.teleport_method." + keys.get(index));
        }

        ClientConfigSettings.TELEPORT_METHOD.reset(config);
        assertTranslation(dropdown.triggerLabel(), "server_waypoint.config.teleport_method.key");
        assertTrue(dropdown.mouseClicked(2, 2, left));
        assertTrue(dropdown.mouseClicked(2, rowHeight + 2, left));
        assertEquals(TeleportMethod.DOUBLE_CLICK, config.getTeleportMethod());
    }

    @Test
    void teleportDropdownKeyboardNavigationSkipsTheCurrentMethod() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        config.setTeleportMethod(TeleportMethod.RIGHT_CLICK);
        var dropdown = new ClientConfigScreen.TeleportMethodDropdown(config, new TestFont(), () -> { });

        assertTrue(dropdown.keyPressed(InputConstants.KEY_RETURN, 0, 0));
        assertEquals(TeleportMethod.KEY.ordinal(), dropdown.getHighlightedItemIndex());
        assertTrue(dropdown.keyPressed(InputConstants.KEY_DOWN, 0, 0));
        assertTrue(dropdown.keyPressed(InputConstants.KEY_DOWN, 0, 0));
        assertEquals(TeleportMethod.MIDDLE_CLICK.ordinal(), dropdown.getHighlightedItemIndex());
        assertTrue(dropdown.keyPressed(InputConstants.KEY_RETURN, 0, 0));
        assertEquals(TeleportMethod.MIDDLE_CLICK, config.getTeleportMethod());
    }

    @Test
    void teleportDropdownOpensAboveTheControlNearTheBottomOfTheScreen() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        var dropdown = new ClientConfigScreen.TeleportMethodDropdown(config, new TestFont(), () -> { });
        int rowHeight = dropdown.getHeight();
        int y = 100 - rowHeight;
        int left = InputConstants.MOUSE_BUTTON_LEFT;
        dropdown.setY(y);
        dropdown.layoutPopup(100);

        assertTrue(dropdown.mouseClicked(2, y + 2, left));
        assertTrue(dropdown.isMouseOverPopup(2, y - 3 * rowHeight + 2));
        assertFalse(dropdown.isMouseOverPopup(2, y - 4 * rowHeight + 2));
        assertTrue(dropdown.mouseClicked(2, y - rowHeight + 2, left));
        assertEquals(TeleportMethod.MIDDLE_CLICK, config.getTeleportMethod());
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
