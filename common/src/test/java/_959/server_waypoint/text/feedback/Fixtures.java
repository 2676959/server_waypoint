package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Viewer;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The sample server of the spec: a player at 100, 64, -20 in the Overworld, facing 37°. */
final class Fixtures {
    static final String OVERWORLD = "minecraft:overworld";
    static final String NETHER = "minecraft:the_nether";
    static final String END = "minecraft:the_end";
    static final String TWILIGHT = "twilightforest:twilight_forest";
    static final Map<String, String> LOADED = Map.of(OVERWORLD, OVERWORLD, NETHER, NETHER, END, END,
            TWILIGHT, "twilightforest:twilight_forest_type");

    private Fixtures() {
    }

    static Viewer player() {
        return new Viewer(Viewer.everything(), true, false, OVERWORLD, new WaypointPos(100, 64, -20), 37F);
    }

    /** A player who may browse and navigate, on a vanilla client. */
    static Viewer member() {
        return new Viewer(Set.of(Viewer.Permission.NAVIGATE), false, false, OVERWORLD, new WaypointPos(100, 64, -20), 37F);
    }

    static Viewer console() {
        return new Viewer(Viewer.everything(), false, true, OVERWORLD, new WaypointPos(0, 64, 0), 0F);
    }

    static Viewer in(Viewer viewer, String dimension) {
        return new Viewer(viewer.permissions(), viewer.hasMod(), viewer.plainText(), dimension, viewer.position(), viewer.yaw());
    }

    static DimensionStyle dims(Viewer viewer) {
        return DimensionStyle.local(viewer, LOADED);
    }

    static SimpleWaypoint waypoint(String name, String initials, int rgb, int x, int y, int z) {
        return new SimpleWaypoint(name, initials, new WaypointPos(x, y, z), rgb, 0, true);
    }

    static WaypointList homeBases() {
        return new WaypointList("Home Bases", 1, List.of(
                new SimpleWaypoint("Main Home", "Main Home", "MH", new WaypointPos(120, 64, -35), 0xFFAA00, 0, true,
                        List.of("home", "base"), "Where the beds are"),
                waypoint("Gem Mine", "GM", 0x55FFFF, -210, 12, 480),
                waypoint("Spawn Village", "SV", 0x55FF55, 0, 70, 0)));
    }

    static WaypointList farms() {
        return new WaypointList("Farms", 1, List.of(
                waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150),
                waypoint("Wheat Fields", "WF", 0xFFFF55, 80, 66, 40),
                waypoint("Cane Farm", "CF", 0x00AA00, 150, 63, -80),
                new SimpleWaypoint("Mob Grinder", "Mob Grinder", "MG", new WaypointPos(-40, 30, -300), 0xFF5555, 0, true,
                        List.of(), "Bring a sword"),
                waypoint("Villager Hall", "VH", 0xAA00AA, 60, 64, 90),
                waypoint("Slime Farm", "SF", 0x55FF55, 410, 20, -260),
                waypoint("Gold Farm", "GF", 0xFFAA00, 520, 120, 610)));
    }

    static WaypointList exploration() {
        return new WaypointList("Exploration", 1, List.of(
                waypoint("Ocean Monument", "OM", 0x5555FF, 1200, 40, -760),
                waypoint("Desert Temple", "DT", 0xFFFF55, -900, 68, 300),
                waypoint("Stronghold", "SH", 0xAA00AA, 2100, 20, -1500),
                waypoint("Woodland Mansion", "WM", 0xAA0000, -3400, 70, -2200)));
    }

    static List<WaypointList> overworldLists() {
        return List.of(homeBases(), farms(), exploration());
    }
}
