//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.command.ClientWaypointCommand;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.handlers.S2CPayloadHandler;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.client.util.ClientCommandUtils;
import _959.server_waypoint.common.network.payload.s2c.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ServerWaypointFabricClient implements ClientModInitializer {
    private static KeyBinding keyBinding;

    @Override
    public void onInitializeClient() {
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "server_waypoint.waypoint_manager_gui.keybind",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "server_waypoint.mod_name"
        ));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                ClientWaypointCommand.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBinding.wasPressed()) {
                MinecraftClient.getInstance().setScreen(new WaypointManagerScreen(WaypointClientMod.getInstance()));
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            WaypointClientMod.createInstance(client, FabricLoader.getInstance().getGameDir(), FabricLoader.getInstance().getConfigDir());
            OptimizedWaypointRenderer.init();
        });
        registerClientHandlers();
    }

    private void registerClientHandlers() {
        //? if >= 1.20.5 {
        S2CPayloadHandler.WaypointListHandler waypointListHandler = new S2CPayloadHandler.WaypointListHandler();
        S2CPayloadHandler.DimensionWaypointHandler dimensionWaypointHandler = new S2CPayloadHandler.DimensionWaypointHandler();
        S2CPayloadHandler.WorldWaypointHandler worldWaypointHandler = new S2CPayloadHandler.WorldWaypointHandler();
        S2CPayloadHandler.WaypointModificationHandler waypointModificationHandler = new S2CPayloadHandler.WaypointModificationHandler();
        S2CPayloadHandler.ServerHandshakeHandler serverHandshakeHandler = new S2CPayloadHandler.ServerHandshakeHandler();
        S2CPayloadHandler.UpdatesBundleHandler updatesBundleHandler = new S2CPayloadHandler.UpdatesBundleHandler();

        ClientPlayNetworking.registerGlobalReceiver(WaypointListS2CPayload.ID, waypointListHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(DimensionWaypointS2CPayload.ID, dimensionWaypointHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(WorldWaypointS2CPayload.ID, worldWaypointHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(WaypointModificationS2CPayload.ID, waypointModificationHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(ServerHandshakeS2CPayload.ID, serverHandshakeHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(UpdatesBundleS2CPayload.ID, updatesBundleHandler::handle);
        //?} else if fabric {
        /*ClientPlayNetworking.registerGlobalReceiver(WaypointListS2CPayload.TYPE, (packet, player, responseSender) ->
                ServerWaypointPayloadHandler.onWaypointListPayload(packet, player));
        ClientPlayNetworking.registerGlobalReceiver(DimensionWaypointS2CPayload.TYPE, (packet, player, responseSender) ->
                ServerWaypointPayloadHandler.onDimensionWaypointPayload(packet, player));
        ClientPlayNetworking.registerGlobalReceiver(WorldWaypointS2CPayload.TYPE, (packet, player, responseSender) ->
                ServerWaypointPayloadHandler.onWorldWaypointPayload(packet, player));
        ClientPlayNetworking.registerGlobalReceiver(WaypointModificationS2CPayload.TYPE, (packet, player, responseSender) ->
                ServerWaypointPayloadHandler.onWaypointModificationPayload(packet, player));
        *///?}
    }
}
//?}