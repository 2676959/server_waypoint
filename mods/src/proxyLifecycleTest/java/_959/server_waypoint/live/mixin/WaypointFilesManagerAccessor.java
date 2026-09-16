package _959.server_waypoint.live.mixin;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(WaypointFilesManagerCore.class)
public interface WaypointFilesManagerAccessor {
    @Accessor("fileManagerMap")
    Map<String, WaypointFileManager> sw$getFileManagerMap();
}
