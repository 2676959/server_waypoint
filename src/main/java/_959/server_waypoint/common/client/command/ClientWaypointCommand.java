package _959.server_waypoint.common.client.command;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.MinecraftClient;
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
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.send(() -> mc.setScreen(new WaypointManagerScreen(WaypointClientMod.getInstance())));
        return Command.SINGLE_SUCCESS;
    }
}
