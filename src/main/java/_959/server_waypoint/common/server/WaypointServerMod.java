package _959.server_waypoint.common.server;

import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.waypoint.DimensionWaypoint;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import _959.server_waypoint.core.waypoint.WaypointModificationType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static _959.server_waypoint.common.util.CommandGenerator.tpCmd;
import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;
import static _959.server_waypoint.common.util.DimensionFileHelper.getFileName;
import static _959.server_waypoint.common.util.SimpleWaypointHelper.DEFAULT_STYLE;
import static _959.server_waypoint.common.util.SimpleWaypointHelper.simpleWaypointToFormattedText;
import static _959.server_waypoint.common.util.TextHelper.text;
import static _959.server_waypoint.common.util.TextHelper.waypointInfoText;

public class WaypointServerMod extends WaypointServerCore {
    public MinecraftServer MINECRAFT_SERVER;
    public static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_mod");
    public static WaypointServerMod INSTANCE;

    public WaypointServerMod(Path configDir) {
        super(configDir, WaypointServerMod::isFileNameValid);
        INSTANCE = this;
    }

    private static boolean isFileNameValid(String fileName) {
        return getDimensionKey(fileName) != null;
    }

    public @Nullable WorldWaypointS2CPayload toWorldWaypointPayload() {
        List<DimensionWaypoint> dimensionWaypoints = new ArrayList<>();

        for(WaypointFileManager fileManager : this.getFileManagerMap().values()) {
            if (fileManager != null) {
                dimensionWaypoints.add(fileManager.toDimensionWaypoint());
            }
        }

        if (dimensionWaypoints.isEmpty()) {
            return null;
        } else {
            return new WorldWaypointS2CPayload(new WorldWaypointBuffer(dimensionWaypoints, EDITION));
        }
    }

    public void broadcastWaypointModification(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint, WaypointModificationType type, @Nullable PlayerEntity source) {
        WaypointModificationS2CPayload payload = new WaypointModificationS2CPayload(new WaypointModificationBuffer(getFileName(dimKey), listName, waypoint, type, EDITION));
        this.MINECRAFT_SERVER.getPlayerManager().getPlayerList().forEach(player -> {
            player.sendMessage(
                    text((source != null ? source.getName().getString() : "Server") + " " + type.toVerb() + " waypoint: ")
                            .append(simpleWaypointToFormattedText(waypoint, tpCmd(dimKey, waypoint.pos(), waypoint.yaw()), waypointInfoText(dimKey, waypoint))
                                    .append(text(" on server.").setStyle(DEFAULT_STYLE)))
            );
            //? if fabric {
            ServerPlayNetworking.send(player, payload);
            //?} else {
            /*PacketDistributor.sendToPlayer(player, payload);
             *///?}
        });
    }

    public void setMinecraftServer(MinecraftServer server) {
        if (this.MINECRAFT_SERVER == null) {
            this.MINECRAFT_SERVER = server;
        }
    }
}
