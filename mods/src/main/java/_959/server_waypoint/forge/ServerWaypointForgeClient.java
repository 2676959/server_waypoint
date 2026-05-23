package _959.server_waypoint.forge;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.command.ClientWaypointCommand;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.handlers.S2CPayloadHandler;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.ServerHandshakeS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.UpdatesBundleS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import com.mojang.blaze3d.platform.InputConstants;
//? if >= 1.20.5
import io.netty.buffer.ByteBuf;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.FriendlyByteBuf;
//? if >= 1.20.5
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//? if < 1.20.5
/*import net.minecraftforge.client.event.RenderGuiEvent;*/
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
//? if < 1.21.6
/*import net.minecraftforge.eventbus.api.IEventBus;*/
import net.minecraftforge.fml.ModList;
//? if >= 1.21.6 && < 1.21.9
/*import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;*/
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
//? if <= 1.20.1 {
/*import net.minecraftforge.network.NetworkEvent;
*///?}
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class ServerWaypointForgeClient {
    private static KeyMapping keyBinding;
    private static boolean clientInitialized;

//? if < 1.21.6 {
    /*public static void initialize(IEventBus modEventBus) {
        modEventBus.addListener(ServerWaypointForgeClient::registerKeyBindings);
        MinecraftForge.EVENT_BUS.addListener(ServerWaypointForgeClient::registerClientCommands);
        MinecraftForge.EVENT_BUS.addListener(ServerWaypointForgeClient::onClientTick);
        //? if < 1.20.5
        /^MinecraftForge.EVENT_BUS.addListener(ServerWaypointForgeClient::onRenderGui);^/
    }
*///?} else {
    public static void initialize() {
        //? if >= 1.21.6 && < 1.21.9 {
        /*RegisterKeyMappingsEvent.getBus(FMLJavaModLoadingContext.get().getModBusGroup()).addListener(ServerWaypointForgeClient::registerKeyBindings);
        *///?} else {
        RegisterKeyMappingsEvent.BUS.addListener(ServerWaypointForgeClient::registerKeyBindings);
        //?}
        RegisterClientCommandsEvent.BUS.addListener(ServerWaypointForgeClient::registerClientCommands);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ServerWaypointForgeClient::onClientTick);
    }
    //?}

    private static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        keyBinding = new KeyMapping(
                "server_waypoint.waypoint_manager_gui.keybind",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                /*? if >= 1.21.9 {*/ KeyMapping.Category.register(_959.server_waypoint.common.util.ResourceLocationHelper.id("server_waypoint", "mod_name")) /*?} else {*/ /*"key.category.server_waypoint.mod_name" *//*?}*/
        );
        event.register(keyBinding);
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        ClientWaypointCommand.register(event.getDispatcher());
    }

//? if < 1.21.6 {
    /*private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ensureClientStarted();
        while (keyBinding != null && keyBinding.consumeClick()) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new WaypointManagerScreen(WaypointClientMod.getInstance()));
        }
    }
*///?} else {
    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        ensureClientStarted();
        while (keyBinding != null && keyBinding.consumeClick()) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new WaypointManagerScreen(WaypointClientMod.getInstance()));
        }
    }
    //?}

//? if < 1.20.5 {
    /*private static void onRenderGui(RenderGuiEvent.Pre event) {
        OptimizedWaypointRenderer.render(event.getGuiGraphics());
    }
*///?}

    private static void ensureClientStarted() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
        ClientConfig.isXaerosMinimapLoaded = ModList/*? if < 26 {*//*.get()*//*?}*/.isLoaded("xaerominimap");
        WaypointClientMod.createInstance(net.minecraft.client.Minecraft.getInstance(), FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
        OptimizedWaypointRenderer.init();
    }

    public static void registerClientPayloadHandlers() {
        S2CPayloadHandler.WaypointListHandler waypointListHandler = new S2CPayloadHandler.WaypointListHandler();
        S2CPayloadHandler.DimensionWaypointHandler dimensionWaypointHandler = new S2CPayloadHandler.DimensionWaypointHandler();
        S2CPayloadHandler.WorldWaypointHandler worldWaypointHandler = new S2CPayloadHandler.WorldWaypointHandler();
        S2CPayloadHandler.WaypointModificationHandler waypointModificationHandler = new S2CPayloadHandler.WaypointModificationHandler();
        S2CPayloadHandler.ServerHandshakeHandler serverHandshakeHandler = new S2CPayloadHandler.ServerHandshakeHandler();
        S2CPayloadHandler.UpdatesBundleHandler updatesBundleHandler = new S2CPayloadHandler.UpdatesBundleHandler();

        registerClientPayload(WaypointListS2CPayload.class, 0, /*? if >= 1.20.5 {*/ WaypointListS2CPayload.PACKET_CODEC /*?} else {*/ /*WaypointListS2CPayload::new *//*?}*/, waypointListHandler);
        registerClientPayload(DimensionWaypointS2CPayload.class, 1, /*? if >= 1.20.5 {*/ DimensionWaypointS2CPayload.PACKET_CODEC /*?} else {*/ /*DimensionWaypointS2CPayload::new *//*?}*/, dimensionWaypointHandler);
        registerClientPayload(WorldWaypointS2CPayload.class, 2, /*? if >= 1.20.5 {*/ WorldWaypointS2CPayload.PACKET_CODEC /*?} else {*/ /*WorldWaypointS2CPayload::new *//*?}*/, worldWaypointHandler);
        registerClientPayload(WaypointModificationS2CPayload.class, 3, /*? if >= 1.20.5 {*/ WaypointModificationS2CPayload.PACKET_CODEC /*?} else {*/ /*WaypointModificationS2CPayload::new *//*?}*/, waypointModificationHandler);
        registerClientPayload(UpdatesBundleS2CPayload.class, 4, /*? if >= 1.20.5 {*/ UpdatesBundleS2CPayload.PACKET_CODEC /*?} else {*/ /*UpdatesBundleS2CPayload::new *//*?}*/, updatesBundleHandler);
        registerClientPayload(ServerHandshakeS2CPayload.class, 5, /*? if >= 1.20.5 {*/ ServerHandshakeS2CPayload.PACKET_CODEC /*?} else {*/ /*ServerHandshakeS2CPayload::new *//*?}*/, serverHandshakeHandler);
    }

    private static <P extends ModPayload> void registerClientPayload(
            Class<P> payloadClass,
            int id,
            //? if >= 1.20.5 {
            StreamCodec<ByteBuf, P> codec,
            //?} else {
            /*Function<FriendlyByteBuf, P> decoder,
            *///?}
            S2CPayloadHandler.CustomPayloadHandler<?, P> handler
    ) {
//? if <= 1.20.1 {
        /*ServerWaypointForge.PACKET_CHANNEL.registerMessage(
                id,
                payloadClass,
                (payload, buf) -> payload.write(buf),
                decoder,
                (payload, context) -> {
                    context.get().enqueueWork(() -> handler.handle(payload, context));
                    context.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
*///?} else {
        ServerWaypointForge.PACKET_CHANNEL.messageBuilder(payloadClass, id, NetworkDirection.PLAY_TO_CLIENT)
                //? if >= 1.20.5 {
                .codec((StreamCodec) codec)
                //?} else {
                /*.encoder((payload, buf) -> payload.write(buf))
                .decoder(decoder)
                *///?}
                //? if >= 1.20.5 {
                .consumerMainThread((payload, context) -> handler.handle((P) payload, context))
                //?} else {
                /*.consumerMainThread(handler::handle)
                *///?}
                .add();
        //?}
    }
}
