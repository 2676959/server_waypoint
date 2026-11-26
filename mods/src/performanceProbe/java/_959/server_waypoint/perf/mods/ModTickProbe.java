package _959.server_waypoint.perf.mods;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

final class ModTickProbe {
    private long startedAt;
    private boolean open;
    private long tickIndex;

    void start(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("Tick hook has no server");
        }
        startedAt = System.nanoTime();
        open = true;
    }

    JsonObject end(MinecraftServer server, ModFixtureAdapter fixture, String phase) {
        tickIndex++;
        if (!open) {
            return null;
        }
        long duration = System.nanoTime() - startedAt;
        open = false;
        JsonObject sample = fixture.snapshot(server);
        sample.addProperty("durationNs", duration);
        sample.addProperty("tickIndex", tickIndex);
        sample.addProperty("phase", phase);
        sample.addProperty("clock", "server-monotonic");
        sample.addProperty("scope", "server-thread");
        sample.addProperty("regionId", "global");
        return sample;
    }
}
