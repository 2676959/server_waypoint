package _959.server_waypoint.common.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.DimensionWaypoint;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.path.XaeroPath;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;

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

    public static void replaceWaypoint(WaypointSet waypointSet, Waypoint waypoint) {
        String name = waypoint.getName();
        removeWaypointsByName(waypointSet, name);
        waypointSet.add(waypoint);
    }

    public static void replaceWaypointList(MinimapWorld minimapWorld, WaypointList waypointList) {
       WaypointSet waypointSet = WaypointSet.Builder.begin().setName(waypointList.name()).build();
        for (SimpleWaypoint simpleWaypoint : waypointList.simpleWaypoints()) {
            if (simpleWaypoint != null) {
//                ServerWaypointClientMod.LOGGER.info("waypoint {} added", simpleWaypoint.name());
                waypointSet.add(SimpleWaypointHelper.simpleWaypointToWaypoint(simpleWaypoint));
            }
        }
        minimapWorld.addWaypointSet(waypointSet);
    }

    public static void replaceWaypointLists(MinimapWorld minimapWorld, List<WaypointList> waypointLists) {
        for (WaypointList waypointList : waypointLists) {
            replaceWaypointList(minimapWorld, waypointList);
//            ServerWaypointClientMod.LOGGER.info("waypoint set {} added", waypointList.name());
        }
    }

    public static void addDimensionWaypoint(MinimapSession session, DimensionWaypoint dimensionWaypoint) {
        MinimapWorld minimapWorld = getMinimapWorld(session, getDimensionKey(dimensionWaypoint.dimString()));
        replaceWaypointLists(minimapWorld, dimensionWaypoint.waypointLists());
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
}
