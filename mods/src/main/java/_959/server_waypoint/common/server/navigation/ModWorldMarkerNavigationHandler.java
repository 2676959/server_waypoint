package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.util.PlayerLocaleHelper;
import _959.server_waypoint.common.util.ResourceLocationHelper;
import _959.server_waypoint.navigation.AbstractWorldMarkerNavigationHandler;
import _959.server_waypoint.navigation.WorldMarkerPosition;
import _959.server_waypoint.util.NamespacedId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
//? if >=26.2
/*import net.minecraft.world.entity.EntityTypes;*/
import net.kyori.adventure.text.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
//? if >=1.21.2
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;

/** Independent, unmounted waypoint marker for the modded server platforms. */
final class ModWorldMarkerNavigationHandler
        extends AbstractWorldMarkerNavigationHandler<ServerPlayer, Display> {
    private static final int LINE_WIDTH = 1000;

    @Override
    protected UUID playerUuid(ServerPlayer player) {
        return player.getUUID();
    }

    @Override
    protected int playerEntityId(ServerPlayer player) {
        return player.getId();
    }

    @Override
    protected Object worldIdentity(ServerPlayer player) {
        return player.level();
    }

    @Override
    protected WorldMarkerPosition eyePosition(ServerPlayer player) {
        return new WorldMarkerPosition(player.getX(), player.getEyeY(), player.getZ());
    }

    @Override
    protected long currentTick(ServerPlayer player) {
        return player.level().getGameTime();
    }

    @Override
    protected int latencyMillis(ServerPlayer player) {
        //? if >=1.20.2 {
        return player.connection.latency();
        //?} else {
        /*return player.latency;
        *///?}
    }

    @Override
    protected Object textLocale(ServerPlayer player) {
        return PlayerLocaleHelper.forPlayer(player);
    }

    @Override
    protected void setPosition(Display display, WorldMarkerPosition position) {
        display.setPos(position.x(), position.y(), position.z());
    }

    @Override
    protected void sendPosition(ServerPlayer player, Display display) {
        //? if >=1.21.2 {
        player.connection.send(ClientboundTeleportEntityPacket.teleport(
                display.getId(), new PositionMoveRotation(display.position(), Vec3.ZERO, 0.0F, 0.0F),
                Set.of(), false));
        //?} else {
        /*player.connection.send(new ClientboundTeleportEntityPacket(display));
        *///?}
    }

    @Override
    protected Display.TextDisplay createDisplay(ServerPlayer player) {
        return ModTextDisplayPackets.createDisplay(player);
    }

    @Override
    protected void configureBadge(Display display, int rgb, float size) {
        CompoundTag tag = configuredData(display, 0.3F * size, size);
        tag.putInt("background", 0x80000000 | rgb);
        tag.putBoolean("default_background", false);
        tag.putBoolean("shadow", false);
        tag.putBoolean("see_through", true);
        tag.putInt("line_width", LINE_WIDTH);
        ModTextDisplayPackets.loadDisplayData(display, tag);
    }

    @Override
    protected void configureDistance(Display display, float size) {
        CompoundTag tag = configuredData(display, 0.0F, 0.8F * size);
        tag.putBoolean("default_background", true);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", true);
        tag.putInt("line_width", LINE_WIDTH);
        ModTextDisplayPackets.loadDisplayData(display, tag);
    }

    @Override
    protected Display createItemDisplay(ServerPlayer player, NamespacedId icon, float size) {
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocationHelper.mcId(icon.namespace(), icon.path()))
                .orElse(Items.AIR);
        if (item == Items.AIR) return null;
        //? if >=26.2 {
        /*Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, player.level());
        *///?} else {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, player.level());
        //?}
        this.configureItem(display, size);
        display.getSlot(0).set(new ItemStack(item));
        return display;
    }

    @Override
    protected void configureItem(Display display, float size) {
        CompoundTag tag = configuredData(display, 0.8F * size, 0.4F * size);
        tag.putString("item_display", "gui");
        ModTextDisplayPackets.loadDisplayData(display, tag);
    }

    private static CompoundTag configuredData(Display display, float offsetY, float size) {
        CompoundTag tag = ModTextDisplayPackets.saveDisplayData(display);
        Vector3f translation = new Vector3f(0.0F, offsetY, 0.0F);
        Vector3f scale = new Vector3f(size);
        tag.put("transformation", ModTextDisplayPackets.transformation(translation, new Quaternionf(), scale));
        tag.put("brightness", ModTextDisplayPackets.fullBrightness());
        tag.putString("billboard", "center");
        //? if >=1.20.2
        tag.putInt("teleport_duration", 1);
        return tag;
    }

    @Override
    protected void sendSpawn(ServerPlayer player, Display display) {
        ModTextDisplayPackets.sendSpawn(player, display);
    }

    @Override
    protected void sendRemove(ServerPlayer player, Display display) {
        ModTextDisplayPackets.sendRemove(player, display);
    }

    @Override
    protected void setText(ServerPlayer player, Display display, Component text) {
        ModTextDisplayPackets.setText(player, (Display.TextDisplay) display, text);
    }

    @Override
    protected boolean sendEntityData(ServerPlayer player, Display display) {
        return ModTextDisplayPackets.sendEntityData(player, display);
    }
}
