package _959.server_waypoint.common.client.command;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;

public class ClientWaypointCommand {
    public static final String OPEN_GUI_COMMAND = "wp_gui";

    @SuppressWarnings("unchecked")
    public static <S> void register(@NotNull CommandDispatcher<S> dispatcher) {
        dispatcher.register(
                (LiteralArgumentBuilder<S>) literal(OPEN_GUI_COMMAND)
                        .executes((context) -> executeOpenGui())
        );
    }

    private static int executeOpenGui() {
        Minecraft mc = Minecraft.getInstance();
        mc.
                //? if > 1.21 {
                schedule
                //?} else {
                /*execute
                *///?}
                (() -> MinecraftClientHelper.setScreen(mc, new WaypointManagerScreen(WaypointClientMod.getInstance())));
        return Command.SINGLE_SUCCESS;
    }
}
