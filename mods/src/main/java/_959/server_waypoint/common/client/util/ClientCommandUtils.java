package _959.server_waypoint.common.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ClientCommandUtils {
    public static CompletableFuture<List<String>> getCommandSuggestions(String command) {
        ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();
        if (networkHandler == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        var commands = networkHandler.getCommands();
        var parseResults = commands.parse(command, networkHandler.getSuggestionsProvider());
        return commands.getCompletionSuggestions(parseResults).thenApply(suggestions ->
                suggestions.getList().stream().map(suggestion -> suggestion.getText()).toList()
        );
    }

    public static boolean sendCommand(String command) {
        ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();
        if (networkHandler != null) {
            networkHandler.sendCommand(command);
            return true;
        } else {
            return false;
        }
    }
}
