//~ resource_location_import
package _959.server_waypoint.common.util;

import net.minecraft.resources.Identifier;

public final class ResourceLocationHelper {
    private ResourceLocationHelper() {
    }

    public static
    //$ resource_location_type_swap
    Identifier
    id(String namespace, String path) {
        //? if >= 1.21.11 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} elif >= 1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }
}
