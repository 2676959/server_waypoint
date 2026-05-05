package _959.server_waypoint.common.util;

import net.minecraft.resources.Identifier;

public final class ResourceLocationHelper {
    private ResourceLocationHelper() {
    }

    public static Identifier id(String namespace, String path) {
        //? if > 1.20.6 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new Identifier(namespace, path);
        *///?}
    }
}
