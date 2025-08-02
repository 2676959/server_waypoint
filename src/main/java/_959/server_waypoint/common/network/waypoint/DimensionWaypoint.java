package _959.server_waypoint.common.network.waypoint;

import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.List;

public record DimensionWaypoint(RegistryKey<World> dimKey, List<WaypointList> waypointLists) {}
