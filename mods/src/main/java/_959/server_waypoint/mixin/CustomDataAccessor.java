//? if >=1.20.5 {
package _959.server_waypoint.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CustomData.class)
public interface CustomDataAccessor {
    // Borrowed read-only data: callers must use CustomData.update for writes.
    @Accessor(value = "tag"/*? if >=26 {*/, remap = false/*?}*/)
    CompoundTag serverWaypoint$getTag();
}
//?}
