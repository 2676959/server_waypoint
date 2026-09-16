package _959.server_waypoint.live.mixin;

import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ChunkedMessageManager.class)
public interface ChunkedMessageManagerAccessor {
    @Accessor("peers")
    ConcurrentHashMap<?, ?> sw$getPeers();

    @Accessor("scheduledPeers")
    ArrayDeque<?> sw$getScheduledPeers();

    @Accessor("scheduledPeerSet")
    Set<?> sw$getScheduledPeerSet();

    @Accessor("globallyRetainedBytes")
    long sw$getGloballyRetainedBytes();
}
