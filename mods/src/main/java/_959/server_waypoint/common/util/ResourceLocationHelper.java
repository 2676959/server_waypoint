//~ resource_location_import
package _959.server_waypoint.common.util;

import net.minecraft.resources.Identifier;

import static _959.server_waypoint.ModInfo.MOD_ID;

public final class ResourceLocationHelper {
    private ResourceLocationHelper() {
    }

    public static
    //$ resource_location_type_swap
    Identifier
    mcId(String namespace, String path) {
        //? if >= 1.21.11 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} elif >= 1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }

    public static
    //$ resource_location_type_swap
    Identifier
    modId(String path) {
        return mcId(MOD_ID, path);
    }

    public static
        //$ resource_location_type_swap
    Identifier
    vanillaId(String namespace, String path) {
        //? if >= 1.21.11 {
        return Identifier.fromNamespaceAndPath("minecraft", path);
        //?} elif >= 1.21 {
        /*return ResourceLocation.fromNamespaceAndPath("minecraft", path);
         *///?} else {
        /*return new ResourceLocation("minecraft", path);
         *///?}
    }
}
