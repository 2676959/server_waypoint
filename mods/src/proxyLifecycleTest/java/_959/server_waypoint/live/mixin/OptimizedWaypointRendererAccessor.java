package _959.server_waypoint.live.mixin;

import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(OptimizedWaypointRenderer.class)
public interface OptimizedWaypointRendererAccessor {
    @Accessor("trackedWaypointRefs")
    static Set<SimpleWaypoint> sw$getTrackedWaypointRefs() {
        throw new AssertionError();
    }

    @Accessor("queue")
    static ConcurrentLinkedQueue<?> sw$getQueue() {
        throw new AssertionError();
    }

    @Accessor("nextRenderId")
    static int sw$getNextRenderId() {
        throw new AssertionError();
    }
}
