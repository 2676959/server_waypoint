package _959.server_waypoint.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface BoundKeyAccessor {
    @Accessor(value = "key"/*? if >= 26 {*/, remap = false/*?}*/)
    InputConstants.Key getBoundKey();
}
