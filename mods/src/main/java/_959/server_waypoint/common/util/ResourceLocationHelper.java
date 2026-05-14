package _959.server_waypoint.common.util;

//? if >= 1.21.11
import net.minecraft.resources.Identifier;
//? if < 1.21.11
/*import net.minecraft.resources.ResourceLocation;*/

public final class ResourceLocationHelper {
    private ResourceLocationHelper() {
    }

    public static /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ id(String namespace, String path) {
        //? if >= 1.21.11 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} elif >= 1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }
}
