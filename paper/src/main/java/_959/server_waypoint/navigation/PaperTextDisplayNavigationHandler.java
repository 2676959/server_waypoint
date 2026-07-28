package _959.server_waypoint.navigation;

import com.mojang.math.Transformation;
import io.papermc.paper.adventure.PaperAdventure;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

//? if >= 26.2 {
/*import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntityType;
*///?} else {
import net.minecraft.world.entity.EntityType;
//?}

/**
 * Paper adapter for the packet-only text display navigation method.
 */
public final class PaperTextDisplayNavigationHandler
        extends AbstractTextDisplayNavigationHandler<Player, Display.TextDisplay> {
    private static final int BACKGROUND_COLOR = 0x66000000;
    private static final int LINE_WIDTH = 1000;
    //? if >= 26.2 {
    /*private static final EntityType<Display.TextDisplay> TEXT_DISPLAY = EntityTypes.TEXT_DISPLAY;
    *///?} else {
    private static final EntityType<Display.TextDisplay> TEXT_DISPLAY = EntityType.TEXT_DISPLAY;
    //?}

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
        ServerPlayer handle = handle(player);
        Display.TextDisplay display = new Display.TextDisplay(
                TEXT_DISPLAY,
                handle.level()
        );
        display.setPos(handle.getX(), handle.getY(), handle.getZ());
        return display;
    }

    @Override
    protected void sendSpawn(Player player, Display.TextDisplay display) {
        ServerPlayer handle = handle(player);
        handle.connection.send(new ClientboundAddEntityPacket(
                display.getId(),
                display.getUUID(),
                handle.getX(),
                handle.getY(),
                handle.getZ(),
                0.0F,
                0.0F,
                TEXT_DISPLAY,
                0,
                Vec3.ZERO,
                0.0D
        ));
    }

    @Override
    protected void sendRemove(Player player, Display.TextDisplay display) {
        ServerPlayer handle = handle(player);
        handle.connection.send(new ClientboundRemoveEntitiesPacket(display.getId()));
        handle.connection.send(new ClientboundSetPassengersPacket(handle));
    }

    @Override
    protected void setText(Player player, Display.TextDisplay display, Component text) {
        Component translated = GlobalTranslator.render(text, player.locale());
        display.setText(PaperAdventure.asVanilla(translated));
    }

    @Override
    protected void setTransformation(
            Display.TextDisplay display,
            Vector3f translation,
            Quaternionf rotation,
            Vector3f scale
    ) {
        display.setTransformation(
                new Transformation(
                        new Vector3f(translation),
                        new Quaternionf(rotation),
                        new Vector3f(scale),
                        new Quaternionf()
                )
        );
        display.setBrightnessOverride(Brightness.FULL_BRIGHT);
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.getEntityData().set(
                Display.TextDisplay.DATA_BACKGROUND_COLOR_ID,
                BACKGROUND_COLOR
        );
        display.getEntityData().set(
                Display.TextDisplay.DATA_LINE_WIDTH_ID,
                LINE_WIDTH
        );
        display.setFlags(
                (byte) (Display.TextDisplay.FLAG_SHADOW
                        | Display.TextDisplay.FLAG_SEE_THROUGH)
        );
    }

    @Override
    protected void sendEntityData(Player player, Display.TextDisplay display) {
        List<SynchedEntityData.DataValue<?>> data = display.getEntityData().packDirty();
        if (data != null) {
            handle(player).connection.send(
                    new ClientboundSetEntityDataPacket(display.getId(), data)
            );
        }
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

    private static ServerPlayer handle(Player player) {
        return ((CraftPlayer) player).getHandle();
    }
}
