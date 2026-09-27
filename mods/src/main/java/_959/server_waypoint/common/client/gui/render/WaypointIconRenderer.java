//~ gui_graphics_26
//~ resource_location_import
package _959.server_waypoint.common.client.gui.render;

import _959.server_waypoint.core.waypoint.WaypointIconPolicy;
import _959.server_waypoint.util.NamespacedId;
import _959.server_waypoint.common.util.ResourceLocationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.*;

/** Resolves icon resources on the client thread and draws their saved handles. */
public final class WaypointIconRenderer {
    public enum Kind { INITIALS, ITEM, VOXELMAP }

    public record ResolvedIcon(Kind kind, @Nullable NamespacedId id,
                               @Nullable ItemStack item, @Nullable
                               //$ resource_location_type_swap
                               Identifier
                               texture) {
    }

    private WaypointIconRenderer() {
    }

    public static Kind classify(@Nullable NamespacedId id, Predicate<NamespacedId> itemAvailable,
                                Predicate<NamespacedId> voxelMapImageAvailable) {
        if (id == null) return Kind.INITIALS;
        if (id.namespace().equals("voxelmap")) {
            return WaypointIconPolicy.isKnownVoxelMapIcon(id) && voxelMapImageAvailable.test(id)
                    ? Kind.VOXELMAP : Kind.INITIALS;
        }
        return itemAvailable.test(id) ? Kind.ITEM : Kind.INITIALS;
    }

    public static ResolvedIcon resolve(@Nullable NamespacedId id) {
        if (id == null) return new ResolvedIcon(Kind.INITIALS, null, null, null);
        if (id.namespace().equals("voxelmap")) {
            if (!WaypointIconPolicy.isKnownVoxelMapIcon(id)) {
                return new ResolvedIcon(Kind.INITIALS, id, null, null);
            }
            String suffix = id.path().equals("waypoint") ? "point" : id.path();
            //$ resource_location_type_swap
            Identifier
            texture = ResourceLocationHelper.mcId("voxelmap",
                    "images/waypoints/selectable/" + suffix + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()
                    ? new ResolvedIcon(Kind.VOXELMAP, id, null, texture)
                    : new ResolvedIcon(Kind.INITIALS, id, null, null);
        }
        //$ resource_location_type_swap
        Identifier
        itemId = ResourceLocationHelper.mcId(id.namespace(), id.path());
        var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
        return item == Items.AIR
                ? new ResolvedIcon(Kind.INITIALS, id, null, null)
                : new ResolvedIcon(Kind.ITEM, id, new ItemStack(item), null);
    }

    public static void draw(GuiGraphicsExtractor context, ResolvedIcon icon, int x, int y, int size) {
        if (icon.kind() == Kind.ITEM && icon.item() != null) {
            push(context);
            translate(context, x, y);
            scale(context, size / 16.0F, size / 16.0F);
            drawItem(context, icon.item(), 0, 0);
            pop(context);
        } else if (icon.kind() == Kind.VOXELMAP && icon.texture() != null) {
            push(context);
            translate(context, x, y);
            scale(context, size / 32.0F, size / 32.0F);
            texture(context, icon.texture(), 0, 0, 0, 0, 32, 32, 32, 32);
            pop(context);
        }
    }

    public static void drawScaled(GuiGraphicsExtractor context, ResolvedIcon icon,
                                  float left, float top, float scale) {
        push(context);
        translate(context, left, top);
        scale(context, scale, scale);
        draw(context, icon, 0, 0, 16);
        pop(context);
    }
}
