//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.WaypointManagerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

public class ServerWaypointFabricClient implements ClientModInitializer {
    private static KeyBinding keyBinding;

    @Override
    public void onInitializeClient() {
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.examplemod.spook", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_R, // The keycode of the key
                "category.examplemod.test" // The translation key of the keybinding's category.
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBinding.wasPressed()) {
                client.player.sendMessage(Text.literal("Key 1 was pressed!"), false);
                MinecraftClient.getInstance().setScreen(new WaypointManagerScreen(Text.of("test"), WaypointClientMod.getInstance()));
            }
        });
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((clientWorld, world) -> {

        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            WaypointClientMod.createInstance(client, FabricLoader.getInstance().getGameDir());
        });
    }
}
//?}