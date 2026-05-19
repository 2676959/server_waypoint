//? if neoforge {
package _959.server_waypoint.neoforge;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.command.ClientWaypointCommand;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.handlers.S2CPayloadHandler;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.common.util.ResourceLocationHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.lwjgl.glfw.GLFW;

public class ServerWaypointNeoForgeClient {
    private static KeyMapping keyBinding;
    private static boolean clientInitialized;

    public static void initialize(IEventBus modEventBus) {
        modEventBus.addListener(ServerWaypointNeoForgeClient::registerKeyBindings);
        NeoForge.EVENT_BUS.addListener(ServerWaypointNeoForgeClient::registerClientCommands);
        NeoForge.EVENT_BUS.addListener(ServerWaypointNeoForgeClient::onClientTick);
    }

    private static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        keyBinding = createKeyBinding();
        event.register(keyBinding);
    }

    private static KeyMapping createKeyBinding() {
        try {
            Class<?> categoryClass = Class.forName("net.minecraft.client.KeyMapping$Category");
            Object categoryId = ResourceLocationHelper.id("server_waypoint", "mod_name");
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
                        .newInstance("server_waypoint.waypoint_manager_gui.keybind", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.server_waypoint.mod_name");
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

    private static void onClientTick(ClientTickEvent.Post event) {
        ensureClientStarted();
        while (keyBinding != null && keyBinding.consumeClick()) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new WaypointManagerScreen(WaypointClientMod.getInstance()));
        }
    }

    private static void ensureClientStarted() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
        ClientConfig.isXaerosMinimapLoaded = ModList.get().isLoaded("xaerominimap");
        WaypointClientMod.createInstance(net.minecraft.client.Minecraft.getInstance(), FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
        OptimizedWaypointRenderer.init();
    }

    public static void registerClientPayloadHandlers(PayloadRegistrar registrar) {
        S2CPayloadHandler.WaypointListHandler waypointListHandler = new S2CPayloadHandler.WaypointListHandler();
        S2CPayloadHandler.DimensionWaypointHandler dimensionWaypointHandler = new S2CPayloadHandler.DimensionWaypointHandler();
        S2CPayloadHandler.WorldWaypointHandler worldWaypointHandler = new S2CPayloadHandler.WorldWaypointHandler();
        S2CPayloadHandler.WaypointModificationHandler waypointModificationHandler = new S2CPayloadHandler.WaypointModificationHandler();
        S2CPayloadHandler.ServerHandshakeHandler serverHandshakeHandler = new S2CPayloadHandler.ServerHandshakeHandler();
        S2CPayloadHandler.UpdatesBundleHandler updatesBundleHandler = new S2CPayloadHandler.UpdatesBundleHandler();
        // S2C
        registrar.playToClient(WaypointListS2CPayload.ID, WaypointListS2CPayload.PACKET_CODEC, waypointListHandler::handle);
        registrar.playToClient(DimensionWaypointS2CPayload.ID, DimensionWaypointS2CPayload.PACKET_CODEC, dimensionWaypointHandler::handle);
        registrar.playToClient(WorldWaypointS2CPayload.ID, WorldWaypointS2CPayload.PACKET_CODEC, worldWaypointHandler::handle);
        registrar.playToClient(WaypointModificationS2CPayload.ID, WaypointModificationS2CPayload.PACKET_CODEC, waypointModificationHandler::handle);
        registrar.playToClient(ServerHandshakeS2CPayload.ID, ServerHandshakeS2CPayload.PACKET_CODEC, serverHandshakeHandler::handle);
        registrar.playToClient(UpdatesBundleS2CPayload.ID, UpdatesBundleS2CPayload.PACKET_CODEC, updatesBundleHandler::handle);
    }
}
//?}
