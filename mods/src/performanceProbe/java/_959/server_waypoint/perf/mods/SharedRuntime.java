package _959.server_waypoint.perf.mods;

import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;

final class SharedRuntime {
    private SharedRuntime() {
    }

    static String describe(MinecraftServer server) {
        return server.getServerModName() + " " + SharedConstants.getCurrentVersion().name()
                + " dataVersion=" + SharedConstants.getCurrentVersion().dataVersion().version();
    }

    static int dataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }
}
