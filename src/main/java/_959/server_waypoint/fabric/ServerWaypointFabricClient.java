//? if fabric {
package _959.server_waypoint.fabric;

import _959.server_waypoint.common.client.WaypointClient;
import _959.server_waypoint.common.client.render.WaypointRenderer;
import _959.server_waypoint.common.client.gui.WaypointManagerScreen;
import _959.server_waypoint.common.network.ServerWaypointPayloadHandler;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.google.common.util.concurrent.AtomicDouble;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4d;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;


public class ServerWaypointFabricClient implements ClientModInitializer {
    private static KeyBinding keyBinding;

    @Override
    public void onInitializeClient() {
        new WaypointClient();
        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.examplemod.spook", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_R, // The keycode of the key
                "category.examplemod.test" // The translation key of the keybinding's category.
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBinding.wasPressed()) {
                client.player.sendMessage(Text.literal("Key 1 was pressed!"), false);
                MinecraftClient.getInstance().setScreen(new WaypointManagerScreen(Text.of("test"), WaypointClient.getInstance()));
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            WaypointClient.getInstance().setMinecraftClient(client);
        });
    }

}
//?}