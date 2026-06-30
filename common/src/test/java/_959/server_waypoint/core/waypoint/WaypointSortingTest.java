package _959.server_waypoint.core.waypoint;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointSortingTest {
    @Test
    void sortsWaypointsByNameCaseInsensitive() {
        List<SimpleWaypoint> waypoints = new ArrayList<>(List.of(
                waypoint("zeta", 0, 0, 0, 0xFF0000),
                waypoint("Alpha", 0, 0, 0, 0x00FF00),
                waypoint("beta", 0, 0, 0, 0x0000FF)
        ));

        waypoints.sort(WaypointSorting.byName());

        assertEquals(List.of("Alpha", "beta", "zeta"), names(waypoints));
    }

    @Test
    void sortsWaypointsByDistanceFromPosition() {
        List<SimpleWaypoint> waypoints = new ArrayList<>(List.of(
                waypoint("far", 10, 0, 0, 0xFF0000),
                waypoint("near", 1, 0, 0, 0x00FF00),
                waypoint("middle", 3, 4, 0, 0x0000FF)
        ));

        waypoints.sort(WaypointSorting.byDistanceFrom(new WaypointPos(0, 0, 0)));

        assertEquals(List.of("near", "middle", "far"), names(waypoints));
    }

    @Test
    void sortsColorsByOklchHueRegionsBeforeGreyTail() {
        List<SimpleWaypoint> waypoints = new ArrayList<>(List.of(
                waypoint("white", 0, 0, 0, 0xFFFFFF),
                waypoint("blue", 0, 0, 0, 0x0000FF),
                waypoint("black", 0, 0, 0, 0x000000),
                waypoint("green", 0, 0, 0, 0x00FF00),
                waypoint("gray", 0, 0, 0, 0x808080),
                waypoint("magenta", 0, 0, 0, 0xFF00FF),
                waypoint("red", 0, 0, 0, 0xFF0000),
                waypoint("yellow", 0, 0, 0, 0xFFFF00),
                waypoint("cyan", 0, 0, 0, 0x00FFFF)
        ));

        WaypointSorting.sortByColor(waypoints);

        assertEquals(List.of("red", "yellow", "green", "cyan", "blue", "magenta", "black", "gray", "white"), names(waypoints));
    }

    @Test
    void colorSortKeepsStrictGreyTailDarkToBright() {
        List<SimpleWaypoint> waypoints = new ArrayList<>(List.of(
                waypoint("gray", 0, 0, 0, 0x808080),
                waypoint("red", 0, 0, 0, 0xFF0000),
                waypoint("white", 0, 0, 0, 0xFFFFFF),
                waypoint("blue", 0, 0, 0, 0x0000FF),
                waypoint("black", 0, 0, 0, 0x000000)
        ));

        WaypointSorting.sortByColor(waypoints);

        assertEquals(List.of("black", "gray", "white"), names(waypoints).subList(2, 5));
    }

    @Test
    void colorSortUsesSmoothedPhaseGradientForLocalContinuity() {
        List<SimpleWaypoint> waypoints = new ArrayList<>(List.of(
                waypoint("c0", 0, 0, 0, 0xD7BAC1),
                waypoint("c1", 0, 0, 0, 0xE2ACB0),
                waypoint("c2", 0, 0, 0, 0xF0AFBE),
                waypoint("c3", 0, 0, 0, 0xEC88A8),
                waypoint("c4", 0, 0, 0, 0xF28AA4),
                waypoint("c5", 0, 0, 0, 0xFD7CA7),
                waypoint("c6", 0, 0, 0, 0xEA4E82),
                waypoint("c7", 0, 0, 0, 0xF84B8B),
                waypoint("c8", 0, 0, 0, 0xF46588),
                waypoint("c9", 0, 0, 0, 0xEA7086),
                waypoint("c10", 0, 0, 0, 0xE36B88),
                waypoint("c11", 0, 0, 0, 0xD86583),
                waypoint("c12", 0, 0, 0, 0xC35F81),
                waypoint("c13", 0, 0, 0, 0xBC6B76),
                waypoint("c14", 0, 0, 0, 0xBC7E85),
                waypoint("c15", 0, 0, 0, 0xB36A76),
                waypoint("c16", 0, 0, 0, 0xA65560),
                waypoint("c17", 0, 0, 0, 0x855D67),
                waypoint("c18", 0, 0, 0, 0x6B494D),
                waypoint("c19", 0, 0, 0, 0x823E48),
                waypoint("c20", 0, 0, 0, 0x63293A),
                waypoint("c21", 0, 0, 0, 0x710D3A),
                waypoint("c22", 0, 0, 0, 0x420819),
                waypoint("c23", 0, 0, 0, 0x33010C),
                waypoint("c24", 0, 0, 0, 0x320A12),
                waypoint("c25", 0, 0, 0, 0x311E23),
                waypoint("c26", 0, 0, 0, 0xA9345C),
                waypoint("c27", 0, 0, 0, 0xB51048),
                waypoint("c28", 0, 0, 0, 0xC50E4E),
                waypoint("c29", 0, 0, 0, 0xCE1460),
                waypoint("c30", 0, 0, 0, 0xDA2F5E),
                waypoint("c31", 0, 0, 0, 0xD83B79)
        ));

        WaypointSorting.sortByColor(waypoints);

        assertTrue(maxAdjacentOklchDistanceSquared(waypoints) < 0.022D);
    }

    private static SimpleWaypoint waypoint(String name, int x, int y, int z, int rgb) {
        return new SimpleWaypoint(name, name.substring(0, 1), x, y, z, rgb, 0, false);
    }

    private static List<String> names(List<SimpleWaypoint> waypoints) {
        return waypoints.stream().map(SimpleWaypoint::name).toList();
    }

    private static double maxAdjacentOklchDistanceSquared(List<SimpleWaypoint> waypoints) {
        double maxDistance = 0.0D;
        for (int i = 1; i < waypoints.size(); i++) {
            OklchColor color1 = rgbToOklch(waypoints.get(i - 1).rgb());
            OklchColor color2 = rgbToOklch(waypoints.get(i).rgb());
            double lightnessDistance = color1.lightness() - color2.lightness();
            double aDistance = color1.chroma() * Math.cos(Math.toRadians(color1.hue()))
                    - color2.chroma() * Math.cos(Math.toRadians(color2.hue()));
            double bDistance = color1.chroma() * Math.sin(Math.toRadians(color1.hue()))
                    - color2.chroma() * Math.sin(Math.toRadians(color2.hue()));
            double distance = lightnessDistance * lightnessDistance + aDistance * aDistance + bDistance * bDistance;
            maxDistance = Math.max(maxDistance, distance);
        }
        return maxDistance;
    }

    private static OklchColor rgbToOklch(int rgb) {
        double r = linearizeSrgb((rgb >> 16) & 0xFF);
        double g = linearizeSrgb((rgb >> 8) & 0xFF);
        double b = linearizeSrgb(rgb & 0xFF);
        double l = Math.cbrt(0.4122214708D * r + 0.5363325363D * g + 0.0514459929D * b);
        double m = Math.cbrt(0.2119034982D * r + 0.6806995451D * g + 0.1073969566D * b);
        double s = Math.cbrt(0.0883024619D * r + 0.2817188376D * g + 0.6299787005D * b);
        double lightness = 0.2104542553D * l + 0.7936177850D * m - 0.0040720468D * s;
        double a = 1.9779984951D * l - 2.4285922050D * m + 0.4505937099D * s;
        double bOpponent = 0.0259040371D * l + 0.7827717662D * m - 0.8086757660D * s;
        double hue = Math.toDegrees(Math.atan2(bOpponent, a));
        if (hue < 0.0D) {
            hue += 360.0D;
        }
        return new OklchColor(lightness, Math.hypot(a, bOpponent), hue);
    }

    private static double linearizeSrgb(int value) {
        double channel = value / 255.0D;
        if (channel <= 0.04045D) {
            return channel / 12.92D;
        }
        return Math.pow((channel + 0.055D) / 1.055D, 2.4D);
    }

    private record OklchColor(double lightness, double chroma, double hue) {
    }
}
