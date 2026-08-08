package _959.server_waypoint.common.util;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.path.XaeroPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;

public class XaeroMinimapHelper {
    public static MinimapSession getMinimapSession() {
        return BuiltInHudModules.MINIMAP.getCurrentSession();
    }
    
    public static String getMinimapWorldNode(MinimapSession session, ResourceKey<Level> dimKey) {
        return session.getWorldStateUpdater().getPotentialWorldNode(dimKey, false);
    }

    public static MinimapWorld getMinimapWorld(MinimapSession session, ResourceKey<Level> dimKey) {
        MinimapWorldManager manager = session.getWorldManager();
        // Xaero can change the automatic world path after receiving a server level
        // id or after an Auto connection. For the current dimension, its active
        // automatic world is authoritative; rebuilding the path can create a
        // second, unconnected sub-world and duplicate waypoint sets.
        if (Minecraft.getInstance().level != null && dimKey.equals(Minecraft.getInstance().level.dimension())) {
            MinimapWorld autoWorld = manager.getAutoWorld();
            if (autoWorld != null) {
                return autoWorld;
            }
        }
        String dimId = session.getDimensionHelper().getDimensionDirectoryName(dimKey);
        XaeroPath root = manager.getAutoRootContainer().getPath();
        String node = getMinimapWorldNode(session, dimKey);
        XaeroPath fullPath = root.resolve(dimId).resolve(node);
        return manager.getWorld(fullPath);
    }

    public static void saveAllWorlds(MinimapSession session) {
        try {
            session.getWorldManagerIO().saveAllWorlds(session);
        } catch (IOException e) {
            WaypointClientMod.LOGGER.error("Xaero's Minimap mod failed to save all worlds", e);
            throw new RuntimeException(e);
        }
    }

    public static void saveMinimapWorld(MinimapSession session, MinimapWorld minimapWorld) throws IOException {
        session.getWorldManagerIO().saveWorld(minimapWorld);
    }

    public static void saveMinimapWorld(MinimapSession session, ResourceKey<Level> dimKey) throws IOException {
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        saveMinimapWorld(session, minimapWorld);
    }

    public static void replaceWaypoint(WaypointSet waypointSet, Waypoint waypoint) {
        String name = waypoint.getName();
        removeWaypointsByName(waypointSet, name);
        waypointSet.add(waypoint);
    }

    public static void replaceSyncedWaypoint(WaypointSet waypointSet, String listName, SimpleWaypoint simpleWaypoint) {
        String syncedName = SyncedWaypointName.formatSyncedName(simpleWaypoint.name());
        if (syncedName == null) {
            WaypointClientMod.LOGGER.warn("Skipping Xaero's Minimap sync for waypoint {} in list {} because its generated name would be ambiguous.", simpleWaypoint.name(), listName);
            return;
        }
        removeSyncedWaypoint(waypointSet, simpleWaypoint.name());
        removeDuplicateWaypoints(waypointSet);
        waypointSet.add(XaerosWaypointHelper.simpleWaypointToXaerosWaypoint(simpleWaypoint, syncedName));
    }

    public static void replaceWaypointList(MinimapWorld minimapWorld, WaypointList waypointList) {
        replaceWaypointList(
                minimapWorld,
                waypointList,
                XaerosWaypointHelper::simpleWaypointToXaerosWaypoint
        );
    }

    static void replaceWaypointList(MinimapWorld minimapWorld, WaypointList waypointList,
                                    BiFunction<SimpleWaypoint, String, Waypoint> waypointFactory) {
        WaypointSet waypointSet = getOrCreateSyncedWaypointSet(minimapWorld, waypointList.name());
        if (waypointSet == null) {
            return;
        }
        syncWaypointSetByWaypoints(waypointSet, waypointList, waypointFactory);
        removeMatchingLocalWaypoints(minimapWorld, waypointList);
    }

    private static WaypointSet getOrCreateSyncedWaypointSet(MinimapWorld minimapWorld, String listName) {
        String syncedListName = SyncedWaypointName.formatSyncedName(listName);
        if (syncedListName == null) {
            WaypointClientMod.LOGGER.warn("Skipping Xaero's Minimap sync for list {} because its generated name would be ambiguous.", listName);
            return null;
        }
        WaypointSet waypointSet = minimapWorld.getWaypointSet(syncedListName);
        if (waypointSet == null) {
            waypointSet = WaypointSet.Builder.begin().setName(syncedListName).build();
            minimapWorld.addWaypointSet(waypointSet);
        }
        return waypointSet;
    }

    private static void syncWaypointSetByWaypoints(WaypointSet waypointSet, WaypointList waypointList) {
        syncWaypointSetByWaypoints(waypointSet, waypointList,
                XaerosWaypointHelper::simpleWaypointToXaerosWaypoint);
    }

    private static void syncWaypointSetByWaypoints(WaypointSet waypointSet, WaypointList waypointList,
                                                   BiFunction<SimpleWaypoint, String, Waypoint> waypointFactory) {
        removeSyncedWaypoints(waypointSet);
        removeDuplicateWaypoints(waypointSet);
        addUniqueSyncedWaypoints(waypointSet, waypointList, waypointFactory);
        removeMatchingUnmanagedWaypoints(waypointSet, waypointList);
    }

    public static void replaceWaypointLists(MinimapWorld minimapWorld, List<WaypointList> waypointLists) {
        Map<String, WaypointList> waypointListsBySyncedName = new LinkedHashMap<>();
        for (WaypointList waypointList : waypointLists) {
            String syncedListName = SyncedWaypointName.formatSyncedName(waypointList.name());
            if (syncedListName == null) {
                WaypointClientMod.LOGGER.warn("Skipping Xaero's Minimap sync for list {} because its generated name would be ambiguous.", waypointList.name());
                continue;
            }
            waypointListsBySyncedName.putIfAbsent(syncedListName, waypointList);
        }

        Set<String> syncedExistingListNames = new HashSet<>();
        for (WaypointSet waypointSet : getSyncedWaypointSets(minimapWorld)) {
            WaypointList waypointList = waypointListsBySyncedName.get(waypointSet.getName());
            if (waypointList == null) {
                removeSyncedWaypointSet(minimapWorld, waypointSet.getName());
                continue;
            }
            syncWaypointSetByWaypoints(waypointSet, waypointList);
            removeMatchingLocalWaypoints(minimapWorld, waypointList);
            syncedExistingListNames.add(waypointSet.getName());
        }

        for (Map.Entry<String, WaypointList> entry : waypointListsBySyncedName.entrySet()) {
            if (!syncedExistingListNames.contains(entry.getKey())) {
                WaypointSet waypointSet = WaypointSet.Builder.begin().setName(entry.getKey()).build();
                minimapWorld.addWaypointSet(waypointSet);
                syncWaypointSetByWaypoints(waypointSet, entry.getValue());
                removeMatchingLocalWaypoints(minimapWorld, entry.getValue());
            }
        }
    }

    public static void removeMatchingLocalWaypoint(
            MinimapWorld minimapWorld,
            String listName,
            SimpleWaypoint waypoint
    ) {
        removeMatchingLocalWaypoints(
                minimapWorld,
                new WaypointList(listName, 0, List.of(waypoint))
        );
    }

    private static void removeMatchingLocalWaypoints(MinimapWorld minimapWorld, WaypointList waypointList) {
        WaypointSet localWaypointSet = minimapWorld.getWaypointSet(waypointList.name());
        if (localWaypointSet == null || SyncedWaypointName.parseSyncedName(localWaypointSet.getName()) != null) {
            return;
        }

        removeMatchingUnmanagedWaypoints(localWaypointSet, waypointList);

        if (localWaypointSet.isEmpty()) {
            removeWaypointSet(minimapWorld, localWaypointSet.getName());
        }
    }

    private static void removeMatchingUnmanagedWaypoints(WaypointSet waypointSet, WaypointList waypointList) {
        Iterator<Waypoint> iterator = waypointSet.getWaypoints().iterator();
        while (iterator.hasNext()) {
            Waypoint localWaypoint = iterator.next();
            if (localWaypoint.getPurpose() != xaero.hud.minimap.waypoint.WaypointPurpose.NORMAL
                    || localWaypoint.isTemporary()
                    || localWaypoint.isDisabled()) {
                continue;
            }
            SimpleWaypoint syncedWaypoint = waypointList.getWaypointByName(localWaypoint.getName());
            if (syncedWaypoint != null && representsSameWaypoint(syncedWaypoint, localWaypoint)) {
                iterator.remove();
            }
        }
    }

    private static boolean representsSameWaypoint(SimpleWaypoint expected, Waypoint actualWaypoint) {
        SimpleWaypoint actual = XaerosWaypointHelper.xaerosWaypointToSimpleWaypoint(actualWaypoint);
        return expected.name().equals(actual.name())
                && Objects.equals(expected.initials(), actual.initials())
                && expected.pos().equals(actual.pos())
                && expected.rgb() == actual.rgb()
                && expected.yaw() == actual.yaw()
                && expected.global() == actual.global();
    }

    public static void removeSyncedWaypointSet(MinimapWorld minimapWorld, String waypointSetName) {
        WaypointSet waypointSet = minimapWorld.getWaypointSet(waypointSetName);
        if (waypointSet == null) {
            removeWaypointSet(minimapWorld, waypointSetName);
            return;
        }
        removeSyncedWaypoints(waypointSet);
        if (waypointSet.isEmpty()) {
            removeWaypointSet(minimapWorld, waypointSetName);
        }
    }

    private static void removeWaypointSet(MinimapWorld minimapWorld, String waypointSetName) {
        minimapWorld.removeWaypointSet(waypointSetName);
        if (minimapWorld.getCurrentWaypointSet() != null) {
            return;
        }
        // Xaero does not update the current set ID when the referenced set is removed.
        for (WaypointSet waypointSet : minimapWorld.getIterableWaypointSets()) {
            minimapWorld.setCurrentWaypointSetId(waypointSet.getName());
            return;
        }
    }

    public static void addOrReplaceWaypointLists(MinimapSession session, ResourceKey<Level> dimKey, List<WaypointList> waypointLists) {
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        replaceWaypointLists(minimapWorld, waypointLists);
    }

    public static void addDimensionWaypoint(MinimapSession session, DimensionWaypointBuffer dimensionWaypointBuffer) {
        MinimapWorld minimapWorld = getMinimapWorld(session, getDimensionKey(dimensionWaypointBuffer.dimensionName()));
        replaceWaypointLists(minimapWorld, dimensionWaypointBuffer.waypointLists());
    }

    public static void removeWaypointsByName(WaypointSet waypointSet, String name) {
        Iterator<Waypoint> iter =  waypointSet.getWaypoints().iterator();
        while (iter.hasNext()) {
            Waypoint waypoint = iter.next();
            if (name.equals(waypoint.getName())) {
                iter.remove();
//                ServerWaypointClientMod.LOGGER.info("Waypoint {} has been removed.", name);
            }
        }
    }

    public static void removeSyncedWaypoint(WaypointSet waypointSet, String waypointName) {
        String syncedName = SyncedWaypointName.formatSyncedName(waypointName);
        if (syncedName == null) {
            return;
        }
        removeWaypointsByName(waypointSet, syncedName);
    }

    private static List<WaypointSet> getSyncedWaypointSets(MinimapWorld minimapWorld) {
        List<WaypointSet> syncedWaypointSets = new ArrayList<>();
        for (WaypointSet waypointSet : minimapWorld.getIterableWaypointSets()) {
            if (SyncedWaypointName.parseSyncedName(waypointSet.getName()) != null) {
                syncedWaypointSets.add(waypointSet);
            }
        }
        return syncedWaypointSets;
    }

    private static void removeSyncedWaypoints(WaypointSet waypointSet) {
        Iterator<Waypoint> iter =  waypointSet.getWaypoints().iterator();
        while (iter.hasNext()) {
            Waypoint waypoint = iter.next();
            if (SyncedWaypointName.parseSyncedName(waypoint.getName()) != null) {
                iter.remove();
            }
        }
    }

    private static void removeDuplicateWaypoints(WaypointSet waypointSet) {
        Set<String> seenNames = new HashSet<>();
        Iterator<Waypoint> iter =  waypointSet.getWaypoints().iterator();
        while (iter.hasNext()) {
            Waypoint waypoint = iter.next();
            if (!seenNames.add(waypoint.getName())) {
                iter.remove();
            }
        }
    }

    private static void addUniqueSyncedWaypoints(WaypointSet waypointSet, WaypointList waypointList,
                                                 BiFunction<SimpleWaypoint, String, Waypoint> waypointFactory) {
        Set<String> addedNames = new HashSet<>();
        for (SimpleWaypoint simpleWaypoint : waypointList.simpleWaypoints()) {
            String syncedName = SyncedWaypointName.formatSyncedName(simpleWaypoint.name());
            if (syncedName == null || !addedNames.add(syncedName)) {
                if (syncedName == null) {
                    WaypointClientMod.LOGGER.warn("Skipping Xaero's Minimap sync for waypoint {} in list {} because its generated name would be ambiguous.", simpleWaypoint.name(), waypointList.name());
                }
                continue;
            }
            waypointSet.add(waypointFactory.apply(simpleWaypoint, syncedName));
        }
    }
}
