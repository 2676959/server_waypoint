package _959.server_waypoint.common.util;

import _959.server_waypoint.core.waypoint.WaypointPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CoordinateSuggestions {
    private static final List<String> SHORTCUTS = List.of("~", "^");

    private CoordinateSuggestions() {
    }

    public static List<String> forAxis(Axis axis, @Nullable WaypointPos targetedBlockPos) {
        List<String> suggestions = new ArrayList<>(SHORTCUTS.size() + 1);
        if (targetedBlockPos != null) {
            suggestions.add(Integer.toString(axis.coordinate(targetedBlockPos)));
        }
        suggestions.addAll(SHORTCUTS);
        return suggestions;
    }

    public enum Axis {
        X {
            @Override
            int coordinate(WaypointPos pos) {
                return pos.x();
            }
        },
        Y {
            @Override
            int coordinate(WaypointPos pos) {
                return pos.y();
            }
        },
        Z {
            @Override
            int coordinate(WaypointPos pos) {
                return pos.z();
            }
        };

        abstract int coordinate(WaypointPos pos);
    }
}
