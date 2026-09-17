package _959.server_waypoint;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Dispatches Paper API work to its Folia ownership context. */
public final class PaperScheduler {
    private static final long MILLIS_PER_TICK = 50L;

    private final JavaPlugin plugin;

    public PaperScheduler(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public boolean execute(Entity entity, Runnable action) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(action, "action");
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            action.run();
            return true;
        }
        return entity.getScheduler().execute(this.plugin, action, null, 1L);
    }

    public void execute(CommandSender sender, Runnable action) {
        Objects.requireNonNull(sender, "sender");
        if (sender instanceof Entity entity) {
            this.execute(entity, action);
            return;
        }
        this.executeGlobal(action);
    }

    public void executeGlobal(Runnable action) {
        Objects.requireNonNull(action, "action");
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, action);
    }

    public ScheduledTask runNextTick(Entity entity, Runnable action) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(action, "action");
        return entity.getScheduler().run(this.plugin, ignored -> action.run(), null);
    }

    public ScheduledTask runAtFixedRate(
            Entity entity,
            Consumer<ScheduledTask> action,
            Runnable retired,
            long initialDelayTicks,
            long periodTicks
    ) {
        return entity.getScheduler().runAtFixedRate(
                this.plugin,
                action,
                retired,
                initialDelayTicks,
                periodTicks
        );
    }

    public void runNextTick(Inventory inventory, Runnable action) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(action, "action");
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Entity entity) {
            this.runNextTick(entity, action);
            return;
        }
        Location location = inventory.getLocation();
        if (location != null) {
            Bukkit.getRegionScheduler().run(
                    this.plugin,
                    location,
                    ignored -> action.run()
            );
            return;
        }
        Bukkit.getGlobalRegionScheduler().run(
                this.plugin,
                ignored -> action.run()
        );
    }

    public void runAsyncDelayed(Runnable action, long delayTicks) {
        Objects.requireNonNull(action, "action");
        Bukkit.getAsyncScheduler().runDelayed(
                this.plugin,
                ignored -> action.run(),
                Math.multiplyExact(delayTicks, MILLIS_PER_TICK),
                TimeUnit.MILLISECONDS
        );
    }
}
