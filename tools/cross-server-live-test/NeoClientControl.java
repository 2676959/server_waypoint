package step19;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.client.Minecraft;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.neoforged.fml.common.Mod;

/** Disposable fixture control: sends commands through the real client connection. */
@Mod("native_client_control")
public final class NeoClientControl {
    private int clientTicks;

    public NeoClientControl() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> clientTicks++);
        System.out.println("STEP19_CONTROL_INIT NeoForge");
        Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "step19-control-poll");
            thread.setDaemon(true);
            return thread;
        }).scheduleWithFixedDelay(() -> { Minecraft client = Minecraft.getInstance(); if (client != null) client.execute(this::tick); },
                1, 1, TimeUnit.SECONDS);
    }

    private void tick() {
        Minecraft client = Minecraft.getInstance();
        Path commandFile = client.gameDirectory.toPath().resolve("native-command.txt");
        if (!Files.isRegularFile(commandFile)) return;
        try {
            String command = Files.readString(commandFile).strip();
            if (command.isEmpty()) return;
            if (!command.equals("STOP") && !command.equals("STATUS") && client.player == null) return;
            Files.delete(commandFile);
            if (command.equals("STATUS")) {
                System.out.println("STEP19_STATUS ticks=" + clientTicks
                        + " network=" + _959.server_waypoint.common.client.WaypointClientMod.getNetworkState()
                        + " screen=" + (client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName())
                        + " position=" + (client.player == null ? "none" : client.player.position()));
            } else if (command.equals("STOP")) client.stop();
            else client.player.connection.sendCommand(command);
            Files.writeString(client.gameDirectory.toPath().resolve("native-control.log"),
                    "SENT " + command + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception failure) {
            throw new IllegalStateException("Native client control failed", failure);
        }
    }
}
