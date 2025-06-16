package _959.server_waypoint.util;

import _959.server_waypoint.ServerWaypointClient;
import _959.server_waypoint.network.waypoint.DimensionWaypoint;
import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import _959.server_waypoint.server.waypoint.WaypointList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.path.XaeroPath;

import java.io.IOException;
import java.util.List;

public class XaeroMinimapHelper {
    public static MinimapSession getMinimapSession() {
        return BuiltInHudModules.MINIMAP.getCurrentSession();
    }
    
    public static String getMinimapWorldNode(MinimapSession session, RegistryKey<World> dimKey) {
        return session.getWorldStateUpdater().getPotentialWorldNode(dimKey, session.getModMain().getSupportMods().worldmap());
    }

    public static MinimapWorld getMinimapWorld(MinimapSession session, RegistryKey<World> dimKey) {
        String dimId = session.getDimensionHelper().getDimensionDirectoryName(dimKey);
        MinimapWorldManager manager = session.getWorldManager();
        XaeroPath root = manager.getAutoRootContainer().getPath();
        String node = getMinimapWorldNode(session, dimKey);
        ServerWaypointClient.LOGGER.info("node: {}", node);
        XaeroPath fullPath = root.resolve(dimId).resolve(node);
        return manager.getWorld(fullPath);
    }

    public static void saveMinimapWorld(MinimapSession session, MinimapWorld minimapWorld) throws IOException {
        session.getWorldManagerIO().saveWorld(minimapWorld);
    }

    public static void saveMinimapWorld(MinimapSession session, RegistryKey<World> dimKey) throws IOException {
        MinimapWorld minimapWorld = getMinimapWorld(session, dimKey);
        saveMinimapWorld(session, minimapWorld);
    }

    public static void addWaypointList(MinimapWorld minimapWorld, WaypointList waypointList){
        WaypointSet waypointSet = WaypointSet.Builder.begin().setName(waypointList.name()).build();
        for (SimpleWaypoint simpleWaypoint : waypointList.simpleWaypoints()) {  
            if (simpleWaypoint != null) {
                ServerWaypointClient.LOGGER.info("waypoint {} added", simpleWaypoint.name());
                waypointSet.add(SimpleWaypointHelper.simpleWaypointToWaypoint(simpleWaypoint));
            } else {
                ServerWaypointClient.LOGGER.warn("waypoint is null");
            }
        }
        minimapWorld.addWaypointSet(waypointSet);
    }

    public static void addWaypointLists(MinimapWorld minimapWorld, List<WaypointList> waypointLists) {
        for (WaypointList waypointList : waypointLists) {
            addWaypointList(minimapWorld, waypointList);
            ServerWaypointClient.LOGGER.info("waypoint set {} added", waypointList.name());
        }
    }

    public static void addDimensionWaypoint(MinimapSession session, DimensionWaypoint dimensionWaypoint) {
        MinimapWorld minimapWorld = getMinimapWorld(session, dimensionWaypoint.dimKey());
        addWaypointLists(minimapWorld, dimensionWaypoint.waypointLists());
    }
}
