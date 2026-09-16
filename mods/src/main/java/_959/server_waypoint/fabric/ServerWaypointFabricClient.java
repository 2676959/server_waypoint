//? if fabric {
//~ fabric_key_mapping_import_26
//~ fabric_key_mapping_call_26
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.command.ClientWaypointCommand;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.handlers.S2CPayloadHandler;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.network.payload.s2c.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import static _959.server_waypoint.common.util.ResourceLocationHelper.modId;

public class ServerWaypointFabricClient implements ClientModInitializer {
    private static KeyMapping keyBinding;

    @Override
    public void onInitializeClient() {
        ClientConfig.isXaerosMinimapLoaded = FabricLoader.getInstance().isModLoaded("xaerominimap");
        ClientConfig.isVoxelMapLoaded = FabricLoader.getInstance().isModLoaded("voxelmap");
        keyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "server_waypoint.waypoint_manager_gui.keybind",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                //? if >= 1.21.9 {
                KeyMapping.Category.register(modId("mod_name"))
                //?} else {
                /*"key.categories.server_waypoint.mod_name"
                *///?}
        ));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                ClientWaypointCommand.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WaypointClientMod.tickChunkedMessagesIfInitialized();
            while (keyBinding.consumeClick()) {
                MinecraftClientHelper.setScreen(client, new WaypointManagerScreen(WaypointClientMod.getInstance()));
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            WaypointClientMod.createInstance(client, FabricLoader.getInstance().getGameDir(), FabricLoader.getInstance().getConfigDir());
            OptimizedWaypointRenderer.init();
        });
        registerClientHandlers();
    }

    private void registerClientHandlers() {
        S2CPayloadHandler.MessageChunkHandler messageChunkHandler = new S2CPayloadHandler.MessageChunkHandler();
        S2CPayloadHandler.ServerHandshakeHandler serverHandshakeHandler = new S2CPayloadHandler.ServerHandshakeHandler();
        S2CPayloadHandler.UploadRequestHandler uploadRequestHandler = new S2CPayloadHandler.UploadRequestHandler();
        ClientPlayNetworking.registerGlobalReceiver(MessageChunkS2CPayload.ID, messageChunkHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(ServerHandshakeS2CPayload.ID, serverHandshakeHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(UploadRequestS2CPayload.ID, uploadRequestHandler::handle);
    }
}
//?}
