//? if neoforge {
/*package _959.server_waypoint.neoforge;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.command.ClientWaypointCommand;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.handlers.S2CPayloadHandler;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.network.payload.s2c.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
//? if >= 1.20.5 {
import net.neoforged.neoforge.client.event.ClientTickEvent;
//?} else {
/^import net.neoforged.neoforge.event.TickEvent;
^///?}
//? if <= 1.20.4 {
/^import net.neoforged.neoforge.client.event.RenderGuiEvent;
^///?}
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLPaths;
//? if >= 1.20.5 {
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//?} elif = 1.20.4 {
/^import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
^///?} elif = 1.20.2 {
/^import net.neoforged.neoforge.network.PlayNetworkDirection;
import net.neoforged.neoforge.network.simple.SimpleChannel;
^///?}
import org.lwjgl.glfw.GLFW;

import static _959.server_waypoint.common.util.ResourceLocationHelper.modId;

public class ServerWaypointNeoForgeClient {
    private static KeyMapping keyBinding;
    private static boolean clientInitialized;

    public static void initialize(IEventBus modEventBus) {
        modEventBus.addListener(ServerWaypointNeoForgeClient::registerKeyBindings);
        NeoForge.EVENT_BUS.addListener(ServerWaypointNeoForgeClient::registerClientCommands);
        NeoForge.EVENT_BUS.addListener(ServerWaypointNeoForgeClient::onClientTick);
//? if <= 1.20.4 {
        /^NeoForge.EVENT_BUS.addListener(ServerWaypointNeoForgeClient::onRenderGui);
^///?}
    }

    private static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        keyBinding = createKeyBinding();
        event.register(keyBinding);
    }

    private static KeyMapping createKeyBinding() {
        try {
            Class<?> categoryClass = Class.forName("net.minecraft.client.KeyMapping$Category");
            Object categoryId = modId("mod_name");
            Object category = categoryClass
                    .getMethod("register", categoryId.getClass())
                    .invoke(null, categoryId);
            return (KeyMapping) KeyMapping.class
                    .getConstructor(String.class, InputConstants.Type.class, int.class, categoryClass)
                    .newInstance("server_waypoint.waypoint_manager_gui.keybind", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category);
        } catch (ClassNotFoundException e) {
            try {
                return (KeyMapping) KeyMapping.class
                        .getConstructor(String.class, InputConstants.Type.class, int.class, String.class)
                        .newInstance("server_waypoint.waypoint_manager_gui.keybind", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.category.server_waypoint.mod_name");
            } catch (ReflectiveOperationException reflectiveException) {
                throw new IllegalStateException("Failed to create key binding", reflectiveException);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create key binding", e);
        }
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        ClientWaypointCommand.register(event.getDispatcher());
    }

//? if >= 1.20.5 {
    private static void onClientTick(ClientTickEvent.Post event) {
//?} else {
    /^private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
^///?}
        ensureClientStarted();
        WaypointClientMod.tickChunkedMessagesIfInitialized();
        while (keyBinding != null && keyBinding.consumeClick()) {
            MinecraftClientHelper.setScreen(new WaypointManagerScreen(WaypointClientMod.getInstance()));
        }
    }

//? if <= 1.20.4 {
    /^private static void onRenderGui(RenderGuiEvent.Pre event) {
        OptimizedWaypointRenderer.render(event.getGuiGraphics());
    }
^///?}

    private static void ensureClientStarted() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
        ClientConfig.isXaerosMinimapLoaded = ModList.get().isLoaded("xaerominimap");
        WaypointClientMod.createInstance(net.minecraft.client.Minecraft.getInstance(), FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
        OptimizedWaypointRenderer.init();
    }

//? if >= 1.20.5 {
    public static void registerClientPayloadHandlers(PayloadRegistrar registrar) {
        S2CPayloadHandler.MessageChunkHandler messageChunkHandler = new S2CPayloadHandler.MessageChunkHandler();
        S2CPayloadHandler.ServerHandshakeHandler serverHandshakeHandler = new S2CPayloadHandler.ServerHandshakeHandler();
        S2CPayloadHandler.UploadRequestHandler uploadRequestHandler = new S2CPayloadHandler.UploadRequestHandler();
        // S2C
        registrar.playToClient(MessageChunkS2CPayload.ID, MessageChunkS2CPayload.PACKET_CODEC, messageChunkHandler::handle);
        registrar.playToClient(ServerHandshakeS2CPayload.ID, ServerHandshakeS2CPayload.PACKET_CODEC, serverHandshakeHandler::handle);
        registrar.playToClient(UploadRequestS2CPayload.ID, UploadRequestS2CPayload.PACKET_CODEC, uploadRequestHandler::handle);
    }
//?} elif = 1.20.4 {
    /^public static void registerClientPayloadHandlers(IPayloadRegistrar registrar) {
        S2CPayloadHandler.MessageChunkHandler messageChunkHandler = new S2CPayloadHandler.MessageChunkHandler();
        S2CPayloadHandler.ServerHandshakeHandler serverHandshakeHandler = new S2CPayloadHandler.ServerHandshakeHandler();
        registrar.play(MessageChunkS2CPayload.MESSAGE_CHUNK_PAYLOAD_ID, MessageChunkS2CPayload::new, handler -> handler.client(messageChunkHandler::handle));
        registrar.play(ServerHandshakeS2CPayload.SERVER_HANDSHAKE_PAYLOAD, ServerHandshakeS2CPayload::new, handler -> handler.client(serverHandshakeHandler::handle));
        S2CPayloadHandler.UploadRequestHandler uploadRequestHandler = new S2CPayloadHandler.UploadRequestHandler();
        registrar.play(UploadRequestS2CPayload.UPLOAD_REQUEST_PAYLOAD_ID, UploadRequestS2CPayload::new, handler -> handler.client(uploadRequestHandler::handle));
    }
^///?} elif = 1.20.2 {
    /^public static void registerClientPayloadHandlers(SimpleChannel channel) {
        S2CPayloadHandler.MessageChunkHandler messageChunkHandler = new S2CPayloadHandler.MessageChunkHandler();
        S2CPayloadHandler.ServerHandshakeHandler serverHandshakeHandler = new S2CPayloadHandler.ServerHandshakeHandler();
        S2CPayloadHandler.UploadRequestHandler uploadRequestHandler = new S2CPayloadHandler.UploadRequestHandler();
        registerLegacyClientPayload(channel, MessageChunkS2CPayload.class, 0, MessageChunkS2CPayload::new, messageChunkHandler);
        registerLegacyClientPayload(channel, ServerHandshakeS2CPayload.class, 1, ServerHandshakeS2CPayload::new, serverHandshakeHandler);
        registerLegacyClientPayload(channel, UploadRequestS2CPayload.class, 2, UploadRequestS2CPayload::new, uploadRequestHandler);
    }

    private static <P extends _959.server_waypoint.common.network.payload.ModPayload> void registerLegacyClientPayload(
            SimpleChannel channel,
            Class<P> payloadClass,
            int id,
            java.util.function.Function<net.minecraft.network.FriendlyByteBuf, P> decoder,
            S2CPayloadHandler.CustomPayloadHandler<?, P> handler
    ) {
        channel.messageBuilder(payloadClass, id, PlayNetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buf) -> payload.write(buf))
                .decoder(decoder::apply)
                .consumerMainThread(handler::handle)
                .add();
    }
^///?}
}
*///?}
