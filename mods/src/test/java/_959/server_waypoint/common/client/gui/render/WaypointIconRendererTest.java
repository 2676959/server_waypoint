package _959.server_waypoint.common.client.gui.render;

import _959.server_waypoint.util.NamespacedId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointIconRendererTest {
    @Test
    void classifiesAvailableIconsAndPreservesMissingFallback() {
        NamespacedId diamond = NamespacedId.parse("minecraft:diamond");
        NamespacedId star = NamespacedId.parse("voxelmap:star");
        assertEquals(WaypointIconRenderer.Kind.ITEM,
                WaypointIconRenderer.classify(diamond, id -> id.equals(diamond), id -> false));
        assertEquals(WaypointIconRenderer.Kind.VOXELMAP,
                WaypointIconRenderer.classify(star, id -> false, id -> id.equals(star)));
        assertEquals(WaypointIconRenderer.Kind.INITIALS,
                WaypointIconRenderer.classify(diamond, id -> false, id -> false));
        assertEquals(WaypointIconRenderer.Kind.INITIALS,
                WaypointIconRenderer.classify(star, id -> false, id -> false));
        assertEquals(WaypointIconRenderer.Kind.INITIALS,
                WaypointIconRenderer.classify(null, id -> true, id -> true));
    }
}
