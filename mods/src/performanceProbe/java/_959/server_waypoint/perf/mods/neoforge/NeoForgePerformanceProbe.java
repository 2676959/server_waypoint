package _959.server_waypoint.perf.mods.neoforge;

import _959.server_waypoint.perf.mods.ModPerformanceProbe;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("server_waypoint_performance_probe")
public final class NeoForgePerformanceProbe {
    private final ModPerformanceProbe probe = new ModPerformanceProbe("neoforge", NeoForgePerformanceProbe.class);

    public NeoForgePerformanceProbe() {
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::tickStart);
        NeoForge.EVENT_BUS.addListener(this::tickEnd);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        probe.register(event.getDispatcher());
    }

    private void tickStart(ServerTickEvent.Pre event) {
        probe.tickStart(event.getServer());
    }

    private void tickEnd(ServerTickEvent.Post event) {
        probe.tickEnd(event.getServer());
    }
}
