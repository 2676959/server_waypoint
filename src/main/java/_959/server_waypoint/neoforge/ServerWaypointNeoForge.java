//? if neoforge {
/*package _959.server_waypoint.neoforge;

import _959.server_waypoint.common.ServerWaypointMod;
import _959.server_waypoint.common.network.ChatMessageHandler;
import _959.server_waypoint.common.network.ClientHandshakeHandler;
import _959.server_waypoint.common.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.common.permission.PermissionKey;
import _959.server_waypoint.common.server.command.WaypointCommand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

import java.nio.file.Path;

@Mod(value = ServerWaypointNeoForge.MOD_ID, dist = Dist.DEDICATED_SERVER)
@EventBusSubscriber(modid = ServerWaypointNeoForge.MOD_ID, value = Dist.DEDICATED_SERVER)
public class ServerWaypointNeoForge extends ServerWaypointMod {
    public ServerWaypointNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::initWaypointServer);
    }

    private void initWaypointServer(FMLDedicatedServerSetupEvent event) {
        initServer();

    }

    @SubscribeEvent
    public static void registerPermissionNodes(PermissionGatherEvent.Nodes event) {
        for (PermissionKey permissionKey: PermissionKey.values()) {
            event.addNodes(permissionKey.getNode());
        }
    }

    @SubscribeEvent
    public static void listenChatMessages(ServerChatEvent event) {
        ChatMessageHandler.onChatMessage(event.getMessage(), event.getPlayer(), null);
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        // S2C
        registrar.playToClient(SimpleWaypointS2CPayload.ID, SimpleWaypointS2CPayload.PACKET_CODEC, (payload, context) -> {});
        registrar.playToClient(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC, (payload, context) -> {});
        registrar.playToClient(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC, (payload, context) -> {});
        registrar.playToClient(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC, (payload, context) -> {});
        registrar.playToClient(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC, (payload, context) -> {});
        // C2S
        registrar.playToServer(HandshakeC2SPayload.ID, HandshakeC2SPayload.PACKET_CODEC, ClientHandshakeHandler::onClientHandshake);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        WaypointCommand.register(event.getDispatcher());
    }

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath());
    }
}
*///?}