package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.access.PlayerNavigationMapIdAccessor;
import _959.server_waypoint.common.util.TextHelper;
import _959.server_waypoint.mixin.MapItemSavedDataAccessor;
import _959.server_waypoint.navigation.NavigationDisplayText;
import _959.server_waypoint.navigation.NavigationTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

//? if >= 1.20.5 {
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
//?} else {
/*import net.minecraft.world.level.saveddata.maps.MapDecoration;
*///?}

/**
 * Gives each player one persistent navigation map ID and rewrites that map's
 * data when the player changes targets.
 */
final class ModNavigationMapCache {
    private static final byte NAVIGATION_SCALE = 2;
    private static final String TARGET_MARKER_ID = "server_waypoint:navigation_target";

    PreparedMap prepare(
            ServerPlayer player,
            ServerLevel targetLevel,
            NavigationTarget target
    ) {
        PlayerNavigationMapIdAccessor mapIdData = (PlayerNavigationMapIdAccessor) player;
        int mapId = mapIdData.sw$getNavigationMapId();
        ItemStack map;
        if (mapId < 0) {
            map = createMap(targetLevel, target);
            mapId = mapId(map);
            mapIdData.sw$setNavigationMapId(mapId);
        } else {
            replaceMapData(targetLevel, target, mapId);
            map = mapStack(targetLevel, mapId, target);
        }
        ModNavigationItemText.apply(map, target);
        return new PreparedMap(map);
    }

    private static ItemStack createMap(ServerLevel targetLevel, NavigationTarget target) {
        ItemStack map = MapItem.create(
                targetLevel,
                target.position().x(),
                target.position().z(),
                NAVIGATION_SCALE,
                true,
                false
        );
        addMarker(targetLevel, map, target);
        return map;
    }

    private static void replaceMapData(ServerLevel targetLevel, NavigationTarget target, int mapId) {
        MapItemSavedData savedData = MapItemSavedData.createFresh(
                target.position().x(),
                target.position().z(),
                NAVIGATION_SCALE,
                true,
                false,
                targetLevel.dimension()
        );
        addNamedMarker(savedData, targetLevel, target);
        //? if >= 1.20.5 {
        targetLevel.setMapData(new MapId(mapId), savedData);
        //?} else {
        /*targetLevel.setMapData(MapItem.makeKey(mapId), savedData);
        *///?}
    }

    private static ItemStack mapStack(
            ServerLevel targetLevel,
            int mapId,
            NavigationTarget target
    ) {
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        //? if >= 1.20.5 {
        map.set(net.minecraft.core.component.DataComponents.MAP_ID, new MapId(mapId));
        //?} else {
        /*map.getOrCreateTag().putInt("map", mapId);
        *///?}
        addMarker(targetLevel, map, target);
        return map;
    }

    private static void addMarker(
            ServerLevel targetLevel,
            ItemStack map,
            NavigationTarget target
    ) {
        BlockPos position = new BlockPos(
                target.position().x(),
                target.position().y(),
                target.position().z()
        );
        //? if >= 1.20.5 {
        MapItemSavedData.addTargetDecoration(map, position, TARGET_MARKER_ID, MapDecorationTypes.TARGET_X);
        //?} else {
        /*MapItemSavedData.addTargetDecoration(map, position, TARGET_MARKER_ID, MapDecoration.Type.TARGET_X);
        *///?}

        MapItemSavedData mapData = MapItem.getSavedData(map, targetLevel);
        if (mapData != null) {
            addNamedMarker(mapData, targetLevel, target);
        }
    }

    private static void addNamedMarker(
            MapItemSavedData mapData,
            LevelAccessor level,
            NavigationTarget target
    ) {
        BlockPos position = new BlockPos(
                target.position().x(),
                target.position().y(),
                target.position().z()
        );
        //? if >= 1.20.5 {
        ((MapItemSavedDataAccessor) mapData).serverWaypoint$addDecoration(
                MapDecorationTypes.TARGET_X,
                level,
                TARGET_MARKER_ID,
                position.getX(),
                position.getZ(),
                180.0D,
                TextHelper.toMinecraft(NavigationDisplayText.buildItemName(target))
        );
        //?} else {
        /*((MapItemSavedDataAccessor) mapData).serverWaypoint$addDecoration(
                MapDecoration.Type.TARGET_X,
                level,
                TARGET_MARKER_ID,
                position.getX(),
                position.getZ(),
                180.0D,
                TextHelper.toMinecraft(NavigationDisplayText.buildItemName(target))
        );
        *///?}
    }

    private static int mapId(ItemStack map) {
        //? if >= 1.20.5 {
        MapId id = map.get(net.minecraft.core.component.DataComponents.MAP_ID);
        if (id == null) {
            throw new IllegalStateException("Generated navigation map has no map ID");
        }
        return id.id();
        //?} else {
        /*return map.getOrCreateTag().getInt("map");
        *///?}
    }

    static final class PreparedMap {
        private final ItemStack item;

        private PreparedMap(ItemStack item) {
            this.item = item;
        }

        ItemStack item() {
            return this.item;
        }
    }
}
