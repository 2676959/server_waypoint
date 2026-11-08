package _959.server_waypoint.navigation;

import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;

//? if >= 26.2 {
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntityType;
//?} else {
/*import net.minecraft.world.entity.EntityType;
*///?}

/** Owner-only entity packets and text conversion shared by the two Paper navigation handlers. */
final class PaperTextDisplayPackets {
    //? if >= 26.2 {
    private static final EntityType<Display.TextDisplay> TEXT_DISPLAY = EntityTypes.TEXT_DISPLAY;
    //?} else {
    /*private static final EntityType<Display.TextDisplay> TEXT_DISPLAY = EntityType.TEXT_DISPLAY;
    *///?}

    private PaperTextDisplayPackets() {
    }

    static Display.TextDisplay createDisplay(Player player) {
        ServerPlayer handle = handle(player);
        Display.TextDisplay display = new Display.TextDisplay(
                TEXT_DISPLAY,
                handle.level()
        );
        display.setPos(handle.getX(), handle.getY(), handle.getZ());
        return display;
    }

    static void sendSpawn(Player player, Display display) {
        ServerPlayer handle = handle(player);
        handle.connection.send(new ClientboundAddEntityPacket(
                display.getId(),
                display.getUUID(),
                display.getX(),
                display.getY(),
                display.getZ(),
                0.0F,
                0.0F,
                display.getType(),
                0,
                Vec3.ZERO,
                0.0D
        ));
    }

    static void setText(Player player, Display.TextDisplay display, Component text) {
        Component translated = GlobalTranslator.render(text, player.locale());
        display.setText(PaperAdventure.asVanilla(translated));
    }

    static boolean sendEntityData(Player player, Display display) {
        List<SynchedEntityData.DataValue<?>> data = display.getEntityData().packDirty();
        if (data != null) {
            handle(player).connection.send(
                    new ClientboundSetEntityDataPacket(display.getId(), data)
            );
            return true;
        }
        return false;
    }

    static ServerPlayer handle(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    static void sendRemove(Player player, Display display) {
        handle(player).connection.send(new ClientboundRemoveEntitiesPacket(display.getId()));
    }
}
