//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.client.render.WaypointRenderer;
import _959.server_waypoint.common.client.gui.WaypointManagerScreen;
import _959.server_waypoint.common.network.ServerWaypointPayloadHandler;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;


public class ServerWaypointFabricClient implements ClientModInitializer {
    private static KeyBinding keyBinding;

    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
//        this.registerPayloadHandlers();

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.examplemod.spook", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_R, // The keycode of the key
                "category.examplemod.test" // The translation key of the keybinding's category.
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBinding.wasPressed()) {
                client.player.sendMessage(Text.literal("Key 1 was pressed!"), false);
                MinecraftClient.getInstance().setScreen(new WaypointManagerScreen(Text.of("test")));
            }
        });
    }

}
//?}