package _959.server_waypoint.common.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;

import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.Nullable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
//? if >= 1.21.5 {
import xaero.hud.minimap.waypoint.WaypointVisibilityType;
//?} else {
/*import xaero.common.minimap.waypoints.WaypointVisibilityType;
*///?}
import static _959.server_waypoint.common.util.TextHelper.ClickEventHelper.RunCommand;
import static _959.server_waypoint.common.util.TextHelper.HoverEventHelper.ShowText;
import static _959.server_waypoint.util.XaeroDimensionStringConverter.toVanilla;

public class SimpleWaypointHelper {
    public static final String SEPARATOR = ":";
    public static final Style DEFAULT_STYLE = Style.EMPTY.withBold(false).withColor(Formatting.WHITE);
    public static final String XAERO_SHARE_PREFIX = "xaero-waypoint";

    public static MutableText simpleWaypointToFormattedText(SimpleWaypoint waypoint, String command, Text hoverText) {
        Style initialStyle = Style.EMPTY.withBold(true).withColor(Formatting.byColorIndex(waypoint.colorIdx())).withClickEvent(RunCommand.apply(command));
        MutableText waypointText = Text.literal("[" + waypoint.initials() + "]").setStyle(initialStyle);
        waypointText.append(Text.literal(" ").setStyle(DEFAULT_STYLE));
        Style nameStyle = DEFAULT_STYLE.withHoverEvent(ShowText.apply(hoverText));
        waypointText.append(Text.literal(waypoint.name()).setStyle(nameStyle));
        return waypointText;
    }

    // xaero-waypoint:a:A:-4:72:-9:13:false:0:Internal-overworld
    //        0      :1:2: 3: 4: 5: 6:  7  :8:        9
    @Nullable
    public static Pair<SimpleWaypoint, String> chatShareToSimpleWaypoint(String shareString) {
        String[] args = shareString.split(SEPARATOR);
        if (args.length < 9) {
            return null;
        }
        if (XAERO_SHARE_PREFIX.equals(args[0])) {
            SimpleWaypoint simpleWaypoint = new SimpleWaypoint(
                    args[1],
                    args[2],
                    new WaypointPos(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5])),
                    Integer.parseInt(args[6]),
                    Integer.parseInt(args[8]),
                    true
            );
            int firstBarIdx = args[9].indexOf('-');
            String xaeroDimString = args[9].substring(firstBarIdx + 1);
            String dimString = toVanilla(xaeroDimString);
            return new Pair<>(simpleWaypoint, dimString);
        }
        return null;
    }

    public static Waypoint simpleWaypointToWaypoint(SimpleWaypoint simpleWaypoint) {
        Waypoint waypoint = new Waypoint(
                simpleWaypoint.pos().x(),
                simpleWaypoint.pos().y(),
                simpleWaypoint.pos().z(),
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
