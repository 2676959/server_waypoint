package _959.server_waypoint.util;
import _959.server_waypoint.server.waypoint.SimpleWaypoint;

import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.WaypointVisibilityType;

public class SimpleWaypointHelper {
    public static final String SEPARATOR = ":";
    public static final Style DEFAULT_STYLE = Style.EMPTY.withBold(false).withColor(Formatting.WHITE);

    public static String simpleWaypointToString(SimpleWaypoint simpleWaypoint) {
        //format: name:initials:x:y:z:color:yaw:global
        //          0 :    1   :2:3:4:  5  : 6 :  7
        return simpleWaypoint.name() +
                SEPARATOR +
                simpleWaypoint.initials() +
                SEPARATOR +
                simpleWaypoint.pos().getX() +
                SEPARATOR +
                simpleWaypoint.pos().getY() +
                SEPARATOR +
                simpleWaypoint.pos().getZ() +
                SEPARATOR +
                simpleWaypoint.colorIdx() +
                SEPARATOR +
                simpleWaypoint.yaw() +
                SEPARATOR +
                simpleWaypoint.global();
    }

    public static SimpleWaypoint stringToSimpleWaypoint(String waypointString) {
        String[] args = waypointString.split(SEPARATOR);
        int colorIdx = Integer.parseInt(args[5]);
        return new SimpleWaypoint(
                args[0],
                args[1],
                new BlockPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4])),
                colorIdx >= 0 && colorIdx <= 15 ? colorIdx : 15,
                Integer.parseInt(args[6]),
                Boolean.parseBoolean(args[7])
        );
    }

    public static MutableText simpleWaypointToFormattedText(SimpleWaypoint waypoint, String command) {
        Style initialStyle = Style.EMPTY.withBold(true).withColor(Formatting.byColorIndex(waypoint.colorIdx())).withClickEvent(new ClickEvent.RunCommand(command));
        MutableText waypointText = Text.literal("[" + waypoint.initials() + "]").setStyle(initialStyle);
        waypointText.append(Text.literal(" ").setStyle(DEFAULT_STYLE));
        Style nameStyle = DEFAULT_STYLE.withHoverEvent(new HoverEvent.ShowText(Text.of("tp " + waypoint.pos().toShortString())));
        waypointText.append(Text.literal(waypoint.name()).setStyle(nameStyle));
        return waypointText;
    }

    public static Waypoint simpleWaypointToWaypoint(SimpleWaypoint simpleWaypoint) {
        Waypoint waypoint = new Waypoint(
                simpleWaypoint.pos().getX(),
                simpleWaypoint.pos().getY(),
                simpleWaypoint.pos().getZ(),
                simpleWaypoint.name(),
                simpleWaypoint.initials(),
                WaypointColor.fromIndex(simpleWaypoint.colorIdx()),
                WaypointPurpose.NORMAL,
                false,
                true
        );
        waypoint.setYaw(simpleWaypoint.yaw());
        waypoint.setRotation(true);
        waypoint.setVisibility(simpleWaypoint.global() ? WaypointVisibilityType.GLOBAL : WaypointVisibilityType.LOCAL);
        return waypoint;
    }
}
