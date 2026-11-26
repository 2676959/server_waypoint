package _959.server_waypoint.navigation;

import com.mojang.math.Transformation;
import _959.server_waypoint.util.NamespacedId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
//? if >=26.2
import net.minecraft.world.entity.EntityTypes;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
//? if >=1.21.2
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemDisplayContext;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;

import static _959.server_waypoint.navigation.PaperTextDisplayPackets.handle;

/** Independent, unmounted waypoint marker for Paper. */
public final class PaperWorldMarkerNavigationHandler
        extends AbstractWorldMarkerNavigationHandler<Player, Display> {
    private static final int LINE_WIDTH = 1000;

    @Override
    protected UUID playerUuid(Player player) {
        return player.getUniqueId();
    }

    @Override
    protected int playerEntityId(Player player) {
        return player.getEntityId();
    }

    @Override
    protected Object worldIdentity(Player player) {
        return handle(player).level();
    }

    @Override
    protected WorldMarkerPosition eyePosition(Player player) {
        ServerPlayer handle = handle(player);
        return new WorldMarkerPosition(handle.getX(), handle.getEyeY(), handle.getZ());
    }

    @Override
    protected long currentTick(Player player) {
        return handle(player).level().getGameTime();
    }

    @Override
    protected int latencyMillis(Player player) {
        return handle(player).connection.latency();
    }

    @Override
    protected Object textLocale(Player player) {
        return player.locale();
    }

    @Override
    protected void setPosition(Display display, WorldMarkerPosition position) {
        display.setPos(position.x(), position.y(), position.z());
    }

    @Override
    protected void sendPosition(Player player, Display display) {
        //? if >=1.21.2 {
        handle(player).connection.send(ClientboundTeleportEntityPacket.teleport(
                display.getId(), new PositionMoveRotation(display.position(), Vec3.ZERO, 0.0F, 0.0F),
                Set.of(), false));
        //?} else {
        /*handle(player).connection.send(new ClientboundTeleportEntityPacket(display));
        *///?}
    }

    @Override
    protected Display.TextDisplay createDisplay(Player player) {
        return PaperTextDisplayPackets.createDisplay(player);
    }

    @Override
    protected void configureBadge(Display display, int rgb, float size) {
        configureBase(display, 0.3F * size, size);
        Display.TextDisplay text = (Display.TextDisplay) display;
        text.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0x80000000 | rgb);
        text.getEntityData().set(Display.TextDisplay.DATA_LINE_WIDTH_ID, LINE_WIDTH);
        text.setFlags(Display.TextDisplay.FLAG_SEE_THROUGH);
    }

    @Override
    protected void configureDistance(Display display, float size) {
        configureBase(display, 0.0F, 0.8F * size);
        Display.TextDisplay text = (Display.TextDisplay) display;
        text.getEntityData().set(Display.TextDisplay.DATA_LINE_WIDTH_ID, LINE_WIDTH);
        text.setFlags((byte) (Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH
                | Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND));
    }

    @Override
    protected Display createItemDisplay(Player player, NamespacedId icon, float size) {
        //? if >=1.21.11 {
        var id = Identifier.tryParse(icon.toString());
        //?} else {
        /*var id = ResourceLocation.tryParse(icon.toString());
        *///?}
        var item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (item == Items.AIR) return null;
        //? if >=26.2 {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, handle(player).level());
        //?} else {
        /*Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, handle(player).level());
        *///?}
        this.configureItem(display, size);
        display.setItemTransform(ItemDisplayContext.GUI);
        display.getSlot(0).set(new ItemStack(item));
        return display;
    }

    @Override
    protected void configureItem(Display display, float size) {
        configureBase(display, 0.8F * size, 0.4F * size);
    }

    private static void configureBase(Display display, float offsetY, float size) {
        Vector3f translation = new Vector3f(0.0F, offsetY, 0.0F);
        Vector3f scale = new Vector3f(size);
        display.setTransformation(new Transformation(translation, new Quaternionf(), scale, new Quaternionf()));
        display.setBrightnessOverride(Brightness.FULL_BRIGHT);
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.getEntityData().set(Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID, 1);
    }


    @Override
    protected void sendSpawn(Player player, Display display) {
        PaperTextDisplayPackets.sendSpawn(player, display);
    }

    @Override
    protected void sendRemove(Player player, Display display) {
        PaperTextDisplayPackets.sendRemove(player, display);
    }

    @Override
    protected void setText(Player player, Display display, Component text) {
        PaperTextDisplayPackets.setText(player, (Display.TextDisplay) display, text);
    }

    @Override
    protected boolean sendEntityData(Player player, Display display) {
        return PaperTextDisplayPackets.sendEntityData(player, display);
    }
}
