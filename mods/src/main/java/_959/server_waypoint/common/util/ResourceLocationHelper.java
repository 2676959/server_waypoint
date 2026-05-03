package _959.server_waypoint.common.util;

import net.minecraft.resources.ResourceLocation;

public final class ResourceLocationHelper {
    private ResourceLocationHelper() {
    }

    public static ResourceLocation id(String namespace, String path) {
        //? if > 1.20.6 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        return new ResourceLocation(namespace, path);
        //?}
    }
}
