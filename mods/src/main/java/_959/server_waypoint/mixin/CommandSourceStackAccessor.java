package _959.server_waypoint.mixin;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSourceStack.class)
public interface CommandSourceStackAccessor {
    @Accessor("source")
    CommandSource serverWaypoint$getSource();

    /** Whether the stack suppresses its output, as the stacks that run datapack functions do. */
    @Accessor("silent")
    boolean serverWaypoint$isSilent();
}
