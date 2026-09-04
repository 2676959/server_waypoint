package _959.server_waypoint.live.mixin;

import _959.server_waypoint.common.client.ClientSynchronizationTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(ClientSynchronizationTracker.class)
public interface ClientSynchronizationTrackerAccessor {
    @Accessor("outOfSyncLists")
    Set<?> sw$getOutOfSyncLists();
}
