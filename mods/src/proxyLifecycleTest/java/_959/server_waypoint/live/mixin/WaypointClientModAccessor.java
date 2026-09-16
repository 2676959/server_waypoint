package _959.server_waypoint.live.mixin;

import _959.server_waypoint.common.client.ClientSynchronizationTracker;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WaypointClientMod.class)
public interface WaypointClientModAccessor {
    @Accessor("chunkedMessages")
    ChunkedMessageManager<String> sw$getChunkedMessages();

    @Accessor("uploadChunkedMessages")
    ChunkedMessageManager<String> sw$getUploadChunkedMessages();

    @Accessor("synchronizationTracker")
    ClientSynchronizationTracker sw$getSynchronizationTracker();
}
