package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.navigation.AbstractTextDisplayNavigationHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.kyori.adventure.text.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/** Mounted text-display HUD for the modded server platforms. */
final class ModTextDisplayNavigationHandler
        extends AbstractTextDisplayNavigationHandler<ServerPlayer, Display.TextDisplay> {
    private static final int BACKGROUND_COLOR = 0x66000000;
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
    protected Display.TextDisplay createDisplay(ServerPlayer player) {
        return ModTextDisplayPackets.createDisplay(player);
    }

    @Override
    protected void sendSpawn(ServerPlayer player, Display.TextDisplay display) {
        ModTextDisplayPackets.sendSpawn(player, display);
    }

    @Override
    protected void sendRemove(ServerPlayer player, Display.TextDisplay display) {
        ModTextDisplayPackets.sendRemove(player, display);
        player.connection.send(new ClientboundSetPassengersPacket(player));
    }

    @Override
    protected void setText(ServerPlayer player, Display.TextDisplay display, Component text) {
        ModTextDisplayPackets.setText(player, display, text);
    }

    @Override
    protected void setTransformation(Display.TextDisplay display, Vector3f translation,
                                     Quaternionf rotation, Vector3f scale) {
        CompoundTag tag = ModTextDisplayPackets.saveDisplayData(display);
        tag.put("transformation", ModTextDisplayPackets.transformation(translation, rotation, scale));
        tag.put("brightness", ModTextDisplayPackets.fullBrightness());
        tag.putString("billboard", "center");
        tag.putInt("background", BACKGROUND_COLOR);
        tag.putBoolean("default_background", false);
        //? if >=1.20.2
        tag.putInt("teleport_duration", 0);
        tag.putInt("line_width", LINE_WIDTH);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", true);
        ModTextDisplayPackets.loadDisplayData(display, tag);
    }

    @Override
    protected void sendEntityData(ServerPlayer player, Display.TextDisplay display) {
        ModTextDisplayPackets.sendEntityData(player, display);
    }

    @Override
    protected void sendPassengers(ServerPlayer player, Display.TextDisplay display) {
        player.connection.send(passengerPacket(player, display.getId()));
    }

    private static ClientboundSetPassengersPacket passengerPacket(
            ServerPlayer player,
            int displayEntityId
    ) {
        int[] passengerIds = new int[player.getPassengers().size() + 1];
        for (int index = 0; index < player.getPassengers().size(); index++) {
            passengerIds[index] = player.getPassengers().get(index).getId();
        }
        passengerIds[passengerIds.length - 1] = displayEntityId;

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(player.getId());
            buffer.writeVarIntArray(passengerIds);
            //? if <=1.20.4 {
            /*return new ClientboundSetPassengersPacket(buffer);
            *///?} else {
            return ClientboundSetPassengersPacket.STREAM_CODEC.decode(buffer);
            //?}
        } finally {
            buffer.release();
        }
    }
}
