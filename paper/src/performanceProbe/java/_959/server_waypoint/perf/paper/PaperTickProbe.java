package _959.server_waypoint.perf.paper;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.google.gson.JsonObject;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class PaperTickProbe implements Listener {
    private final PaperPerformanceProbe plugin;
    private final PaperFixtureAdapter fixture;
    private long tickCount;

    PaperTickProbe(PaperPerformanceProbe plugin, PaperFixtureAdapter fixture) {
        this.plugin = plugin;
        this.fixture = fixture;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void tickEnd(ServerTickEndEvent event) {
        tickCount++;
        if (!plugin.sampling()) {
            return;
        }
        fixture.observePhysicalChanges();
        JsonObject sample = fixture.snapshot();
        sample.addProperty("durationNs", Math.round(event.getTickDuration() * 1_000_000));
        sample.addProperty("tickIndex", event.getTickNumber());
        sample.addProperty("tickCount", tickCount);
        sample.addProperty("phase", plugin.phase());
        sample.addProperty("clock", "server-monotonic");
        sample.addProperty("scope", "server-thread");
        sample.addProperty("regionId", "global");
        plugin.addWorkloadSample(sample);
        plugin.emit("sample", sample);
    }
}
