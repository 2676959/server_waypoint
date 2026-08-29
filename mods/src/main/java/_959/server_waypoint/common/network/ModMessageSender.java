package _959.server_waypoint.common.network;

//? if <= 1.20.1
/*import _959.server_waypoint.access.PlayerLocaleAccessor;*/
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import _959.server_waypoint.common.server.WaypointServerMod;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


import static _959.server_waypoint.common.network.MessagePayloadMapping.getPayload;
//? if >= 1.20.3 {
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.ComponentSerialization;
//?}
//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?} elif forge {
/*import _959.server_waypoint.forge.ServerWaypointForge;
import net.minecraftforge.network.PacketDistributor;
*///?} elif neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}
//? if neoforge && = 1.20.2
/*import _959.server_waypoint.neoforge.ServerWaypointNeoForge;*/

public class ModMessageSender implements PlatformMessageSender<CommandSourceStack, ServerPlayer> {
    private static final ModMessageSender INSTANCE = new ModMessageSender();
    private final Set<UUID> chunkedMessageCapablePlayers = ConcurrentHashMap.newKeySet();

    public static ModMessageSender getInstance() {
        return INSTANCE;
    }

    public static net.minecraft.network.chat.Component toVanillaText(Component component) {
        //? if >= 1.20.3 {
        var result = ComponentSerialization.CODEC.decode(JsonOps.INSTANCE, GsonComponentSerializer.gson().serializeToTree(component)).result();
        if (result.isPresent()) {
            return result.get().getFirst();
        } else {
            return net.minecraft.network.chat.Component.literal("failed to decode message component");
        }
        //?} else {
        /*return net.minecraft.network.chat.Component.Serializer.fromJson(GsonComponentSerializer.gson().serializeToTree(component));
        *///?}
    }

    private net.minecraft.network.chat.Component getTranslatedText(CommandSourceStack source, Component component) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return getTranslatedText(player, component);
        } else {
            return toVanillaText(GlobalTranslator.render(component, Locale.getDefault()));
        }
    }

    public net.minecraft.network.chat.Component getTranslatedText(ServerPlayer player, Component component) {
        //? if <= 1.20.1 {
        /*String language = ((PlayerLocaleAccessor) player).sw$getLocale();
        *///?} else {
        String language = player.clientInformation().language();
        //?}
        Locale locale = Translator.parseLocale(language);
        if (locale == null) {
            locale = Locale.getDefault();
        }
        return toVanillaText(GlobalTranslator.render(component, locale));
    }

    @Override
    public void sendMessage(CommandSourceStack source, Component component) {
        source.sendSystemMessage(getTranslatedText(source, component));
    }

    @Override
    public void sendPlayerMessage(ServerPlayer player, Component component) {
        player.sendSystemMessage(getTranslatedText(player, component));
    }

    @Override
    public void sendError(CommandSourceStack source, Component component) {
        source.sendSystemMessage(getTranslatedText(source, component.color(NamedTextColor.RED)));
    }

    @Override
    public Collection<ServerPlayer> getBroadcastPlayers(CommandSourceStack source) {
        return source.getServer().getPlayerList().getPlayers();
    }

    @Override
    public Collection<ServerPlayer> getBroadcastPlayersFromPlayer(ServerPlayer player) {
        return WaypointServerMod.MINECRAFT_SERVER == null
                ? java.util.List.of(player)
                : WaypointServerMod.MINECRAFT_SERVER.getPlayerList().getPlayers();
    }

    @Override
    public Component getSenderName(CommandSourceStack source) {
        return Component.text(source.getTextName());
    }

    @Override
    public void broadcastPacket(SinglePacketMessage message) {
        if (WaypointServerMod.MINECRAFT_SERVER != null) {
            WaypointServerMod.MINECRAFT_SERVER.getPlayerList().getPlayers()
                    .forEach(player -> sendPlayerPacket(player, message));
        }
    }

    @Override
    public void broadcastChunkedMessage(ChunkedMessage message) {
        if (WaypointServerMod.MINECRAFT_SERVER != null) {
            this.broadcastChunkedMessage(
                    WaypointServerMod.MINECRAFT_SERVER.getPlayerList().getPlayers(),
                    message
            );
        }
    }

    @Override
    public void sendPlayerPacket(ServerPlayer player, SinglePacketMessage message) {
        try {
            byte[] encodedMessage = SinglePacketMessageEncoder.encode(message);
        //? if fabric {
        ServerPlayNetworking.send(player, getPayload(message, encodedMessage));
        //?} elif forge {
        /*//? if <= 1.20.1 {
        /^ServerWaypointForge.PACKET_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), getPayload(message, encodedMessage));
        ^///?} else {
        ServerWaypointForge.PACKET_CHANNEL.send(getPayload(message, encodedMessage), PacketDistributor.PLAYER.with(player));
        //?}
        *///?} elif neoforge && = 1.20.2 {
        /*ServerWaypointNeoForge.PACKET_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), getPayload(message, encodedMessage));
        *///?} elif neoforge && = 1.20.4 {
        /*PacketDistributor.PLAYER.with(player).send(getPayload(message, encodedMessage));
        *///?} else {
        /*PacketDistributor.sendToPlayer(player, getPayload(message, encodedMessage));
         *///?}
        } catch (MessageEncodingException exception) {
            WaypointServerMod.LOGGER.warn(
                    "Failed to encode single-packet message type {} within the {}-byte packet budget",
                    message.getClass().getSimpleName(),
                    SinglePacketMessageEncoder.MAX_ENCODED_BYTES,
                    exception
            );
            this.sendPlayerMessage(
                    player,
                    Component.translatable("waypoint.network.encoding_failed")
            );
        }
    }

    @Override
    public void sendPacket(CommandSourceStack source, SinglePacketMessage message) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            sendPlayerPacket(player, message);
        }
    }

    @Override
    public void sendChunkedMessage(CommandSourceStack source, ChunkedMessage message) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            this.sendPlayerChunkedMessage(player, message);
        }
    }

    @Override
    public void setChunkedMessageCapable(ServerPlayer player, boolean capable) {
        UUID playerId = player.getUUID();
        if (capable) {
            this.chunkedMessageCapablePlayers.add(playerId);
        } else {
            this.chunkedMessageCapablePlayers.remove(playerId);
            PlatformMessageSender.super.disconnectChunkedMessages(player);
        }
    }

    @Override
    public boolean canSendChunkedMessage(ServerPlayer player) {
        return this.chunkedMessageCapablePlayers.contains(player.getUUID());
    }

    @Override
    public void disconnectChunkedMessages(ServerPlayer player) {
        this.setChunkedMessageCapable(player, false);
    }
}
