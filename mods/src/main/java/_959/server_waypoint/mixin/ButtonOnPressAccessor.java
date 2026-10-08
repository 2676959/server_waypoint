package _959.server_waypoint.mixin;

import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Button.class)
public interface ButtonOnPressAccessor {
    @Accessor("onPress")
    Button.OnPress sw$getOnPress();

    @Mutable
    @Accessor("onPress")
    void sw$setOnPress(Button.OnPress action);
}
