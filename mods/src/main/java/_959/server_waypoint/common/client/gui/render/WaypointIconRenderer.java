//~ gui_graphics_26
//~ resource_location_import
package _959.server_waypoint.common.client.gui.render;

import _959.server_waypoint.core.waypoint.WaypointIconPolicy;
import _959.server_waypoint.common.client.render.WaypointItemAlpha;
import _959.server_waypoint.util.NamespacedId;
import _959.server_waypoint.common.util.ResourceLocationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
//? if >= 1.21.6
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
//? if < 1.21.6
/*import com.mojang.blaze3d.systems.RenderSystem;*/

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
            String suffix = id.path();
            //? if >=1.21.11 {
            String path = "images/waypoints/selectable/" + (suffix.equals("waypoint") ? "point" : suffix) + ".png";
            //?} else {
            /*String path = "images/waypoints/waypoint"
                    + (suffix.equals("waypoint") || suffix.equals("point") ? "" : suffix) + ".png";
            *///?}
            //$ resource_location_type_swap
            Identifier
            texture = ResourceLocationHelper.mcId("voxelmap", path);
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
            //? if <= 1.21 {
            /*// The immediate texture blit does not enable blending for texture or shader alpha.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            *///?}
            try {
                texture(context, icon.texture(), 0, 0, 0, 0, 32, 32, 32, 32);
            } finally {
                //? if <= 1.21
                /*RenderSystem.disableBlend();*/
                pop(context);
            }
        }
    }

    /** Draws a saved waypoint's icon, tinting VoxelMap images with its RGB color. */
    public static void drawForWaypoint(GuiGraphicsExtractor context, ResolvedIcon icon,
                                       int x, int y, int size, int waypointColor) {
        if (icon.kind() == Kind.VOXELMAP) {
            drawScaledVoxelMap(context, icon, x, y, size / 16.0F, 255, waypointColor);
        } else {
            draw(context, icon, x, y, size);
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

    /** Draws only the in-world item icon with the marker background alpha. */
    public static void drawScaledWorldItem(GuiGraphicsExtractor context, ResolvedIcon icon,
                                           float left, float top, float scale, int alpha) {
        //? if >= 1.21.6 {
        int previous = WaypointItemAlpha.pushWorldItemTint(alpha);
        try {
            drawScaled(context, icon, left, top, scale);
        } finally {
            WaypointItemAlpha.restoreTint(previous);
        }
        //?} else {
        /*int previousTint = WaypointItemAlpha.pushWorldItemTint(alpha);
        float[] previous = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(previous[0], previous[1], previous[2],
                previous[3] * (alpha / 255.0F));
        try {
            drawScaled(context, icon, left, top, scale);
        } finally {
            RenderSystem.setShaderColor(previous[0], previous[1], previous[2], previous[3]);
            WaypointItemAlpha.restoreTint(previousTint);
        }
        *///?}
    }

    /** Draws a VoxelMap image with the waypoint RGB and a caller-selected alpha. */
    public static void drawScaledVoxelMap(GuiGraphicsExtractor context, ResolvedIcon icon,
                                          float left, float top, float scale, int alpha, int waypointColor) {
        if (icon.kind() != Kind.VOXELMAP || icon.texture() == null) return;
        //? if >= 1.21.6 {
        push(context);
        translate(context, left, top);
        scale(context, scale / 2.0F, scale / 2.0F);
        context.blit(RenderPipelines.GUI_TEXTURED, icon.texture(),
                0, 0, 0, 0, 32, 32, 32, 32, (alpha << 24) | (waypointColor & 0x00FFFFFF));
        pop(context);
        //?} else {
        /*context.flush();
        float[] previous = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(previous[0] * ((waypointColor >> 16) & 0xFF) / 255.0F,
                previous[1] * ((waypointColor >> 8) & 0xFF) / 255.0F,
                previous[2] * (waypointColor & 0xFF) / 255.0F,
                previous[3] * (alpha / 255.0F));
        try {
            drawScaled(context, icon, left, top, scale);
            context.flush();
        } finally {
            RenderSystem.setShaderColor(previous[0], previous[1], previous[2], previous[3]);
        }
        *///?}
    }
}
