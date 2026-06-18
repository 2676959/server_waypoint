package _959.server_waypoint.common.client.util;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

public final class MinecraftClientHelper {
    private MinecraftClientHelper() {
    }

    public static void setScreen(@Nullable Screen screen) {
        setScreen(Minecraft.getInstance(), screen);
    }

    public static void setScreen(Minecraft minecraft, @Nullable Screen screen) {
        //? if >= 26.2 {
        minecraft.gui.setScreen(screen);
        //?} else {
        /*minecraft.setScreen(screen);
        *///?}
    }

    public static Camera getMainCamera() {
        return getMainCamera(Minecraft.getInstance());
    }

    public static Camera getMainCamera(Minecraft minecraft) {
        //? if >= 26.2 {
        return minecraft.gameRenderer.mainCamera();
        //?} else {
        /*return minecraft.gameRenderer.getMainCamera();
        *///?}
    }
}
