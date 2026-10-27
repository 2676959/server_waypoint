//? if voxelmap {
package _959.server_waypoint.mixin.voxelmap;

import com.mamiyaotaru.voxelmap.VoxelMap;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VoxelMap.class, remap = false)
public interface VoxelMapWorldAccessor {
    @Accessor(value = "world", remap = false)
    ClientLevel sw$getWorld();
}
//?}
