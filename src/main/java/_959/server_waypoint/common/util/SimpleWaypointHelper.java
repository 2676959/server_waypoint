package _959.server_waypoint.common.util;
import _959.server_waypoint.common.server.waypoint.SimpleWaypoint;

import net.minecraft.registry.RegistryKey;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
import static _959.server_waypoint.common.util.XaeroDimensionStringConverter.convert;
import static _959.server_waypoint.common.ServerWaypointMod.LOGGER;

public class SimpleWaypointHelper {
    public static final String SEPARATOR = ":";
    public static final Style DEFAULT_STYLE = Style.EMPTY.withBold(false).withColor(Formatting.WHITE);
    public static final String XAERO_SHARE_PREFIX = "xaero-waypoint";

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
    public static Pair<@Nullable SimpleWaypoint, @Nullable RegistryKey<World>> chatShareToSimpleWaypoint(String shareString) {
        String[] args = shareString.split(SEPARATOR);
        if (args.length < 9) {
            return null;
        }
        if (XAERO_SHARE_PREFIX.equals(args[0])) {
            SimpleWaypoint simpleWaypoint = new SimpleWaypoint(
                    args[1],
                    args[2],
                    new BlockPos(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5])),
                    Integer.parseInt(args[6]),
                    Integer.parseInt(args[8]),
                    true
            );
            int firstBarIdx = args[9].indexOf('-');
            String dimString = args[9].substring(firstBarIdx + 1);
            RegistryKey<World> dimKey = convert(dimString);
            if (dimKey == null) {
                LOGGER.warn("unrecognized dimension '{}'", dimString);
                return null;
            }
            return new Pair<>(simpleWaypoint, dimKey);
        }
        return null;
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
