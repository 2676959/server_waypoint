package _959.server_waypoint.navigation;

import com.mojang.math.Transformation;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

import static _959.server_waypoint.navigation.PaperTextDisplayPackets.handle;

/** Mounted text-display HUD for Paper. */
public final class PaperTextDisplayNavigationHandler
        extends AbstractTextDisplayNavigationHandler<Player, Display.TextDisplay> {
    private static final int BACKGROUND_COLOR = 0x66000000;
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
    protected Display.TextDisplay createDisplay(Player player) {
        return PaperTextDisplayPackets.createDisplay(player);
    }

    @Override
    protected void sendSpawn(Player player, Display.TextDisplay display) {
        PaperTextDisplayPackets.sendSpawn(player, display);
    }

    @Override
    protected void sendRemove(Player player, Display.TextDisplay display) {
        PaperTextDisplayPackets.sendRemove(player, display);
        handle(player).connection.send(new ClientboundSetPassengersPacket(handle(player)));
    }

    @Override
    protected void setText(Player player, Display.TextDisplay display, Component text) {
        PaperTextDisplayPackets.setText(player, display, text);
    }

    @Override
    protected void setTransformation(Display.TextDisplay display, Vector3f translation,
                                     Quaternionf rotation, Vector3f scale) {
        display.setTransformation(new Transformation(new Vector3f(translation), new Quaternionf(rotation),
                new Vector3f(scale), new Quaternionf()));
        display.setBrightnessOverride(Brightness.FULL_BRIGHT);
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, BACKGROUND_COLOR);
        display.getEntityData().set(Display.TextDisplay.DATA_LINE_WIDTH_ID, LINE_WIDTH);
        display.setFlags((byte) (Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH));
    }

    @Override
    protected void sendEntityData(Player player, Display.TextDisplay display) {
        PaperTextDisplayPackets.sendEntityData(player, display);
    }

    @Override
    protected void sendPassengers(Player player, Display.TextDisplay display) {
        ServerPlayer handle = handle(player);
        handle.connection.send(passengerPacket(handle, display.getId()));
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
            return ClientboundSetPassengersPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }


}
