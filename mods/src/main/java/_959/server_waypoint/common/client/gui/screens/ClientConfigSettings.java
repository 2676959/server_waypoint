package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.core.network.upload.UploadTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The client settings shown on {@link ClientConfigScreen}: their text, ranges and defaults, and how
 * each reads and writes {@link ClientConfig}. The setters apply changes to the renderer immediately.
 */
final class ClientConfigSettings {
    private static final String KEY_PREFIX = "server_waypoint.config.";

    static final BooleanSetting SHOW_WAYPOINTS = new BooleanSetting(
            SettingText.of("enable_waypoint_render"),
            ClientConfig.DEFAULT_ENABLE_WAYPOINT_RENDER,
            ClientConfig::isEnableWaypointRender,
            ClientConfig::setEnableWaypointRender
    );
    static final IntSetting SCALE = new IntSetting(
            SettingText.of("waypoint_scale_factor"),
            0,
            500,
            ClientConfig.DEFAULT_WAYPOINT_SCALING_FACTOR,
            ValueFormat.PERCENT,
            ClientConfig::getWaypointScalingFactor,
            ClientConfig::setWaypointScalingFactor
    );
    static final BooleanSetting RENDER_UNDER_F1 = new BooleanSetting(
            SettingText.of("render_waypoints_under_f1"),
            ClientConfig.DEFAULT_RENDER_WAYPOINTS_UNDER_F1,
            ClientConfig::isRenderWaypointsUnderF1,
            ClientConfig::setRenderWaypointsUnderF1
    );
    static final IntSetting VERTICAL_OFFSET = new IntSetting(
            SettingText.of("waypoint_vertical_offset"),
            -100,
            100,
            ClientConfig.DEFAULT_WAYPOINT_VERTICAL_OFFSET,
            ValueFormat.PERCENT,
            ClientConfig::getWaypointVerticalOffset,
            ClientConfig::setWaypointVerticalOffset
    );
    static final IntSetting BACKGROUND_OPACITY = new IntSetting(
            SettingText.of("waypoint_bg_opacity"),
            0,
            255,
            ClientConfig.DEFAULT_WAYPOINT_BACKGROUND_ALPHA,
            ValueFormat.PLAIN,
            ClientConfig::getWaypointBackgroundAlpha,
            ClientConfig::setWaypointBackgroundAlpha
    );
    static final IntSetting LOCAL_WAYPOINT_RANGE = new IntSetting(
            SettingText.of("local_waypoint_view_distance"),
            0,
            1024,
            ClientConfig.DEFAULT_VIEW_DISTANCE,
            ValueFormat.CHUNKS,
            ClientConfig::getViewDistance,
            ClientConfig::setViewDistance
    );
    static final BooleanSetting XAERO_DEFAULT_LIST_DIRECT_SYNC = new BooleanSetting(
            SettingText.of("xaero_default_list_direct_sync"),
            ClientConfig.DEFAULT_XAERO_DEFAULT_LIST_DIRECT_SYNC,
            ClientConfig::isXaeroDefaultListDirectSync,
            ClientConfig::setXaeroDefaultListDirectSync
    );
    /** The Waypoint rendering section, in screen order. */
    static final List<Setting> RENDERING = List.of(
            SHOW_WAYPOINTS, RENDER_UNDER_F1, SCALE, VERTICAL_OFFSET, BACKGROUND_OPACITY, LOCAL_WAYPOINT_RANGE);
    /** The map mods the Map mods section lists, in screen order. */
    static final List<UploadTarget> MAP_MODS = List.of(UploadTarget.XAERO, UploadTarget.VOXELMAP);

    private ClientConfigSettings() {
    }

    static BooleanSetting autoSync(UploadTarget target) {
        SettingText text = SettingText.forMapMod("map_mod.auto_sync", target);
        return switch (target) {
            case XAERO -> new BooleanSetting(text, ClientConfig.DEFAULT_AUTO_SYNC_TO_XAEROS_MINIMAP,
                    ClientConfig::isAutoSyncToXaerosMinimap, ClientConfig::setAutoSyncToXaerosMinimap);
            case VOXELMAP -> new BooleanSetting(text, ClientConfig.DEFAULT_AUTO_SYNC_TO_VOXELMAP,
                    ClientConfig::isAutoSyncToVoxelMap, ClientConfig::setAutoSyncToVoxelMap);
        };
    }

    static String mapModNameKey(UploadTarget target) {
        return switch (target) {
            case XAERO -> "server_waypoint.map_mod.xaeros_minimap";
            case VOXELMAP -> "server_waypoint.map_mod.voxelmap";
        };
    }

    /** Rendering settings, then the settings for each installed map mod in {@link #MAP_MODS} order. */
    static List<Setting> forScreen(Set<UploadTarget> installedMapMods) {
        List<Setting> settings = new ArrayList<>(RENDERING);
        for (UploadTarget target : MAP_MODS) {
            if (installedMapMods.contains(target)) {
                settings.add(autoSync(target));
                if (target == UploadTarget.XAERO) {
                    settings.add(XAERO_DEFAULT_LIST_DIRECT_SYNC);
                }
            }
        }
        return List.copyOf(settings);
    }

    static void resetAll(ClientConfig config, List<Setting> settings) {
        for (Setting setting : settings) {
            setting.reset(config);
        }
    }

    static boolean allDefault(ClientConfig config, List<Setting> settings) {
        for (Setting setting : settings) {
            if (!setting.isDefault(config)) {
                return false;
            }
        }
        return true;
    }

    static Component onOff(boolean value) {
        return Component.translatable(KEY_PREFIX + (value ? "on" : "off"));
    }

    /** A setting shown as one row. */
    sealed interface Setting permits IntSetting, BooleanSetting {
        SettingText text();

        /** The default as the row shows it, such as "100%", "12 chunks" or "On". */
        Component defaultText();

        boolean isDefault(ClientConfig config);

        /** Restores the default through the setter, so the change applies immediately. */
        void reset(ClientConfig config);
    }

    record IntSetting(SettingText text, int min, int max, int defaultValue, ValueFormat format,
                      ToIntFunction<ClientConfig> getter, ObjIntConsumer<ClientConfig> setter) implements Setting {
        int get(ClientConfig config) {
            return this.getter.applyAsInt(config);
        }

        void set(ClientConfig config, int value) {
            this.setter.accept(config, value);
        }

        @Override
        public Component defaultText() {
            return this.format.value(this.defaultValue);
        }

        @Override
        public boolean isDefault(ClientConfig config) {
            return this.get(config) == this.defaultValue;
        }

        @Override
        public void reset(ClientConfig config) {
            this.set(config, this.defaultValue);
        }
    }

    record BooleanSetting(SettingText text, boolean defaultValue, Predicate<ClientConfig> getter,
                          BiConsumer<ClientConfig, Boolean> setter) implements Setting {
        boolean get(ClientConfig config) {
            return this.getter.test(config);
        }

        void set(ClientConfig config, boolean value) {
            this.setter.accept(config, value);
        }

        @Override
        public Component defaultText() {
            return onOff(this.defaultValue);
        }

        @Override
        public boolean isDefault(ClientConfig config) {
            return this.get(config) == this.defaultValue;
        }

        @Override
        public void reset(ClientConfig config) {
            this.set(config, this.defaultValue);
        }
    }

    /** Translation keys for a setting. {@code argumentKey} names the map mod in per-mod text. */
    record SettingText(String labelKey, String descriptionKey, @Nullable String argumentKey) {
        static SettingText of(String key) {
            return new SettingText(KEY_PREFIX + key, KEY_PREFIX + key + ".tooltip", null);
        }

        static SettingText forMapMod(String key, UploadTarget target) {
            return new SettingText(KEY_PREFIX + key, KEY_PREFIX + key + ".tooltip", mapModNameKey(target));
        }

        Component label() {
            return this.translate(this.labelKey);
        }

        Component description() {
            return this.translate(this.descriptionKey);
        }

        private Component translate(String key) {
            return this.argumentKey == null
                    ? Component.translatable(key)
                    : Component.translatable(key, Component.translatable(this.argumentKey));
        }
    }

    /** How an integer setting shows its unit and its default. */
    enum ValueFormat {
        PLAIN(null, null),
        PERCENT(KEY_PREFIX + "unit.percent", KEY_PREFIX + "value.percent"),
        CHUNKS(KEY_PREFIX + "unit.chunks", KEY_PREFIX + "value.chunks");

        private final @Nullable String unitKey;
        private final @Nullable String valueKey;

        ValueFormat(@Nullable String unitKey, @Nullable String valueKey) {
            this.unitKey = unitKey;
            this.valueKey = valueKey;
        }

        /** The unit shown after the control, or null for a plain number. */
        @Nullable Component unit() {
            return this.unitKey == null ? null : Component.translatable(this.unitKey);
        }

        Component value(int value) {
            return this.valueKey == null
                    ? Component.literal(Integer.toString(value))
                    : Component.translatable(this.valueKey, value);
        }
    }
}
