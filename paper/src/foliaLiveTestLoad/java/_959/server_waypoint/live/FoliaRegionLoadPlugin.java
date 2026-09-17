package _959.server_waypoint.live;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class FoliaRegionLoadPlugin extends JavaPlugin {
    private static final int MAX_DURATION_SECONDS = 300;
    private static final int MAX_BUSY_MILLIS = 250;
    private final Map<UUID, LoadSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void onDisable() {
        this.sessions.values().forEach(session -> session.task().cancel());
        this.sessions.clear();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            return false;
        }
        return switch (args[0]) {
            case "start" -> this.start(sender, args);
            case "stop" -> this.stop(sender, args);
            case "status" -> this.status(sender);
            default -> false;
        };
    }

    private boolean start(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            return false;
        }
        Player player = resolvePlayer(sender, args.length == 4 ? args[3] : null);
        if (player == null) {
            sender.sendMessage("A connected target player is required.");
            return true;
        }
        int durationSeconds;
        int busyMillis;
        try {
            durationSeconds = bounded(args[1], 1, MAX_DURATION_SECONDS, "duration seconds");
            busyMillis = bounded(args[2], 1, MAX_BUSY_MILLIS, "busy milliseconds");
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(exception.getMessage());
            return true;
        }
        Instant deadline = Instant.now().plusSeconds(durationSeconds);
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(durationSeconds).toNanos();
        AtomicReference<LoadSession> sessionReference = new AtomicReference<>();
        ScheduledTask task = player.getScheduler().runAtFixedRate(
                this,
                scheduledTask -> {
                    LoadSession session = sessionReference.get();
                    if (System.nanoTime() >= deadlineNanos) {
                        scheduledTask.cancel();
                        this.sessions.remove(player.getUniqueId(), session);
                        this.getLogger().info("Region load finished for " + player.getName());
                        return;
                    }
                    blockFor(Duration.ofMillis(busyMillis));
                },
                () -> {
                    LoadSession session = sessionReference.get();
                    this.sessions.remove(player.getUniqueId(), session);
                },
                1L,
                1L
        );
        LoadSession session = new LoadSession(player.getName(), deadline, busyMillis, task);
        sessionReference.set(session);
        LoadSession existing = this.sessions.putIfAbsent(player.getUniqueId(), session);
        if (existing != null) {
            task.cancel();
            sender.sendMessage("A region load is already active for " + player.getName() + ".");
            return true;
        }
        sender.sendMessage(
                "Started region load for " + player.getName() + ": "
                        + busyMillis + " ms/tick for " + durationSeconds + " seconds."
        );
        this.getLogger().info(
                "Region load started for " + player.getName() + " at " + player.getLocation()
                        + ": " + busyMillis + " ms/tick for " + durationSeconds + " seconds"
        );
        return true;
    }

    private boolean stop(CommandSender sender, String[] args) {
        if (args.length > 2) {
            return false;
        }
        Player player = resolvePlayer(sender, args.length == 2 ? args[1] : null);
        if (player == null) {
            sender.sendMessage("A connected target player is required.");
            return true;
        }
        LoadSession session = this.sessions.remove(player.getUniqueId());
        if (session == null) {
            sender.sendMessage("No region load is active for " + player.getName() + ".");
            return true;
        }
        session.task().cancel();
        sender.sendMessage("Stopped region load for " + player.getName() + ".");
        this.getLogger().info("Region load stopped for " + player.getName());
        return true;
    }

    private boolean status(CommandSender sender) {
        if (this.sessions.isEmpty()) {
            sender.sendMessage("No region loads are active.");
            return true;
        }
        this.sessions.values().forEach(session -> sender.sendMessage(
                session.playerName() + ": " + session.busyMillis() + " ms/tick until "
                        + session.deadline()
        ));
        return true;
    }

    private Player resolvePlayer(CommandSender sender, String name) {
        if (name != null) {
            return this.getServer().getPlayerExact(name);
        }
        return sender instanceof Player player ? player : null;
    }

    private static int bounded(String value, int minimum, int maximum, String label) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be an integer", exception);
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(
                    label + " must be between " + minimum + " and " + maximum
            );
        }
        return parsed;
    }

    private static void blockFor(Duration duration) {
        long deadline = System.nanoTime() + duration.toNanos();
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0L) {
            LockSupport.parkNanos(remaining);
        }
    }

    private record LoadSession(
            String playerName,
            Instant deadline,
            int busyMillis,
            ScheduledTask task
    ) {
    }
}
