package step19;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.client.Minecraft;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraftforge.fml.common.Mod;

/** Disposable fixture control: sends commands through the real client connection. */
@Mod("native_client_control")
public final class NativeClientControl {
    private int clientTicks;

    public NativeClientControl() {
        net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.addListener(event -> clientTicks++);
        System.out.println("STEP19_CONTROL_INIT " + Minecraft.getInstance().gameDirectory);
        Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "step19-control-poll");
            thread.setDaemon(true);
            return thread;
        }).scheduleWithFixedDelay(() -> Minecraft.getInstance().execute(this::tick),
                1, 1, TimeUnit.SECONDS);
    }

    private void tick() {
        Minecraft client = Minecraft.getInstance();
        Path commandFile = client.gameDirectory.toPath().resolve("native-command.txt");
        if (!Files.isRegularFile(commandFile)) return;
        try {
            String command = Files.readString(commandFile).strip();
            if (command.isEmpty()) return;
            if (!command.equals("STOP") && client.player == null) return;
            Files.delete(commandFile);
            if (command.equals("STATUS")) {
                System.out.println("STEP19_STATUS ticks=" + clientTicks + " bus="
                        + net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.hasListeners()
                        + " network=" + _959.server_waypoint.common.client.WaypointClientMod.getNetworkState()
                        + " position=" + client.player.position());
            } else if (command.equals("STOP")) client.stop();
            else client.player.connection.sendCommand(command);
            Files.writeString(client.gameDirectory.toPath().resolve("native-control.log"),
                    "SENT " + command + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception failure) {
            throw new IllegalStateException("Native client control failed", failure);
        }
    }
}
