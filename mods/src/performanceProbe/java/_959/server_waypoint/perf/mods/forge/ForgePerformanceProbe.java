package _959.server_waypoint.perf.mods.forge;

import _959.server_waypoint.perf.mods.ModPerformanceProbe;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("server_waypoint_performance_probe")
public final class ForgePerformanceProbe {
    private final ModPerformanceProbe probe = new ModPerformanceProbe("forge", ForgePerformanceProbe.class);

    public ForgePerformanceProbe() {
        RegisterCommandsEvent.BUS.addListener(this::registerCommands);
        TickEvent.ServerTickEvent.Pre.BUS.addListener(this::tickStart);
        TickEvent.ServerTickEvent.Post.BUS.addListener(this::tickEnd);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        probe.register(event.getDispatcher());
    }

    private void tickStart(TickEvent.ServerTickEvent.Pre event) {
        probe.tickStart(event.server());
    }

    private void tickEnd(TickEvent.ServerTickEvent.Post event) {
        probe.tickEnd(event.server());
    }
}
