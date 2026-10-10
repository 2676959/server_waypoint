package _959.server_waypoint.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Shares vanilla's horizontal text viewport with the suggestion renderer. */
@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("displayPos")
    int sw$getDisplayPos();
}
