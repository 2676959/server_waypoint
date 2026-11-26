package _959.server_waypoint.perf.mods.fabric;

import _959.server_waypoint.perf.mods.ModPerformanceProbe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class FabricPerformanceProbe implements ModInitializer {
    private final ModPerformanceProbe probe = new ModPerformanceProbe("fabric", FabricPerformanceProbe.class);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> probe.register(dispatcher));
        ServerTickEvents.START_SERVER_TICK.register(probe::tickStart);
        ServerTickEvents.END_SERVER_TICK.register(probe::tickEnd);
    }
}
