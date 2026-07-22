package _959.server_waypoint.navigation;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class PaperNavigationMapRenderer extends MapRenderer {
    private static final int MAP_CURSOR_MIN = -128;
    private static final int MAP_CURSOR_MAX = 127;

    private final Map<UUID, TargetMarker> targets = new HashMap<>();

    PaperNavigationMapRenderer() {
        super(true);
    }

    void setTarget(
            UUID playerUuid,
            NavigationTarget target,
            int centerX,
            int centerZ,
            int blocksPerPixel
    ) {
        int cursorX = cursorCoordinate(target.position().x() - centerX, blocksPerPixel);
        int cursorZ = cursorCoordinate(target.position().z() - centerZ, blocksPerPixel);
        this.targets.put(
                playerUuid,
                new TargetMarker((byte) cursorX, (byte) cursorZ, Component.text(target.waypointName()))
        );
    }

    void removeTarget(UUID playerUuid) {
        this.targets.remove(playerUuid);
    }

    void clearTargets() {
        this.targets.clear();
    }

    @Override
    public void render(
            @NotNull MapView map,
            @NotNull MapCanvas canvas,
            @NotNull Player player
    ) {
        MapCursorCollection cursors = new MapCursorCollection();
        TargetMarker marker = this.targets.get(player.getUniqueId());
        if (marker != null) {
            cursors.addCursor(new MapCursor(
                    marker.x(),
                    marker.z(),
                    (byte) 0,
                    MapCursor.Type.TARGET_X,
                    true,
                    marker.caption()
            ));
        }
        canvas.setCursors(cursors);
    }

    private static int cursorCoordinate(int blockOffset, int blocksPerPixel) {
        int coordinate = (int) Math.round(blockOffset * 2.0D / blocksPerPixel);
        return Math.max(MAP_CURSOR_MIN, Math.min(MAP_CURSOR_MAX, coordinate));
    }

    private record TargetMarker(byte x, byte z, Component caption) {
    }
}
