package _959.server_waypoint.common.util;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
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
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.util.DimensionKeyParser.getDimensionKey;

public class XaeroMinimapHelper {
    public static final String DEFAULT_WAYPOINT_SET = "gui.xaero_default";

    public static String getSyncedWaypointSetName(String listName) {
        var config = WaypointClientMod.getClientConfig();
        if (DEFAULT_WAYPOINT_SET.equals(listName) && config != null
                && config.isXaeroDefaultListDirectSync()) {
            return DEFAULT_WAYPOINT_SET;
        }
        return SyncedWaypointName.formatSyncedName(listName);
    }

    /** Ownership in the shared default set is carried by the waypoint name. */
    public static String getSyncedWaypointListName(String setName, String waypointName) {
        if (DEFAULT_WAYPOINT_SET.equals(setName)) {
            return SyncedWaypointName.parseSyncedName(waypointName) == null ? null : DEFAULT_WAYPOINT_SET;
        }
        return SyncedWaypointName.parseSyncedName(setName);
    }

    public static MinimapSession getMinimapSession() {
        return BuiltInHudModules.MINIMAP.getCurrentSession();
    }
    
    public static String getMinimapWorldNode(MinimapSession session, ResourceKey<Level> dimKey) {
        return session.getWorldStateUpdater().getPotentialWorldNode(dimKey, false);
    }

    public static MinimapWorld getMinimapWorld(MinimapSession session, ResourceKey<Level> dimKey) {
        Level level = Minecraft.getInstance().level;
        return getMinimapWorld(session, dimKey, level == null ? null : level.dimension());
    }

    static MinimapWorld getMinimapWorld(MinimapSession session, ResourceKey<Level> dimKey,
                                        @Nullable ResourceKey<Level> clientDimension) {
        MinimapWorldManager manager = session.getWorldManager();
        // Xaero can change the automatic world path after receiving a server level
        // id or after an Auto connection. For the current dimension, its active
        // automatic world is authoritative once it matches the requested dimension;
        // rebuilding the path can create a second, unconnected sub-world and
        // duplicate waypoint sets. During a dimension change Minecraft can already
        // report the destination while Xaero's automatic world still points to the
        // previous dimension.
        if (dimKey.equals(clientDimension)) {
            MinimapWorld autoWorld = manager.getAutoWorld();
            if (autoWorld != null && dimKey.equals(autoWorld.getDimId())) {
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

    public static void replaceSyncedWaypoint(WaypointSet waypointSet, SimpleWaypoint simpleWaypoint) {
        replaceSyncedWaypoint(waypointSet, simpleWaypoint, XaerosWaypointHelper::simpleWaypointToXaerosWaypoint);
    }

    static void replaceSyncedWaypoint(WaypointSet waypointSet, SimpleWaypoint simpleWaypoint,
                                      BiFunction<SimpleWaypoint, String, Waypoint> waypointFactory) {
        String name = syncedWaypointName(waypointSet, simpleWaypoint.name());
        if (name == null) {
            WaypointClientMod.LOGGER.warn("Skipping Xaero's Minimap sync for waypoint {} because its generated name would be ambiguous.", simpleWaypoint.name());
            return;
        }
        removeSyncedWaypoint(waypointSet, simpleWaypoint.name());
        if (!DEFAULT_WAYPOINT_SET.equals(waypointSet.getName())) {
            removeDuplicateWaypoints(waypointSet);
        }
        waypointSet.add(waypointFactory.apply(simpleWaypoint, name));
    }

    public static boolean replaceWaypointList(MinimapWorld minimapWorld, WaypointList waypointList) {
        return replaceWaypointList(
                minimapWorld,
                waypointList,
                XaerosWaypointHelper::simpleWaypointToXaerosWaypoint
        );
    }

    static boolean replaceWaypointList(MinimapWorld minimapWorld, WaypointList waypointList,
                                    BiFunction<SimpleWaypoint, String, Waypoint> waypointFactory) {
        if (!canSyncWaypointList(waypointList)) {
            return false;
        }
        prepareDefaultListSync(minimapWorld, waypointList.name());
        WaypointSet waypointSet = getOrCreateSyncedWaypointSet(minimapWorld, waypointList.name());
        if (waypointSet == null) {
            return false;
        }
        syncWaypointSetByWaypoints(waypointSet, waypointList, waypointFactory);
        return true;
    }

    public static WaypointSet getOrCreateSyncedWaypointSet(MinimapWorld minimapWorld, String listName) {
        String syncedListName = getSyncedWaypointSetName(listName);
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

    private static void prepareDefaultListSync(MinimapWorld minimapWorld, String listName) {
        if (DEFAULT_WAYPOINT_SET.equals(listName)) {
            if (DEFAULT_WAYPOINT_SET.equals(getSyncedWaypointSetName(listName))) {
                removeWaypointSet(minimapWorld, SyncedWaypointName.formatSyncedName(listName));
            } else {
                removeMarkedDefaultWaypoints(minimapWorld);
            }
        }
    }

    private static boolean canSyncWaypointList(WaypointList list) {
        if (!DEFAULT_WAYPOINT_SET.equals(getSyncedWaypointSetName(list.name()))) {
            return true;
        }
        for (SimpleWaypoint waypoint : list.simpleWaypoints()) {
            if (SyncedWaypointName.formatSyncedName(waypoint.name()) == null) {
                WaypointClientMod.LOGGER.warn("Skipping Xaero's Minimap sync for list {} because waypoint {} would have an ambiguous generated name.", list.name(), waypoint.name());
                return false;
            }
        }
        return true;
    }

    private static void syncWaypointSetByWaypoints(WaypointSet waypointSet, WaypointList waypointList) {
        syncWaypointSetByWaypoints(waypointSet, waypointList,
                XaerosWaypointHelper::simpleWaypointToXaerosWaypoint);
    }

    private static void syncWaypointSetByWaypoints(WaypointSet waypointSet, WaypointList waypointList,
                                                   BiFunction<SimpleWaypoint, String, Waypoint> waypointFactory) {
        // Direct default-set sync is an explicit opt-in to replacing that set's contents.
        // Other personal sets remain outside the server synchronization boundary.
        removeAllWaypoints(waypointSet);
        addUniqueSyncedWaypoints(waypointSet, waypointList, waypointFactory);
    }

    public static boolean replaceWaypointLists(MinimapWorld minimapWorld, List<WaypointList> waypointLists) {
        boolean synced = true;
        Map<String, WaypointList> waypointListsBySyncedName = new LinkedHashMap<>();
        Set<String> skippedSetNames = new HashSet<>();
        for (WaypointList waypointList : waypointLists) {
            String syncedListName = getSyncedWaypointSetName(waypointList.name());
            if (syncedListName == null) {
                WaypointClientMod.LOGGER.warn("Skipping Xaero's Minimap sync for list {} because its generated name would be ambiguous.", waypointList.name());
                synced = false;
                continue;
            }
            if (!canSyncWaypointList(waypointList)) {
                skippedSetNames.add(syncedListName);
                synced = false;
                continue;
            }
            waypointListsBySyncedName.putIfAbsent(syncedListName, waypointList);
        }

        if (!waypointListsBySyncedName.containsKey(DEFAULT_WAYPOINT_SET)
                && !skippedSetNames.contains(DEFAULT_WAYPOINT_SET)) {
            removeMarkedDefaultWaypoints(minimapWorld);
        }
        Set<String> syncedExistingListNames = new HashSet<>();
        for (WaypointSet waypointSet : getSyncedWaypointSets(minimapWorld)) {
            if (skippedSetNames.contains(getSyncedWaypointSetName(
                    SyncedWaypointName.parseSyncedName(waypointSet.getName())))) {
                continue;
            }
            WaypointList waypointList = waypointListsBySyncedName.get(waypointSet.getName());
            if (waypointList == null) {
                removeSyncedWaypointSet(minimapWorld, waypointSet.getName());
                continue;
            }
            syncWaypointSetByWaypoints(waypointSet, waypointList);
            syncedExistingListNames.add(waypointSet.getName());
        }

        for (Map.Entry<String, WaypointList> entry : waypointListsBySyncedName.entrySet()) {
            if (!syncedExistingListNames.contains(entry.getKey())) {
                prepareDefaultListSync(minimapWorld, entry.getValue().name());
                WaypointSet waypointSet = getOrCreateSyncedWaypointSet(minimapWorld, entry.getValue().name());
                syncWaypointSetByWaypoints(waypointSet, entry.getValue());
            }
        }
        return synced;
    }

    public static void removeSyncedWaypointSet(MinimapWorld minimapWorld, String waypointSetName) {
        if (DEFAULT_WAYPOINT_SET.equals(waypointSetName)
                || SyncedWaypointName.formatSyncedName(DEFAULT_WAYPOINT_SET).equals(waypointSetName)) {
            removeMarkedDefaultWaypoints(minimapWorld);
            removeWaypointSet(minimapWorld, SyncedWaypointName.formatSyncedName(DEFAULT_WAYPOINT_SET));
        } else {
            removeWaypointSet(minimapWorld, waypointSetName);
        }
    }

    private static void removeMarkedDefaultWaypoints(MinimapWorld minimapWorld) {
        WaypointSet set = minimapWorld.getWaypointSet(DEFAULT_WAYPOINT_SET);
        if (set == null) {
            return;
        }
        Iterator<Waypoint> iterator = set.getWaypoints().iterator();
        while (iterator.hasNext()) {
            if (SyncedWaypointName.parseSyncedName(iterator.next().getName()) != null) {
                iterator.remove();
            }
        }
    }

    private static String syncedWaypointName(WaypointSet waypointSet, String name) {
        return DEFAULT_WAYPOINT_SET.equals(waypointSet.getName())
                ? SyncedWaypointName.formatSyncedName(name) : name;
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

    public static void addDimensionWaypoint(MinimapSession session, DimensionWaypointData dimensionWaypointBuffer) {
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

    public static boolean removeSyncedWaypoint(WaypointSet waypointSet, String waypointName) {
        int previousSize = waypointSet.size();
        if (!DEFAULT_WAYPOINT_SET.equals(waypointSet.getName())) {
            removeWaypointsByName(waypointSet, waypointName);
        }
        String legacySyncedName = SyncedWaypointName.formatSyncedName(waypointName);
        if (legacySyncedName != null) {
            removeWaypointsByName(waypointSet, legacySyncedName);
        }
        return waypointSet.size() < previousSize;
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

    private static void removeAllWaypoints(WaypointSet waypointSet) {
        Iterator<Waypoint> iterator = waypointSet.getWaypoints().iterator();
        while (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static void addUniqueSyncedWaypoints(WaypointSet waypointSet, WaypointList waypointList,
                                                 BiFunction<SimpleWaypoint, String, Waypoint> waypointFactory) {
        Set<String> addedNames = new HashSet<>();
        for (SimpleWaypoint simpleWaypoint : waypointList.simpleWaypoints()) {
            String name = simpleWaypoint.name();
            if (!addedNames.add(name)) {
                continue;
            }
            removeWaypointsByName(waypointSet, name);
            waypointSet.add(waypointFactory.apply(simpleWaypoint, syncedWaypointName(waypointSet, name)));
        }
    }
}
