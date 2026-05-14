//? if neoforge {
//package _959.server_waypoint.neoforge;
//
//import _959.server_waypoint.ModInfo;
//import _959.server_waypoint.common.client.ClientConfig;
//import _959.server_waypoint.common.client.WaypointClientMod;
//import _959.server_waypoint.common.client.command.ClientWaypointCommand;
//import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
//import _959.server_waypoint.common.client.handlers.S2CPayloadHandler;
//import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
//import _959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload;
//import _959.server_waypoint.common.network.payload.c2s.UpdateRequestC2SPayload;
//import _959.server_waypoint.common.network.payload.s2c.*;
//import com.mojang.blaze3d.platform.InputConstants;
//import net.minecraft.client.KeyMapping;
//import net.neoforged.bus.api.IEventBus;
//import net.neoforged.api.distmarker.Dist;
//import net.neoforged.neoforge.client.event.ClientTickEvent;
//import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
//import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
//import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
//import net.neoforged.neoforge.common.NeoForge;
//import net.neoforged.fml.loading.FMLPaths;
//import net.neoforged.fml.common.EventBusSubscriber;
//import net.neoforged.fml.common.Mod;
//import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
//import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//import org.lwjgl.glfw.GLFW;
//
//@Mod(value = ModInfo.MOD_ID, dist = Dist.CLIENT)
//@EventBusSubscriber(modid = ModInfo.MOD_ID, value = Dist.CLIENT)
//public class ServerWaypointNeoForgeClient {
//    private static KeyMapping keyBinding;
//
//    public ServerWaypointNeoForgeClient(IEventBus modEventBus) {
//        modEventBus.addListener(this::registerPayloads);
//        modEventBus.addListener(this::registerKeyMappings);
//        NeoForge.EVENT_BUS.addListener(this::registerClientCommands);
//        NeoForge.EVENT_BUS.addListener(this::onClientStarted);
//        NeoForge.EVENT_BUS.addListener(this::onClientTick);
//    }
//
//    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
//        keyBinding = new KeyMapping(
//                "server_waypoint.waypoint_manager_gui.keybind",
//                InputConstants.Type.KEYSYM,
//                GLFW.GLFW_KEY_RIGHT_SHIFT,
//                "key.categories.server_waypoint.mod_name"
//        );
//        event.register(keyBinding);
//    }
//
//    private void registerClientCommands(RegisterClientCommandsEvent event) {
//        ClientWaypointCommand.register(event.getDispatcher());
//    }
//
//    private void onClientStarted(ClientStartedEvent event) {
//        ClientConfig.isXaerosMinimapLoaded = true;
//        WaypointClientMod.createInstance(event.getClient(), FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
//        OptimizedWaypointRenderer.init();
//    }
//
//    private void onClientTick(ClientTickEvent.Post event) {
//        while (keyBinding != null && keyBinding.consumeClick()) {
//            net.minecraft.client.Minecraft.getInstance().setScreen(new WaypointManagerScreen(WaypointClientMod.getInstance()));
//        }
//    }
//
//    private void registerPayloads(RegisterPayloadHandlersEvent event) {
//        final PayloadRegistrar registrar = event.registrar("1");
//        S2CPayloadHandler.WaypointListHandler waypointListHandler = new S2CPayloadHandler.WaypointListHandler();
//        S2CPayloadHandler.DimensionWaypointHandler dimensionWaypointHandler = new S2CPayloadHandler.DimensionWaypointHandler();
//        S2CPayloadHandler.WorldWaypointHandler worldWaypointHandler = new S2CPayloadHandler.WorldWaypointHandler();
//        S2CPayloadHandler.WaypointModificationHandler waypointModificationHandler = new S2CPayloadHandler.WaypointModificationHandler();
//        S2CPayloadHandler.ServerHandshakeHandler serverHandshakeHandler = new S2CPayloadHandler.ServerHandshakeHandler();
//        S2CPayloadHandler.UpdatesBundleHandler updatesBundleHandler = new S2CPayloadHandler.UpdatesBundleHandler();
//        // S2C
//        registrar.playToClient(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC, waypointListHandler::handle);
//        registrar.playToClient(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC, dimensionWaypointHandler::handle);
//        registrar.playToClient(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC, worldWaypointHandler::handle);
//        registrar.playToClient(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC, waypointModificationHandler::handle);
//        registrar.playToClient(ServerHandshakeS2CPayload.ID, ServerHandshakeS2CPayload.PACKET_CODEC, serverHandshakeHandler::handle);
//        registrar.playToClient(UpdatesBundleS2CPayload.ID, UpdatesBundleS2CPayload.PACKET_CODEC, updatesBundleHandler::handle);
//        registrar.playToClient(XaerosWorldIdS2CPayload.ID, XaerosWorldIdS2CPayload.PACKET_CODEC, (payload, context) -> {});
//        // C2S
//        registrar.playToServer(ClientHandshakeC2SPayload.ID, ClientHandshakeC2SPayload.PACKET_CODEC, (payload, context) -> {});
//        registrar.playToServer(UpdateRequestC2SPayload.ID, UpdateRequestC2SPayload.PACKET_CODEC, (payload, context) -> {});
//    }
//}
//?}
