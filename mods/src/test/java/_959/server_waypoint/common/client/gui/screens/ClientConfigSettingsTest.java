package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.ClientConfig.SyncNotificationMode;
import _959.server_waypoint.common.client.ClientConfig.TeleportMethod;
import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.widgets.SettingsListWidget;
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
        assertEquals(Stream.of(ClientConfigSettings.RENDERING, ClientConfigSettings.WAYPOINT_MANAGER,
                        List.of(ClientConfigSettings.SYNC_NOTIFICATIONS)).flatMap(List::stream)
                .toList(), withoutMapMods);

        List<ClientConfigSettings.Setting> withXaero = ClientConfigSettings.forScreen(Set.of(UploadTarget.XAERO));

        assertEquals(withoutMapMods.size() + 2, withXaero.size());
        assertEquals("server_waypoint.map_mod.xaeros_minimap",
                withXaero.get(withoutMapMods.size()).text().argumentKey());
    }

    @Test
    void waypointUpdateNotificationDropdownPersistsIndependentlyAndResetsToOff() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        var setting = ClientConfigSettings.WAYPOINT_UPDATE_NOTIFICATIONS;
        var settings = ClientConfigSettings.forScreen(Set.of());
        var dropdown = new ClientConfigScreen.SettingDropdown<>(setting, config, new TestFont(), () -> { });
        String prefix = "server_waypoint.config.waypoint_update_notifications.";
        assertTrue(settings.contains(setting));
        assertEquals(SyncNotificationMode.OFF, config.getWaypointUpdateNotificationMode());
        assertEquals(SyncNotificationMode.CHAT, config.getSyncNotificationMode());
        assertTranslation(dropdown.triggerLabel(), prefix + "off");

        config.setSyncNotificationMode(SyncNotificationMode.TOAST);
        var choices = List.of(SyncNotificationMode.CHAT, SyncNotificationMode.TOAST,
                SyncNotificationMode.OFF, SyncNotificationMode.TOAST);
        var keys = List.of("chat", "toast", "off", "toast");
        int[] popupRows = {1, 2, 1, 2};
        for (int index = 0; index < choices.size(); index++) {
            int left = InputConstants.MOUSE_BUTTON_LEFT;
            assertTrue(dropdown.mouseClicked(2, 2, left));
            assertTrue(dropdown.mouseClicked(2, popupRows[index] * dropdown.getHeight() + 2, left));
            assertTranslation(dropdown.triggerLabel(), prefix + keys.get(index));
            ClientConfig restored = GSON.fromJson(GSON.toJson(config), ClientConfig.class);
            assertEquals(choices.get(index), restored.getWaypointUpdateNotificationMode());
            assertEquals(SyncNotificationMode.TOAST, restored.getSyncNotificationMode());
        }

        ClientConfigSettings.resetAll(config, ClientConfigSettings.WAYPOINT_MANAGER);
        assertEquals(SyncNotificationMode.OFF, config.getWaypointUpdateNotificationMode());
        assertEquals(SyncNotificationMode.OFF,
                GSON.fromJson(GSON.toJson(config), ClientConfig.class).getWaypointUpdateNotificationMode());
        assertTranslation(dropdown.triggerLabel(), prefix + "off");
    }

    @Test
    void syncNotificationDropdownSelectsAllModesAndReflectsReset() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        var setting = ClientConfigSettings.SYNC_NOTIFICATIONS;
        var dropdown = new ClientConfigScreen.SettingDropdown<>(setting, config, new TestFont(), () -> { });
        String prefix = "server_waypoint.config.sync_notifications.";
        int left = InputConstants.MOUSE_BUTTON_LEFT;
        int rowHeight = dropdown.getHeight();
        assertTranslation(dropdown.triggerLabel(), prefix + "chat");

        var choices = List.of(SyncNotificationMode.TOAST, SyncNotificationMode.OFF, SyncNotificationMode.CHAT);
        var keys = List.of("toast", "off", "chat");
        int[] popupRows = {2, 1, 1};
        for (int index = 0; index < choices.size(); index++) {
            assertTrue(dropdown.mouseClicked(2, 2, left));
            assertEquals(2, dropdown.getPopupItemCount());
            assertTrue(dropdown.mouseClicked(2, popupRows[index] * rowHeight + 2, left));
            assertFalse(dropdown.isExpanded());
            assertTranslation(dropdown.triggerLabel(), prefix + keys.get(index));
            ClientConfig restored = GSON.fromJson(GSON.toJson(config), ClientConfig.class);
            assertEquals(choices.get(index), restored.getSyncNotificationMode());
        }

        assertTrue(dropdown.keyPressed(InputConstants.KEY_RETURN, 0, 0));
        assertEquals(SyncNotificationMode.OFF.ordinal(), dropdown.getHighlightedItemIndex());
        assertTrue(dropdown.keyPressed(InputConstants.KEY_RETURN, 0, 0));
        assertEquals(SyncNotificationMode.OFF, config.getSyncNotificationMode());
        var screenSettings = ClientConfigSettings.forScreen(Set.of());
        assertFalse(ClientConfigSettings.allDefault(config, screenSettings));
        setting.reset(config);
        assertTrue(ClientConfigSettings.allDefault(config, screenSettings));
        assertTranslation(dropdown.triggerLabel(), prefix + "chat");
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
    void teleportDropdownAndPopupChoicesUseTheSettingsRowScale() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        var setting = ClientConfigSettings.TELEPORT_METHOD;
        var font = new TestFont();
        var dropdown = new ClientConfigScreen.SettingDropdown<>(setting, config, font, () -> { });
        int widestLabel = setting.choices().stream().map(setting::valueText).mapToInt(font::width).max().orElseThrow();
        int width = TextChoiceDropdown.triggerWidth((int) Math.ceil(widestLabel * SettingsListWidget.ROW_TEXT_SCALE));

        assertEquals(width, dropdown.getWidth());
        assertEquals(9, dropdown.getHeight());
        for (var choice : dropdown.getMenuItems()) {
            assertEquals(width, choice.getWidth());
            assertEquals(9, choice.getHeight());
        }
    }

    @Test
    void teleportDropdownOmitsTheCurrentMethodAndReflectsReset() {
        ClientConfig config = GSON.fromJson("{}", ClientConfig.class);
        var dropdown = new ClientConfigScreen.SettingDropdown<>(ClientConfigSettings.TELEPORT_METHOD, config, new TestFont(), () -> { });
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
        var dropdown = new ClientConfigScreen.SettingDropdown<>(ClientConfigSettings.TELEPORT_METHOD, config, new TestFont(), () -> { });

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
        var dropdown = new ClientConfigScreen.SettingDropdown<>(ClientConfigSettings.TELEPORT_METHOD, config, new TestFont(), () -> { });
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
