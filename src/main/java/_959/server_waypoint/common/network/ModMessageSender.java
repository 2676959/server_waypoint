package _959.server_waypoint.common.network;

import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.Locale;

import static _959.server_waypoint.common.network.BufferPayloadMapping.getPayload;
import static _959.server_waypoint.text.WaypointTextHelper.defaultWaypointText;

//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?} elif neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}

public class ModMessageSender implements PlatformMessageSender<ServerCommandSource, ServerPlayerEntity> {
    public static Text toVanillaText(Component component) {
        return TextCodecs.CODEC.decode(
                DynamicRegistryManager.EMPTY.getOps(JsonOps.INSTANCE),
                GsonComponentSerializer.gson().serializeToTree(component)).getOrThrow().getFirst();
    }

    private Text getTranslatedText(ServerCommandSource source, Component component) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            return getTranslatedText(player, component);
        } else {
            return toVanillaText(GlobalTranslator.render(component, Locale.getDefault()));
        }
    }

    private Text getTranslatedText(ServerPlayerEntity player, Component component) {
        String language = player.getClientOptions().language();
        Locale locale = Translator.parseLocale(language);
        if (locale == null) {
            locale = Locale.getDefault();
        }
        return toVanillaText(GlobalTranslator.render(component, locale));
    }

    @Override
    public void sendMessage(ServerCommandSource source, Component component) {
        source.sendMessage(getTranslatedText(source, component));
    }

    @Override
    public void sendPlayerMessage(ServerPlayerEntity player, Component component) {
        player.sendMessage(getTranslatedText(player, component));
    }

    @Override
    public void sendFeedback(ServerCommandSource source, Component component, boolean broadcastToOps) {
        source.sendFeedback(() -> getTranslatedText(source, component), broadcastToOps);
    }

    @Override
    public void sendError(ServerCommandSource source, Component component) {
        source.sendError(getTranslatedText(source, component));
    }

    @Override
    public void broadcastWaypointModification(ServerCommandSource source, WaypointModificationBuffer modification) {
        ServerPlayerEntity executorPlayer = source.getPlayer();
        Component waypointText = defaultWaypointText(modification.waypoint(), modification.dimensionName(), modification.listName());
        Component info;
        if (executorPlayer != null) {
            info = Component.translatable("waypoint.modification.broadcast.player", Component.text(executorPlayer.getName().getString()), modification.type().toTranslatable(), waypointText);
        } else {
            info = Component.translatable("waypoint.modification.broadcast.server", modification.type().toTranslatable(), waypointText);
        }
        source.getServer().getPlayerManager().getPlayerList().forEach(
                player -> {
                    sendPlayerMessage(player, info);
                    sendPlayerPacket(player, modification);
                }
        );
    }

    @Override
    public void sendPlayerPacket(ServerPlayerEntity player, MessageBuffer packet) {
        //? if fabric {
        ServerPlayNetworking.send(player, getPayload(packet));
        //?} else {
        /*PacketDistributor.sendToPlayer(player, payload);
         *///?}
    }

    @Override
    public void sendPacket(ServerCommandSource source, MessageBuffer packet) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            sendPlayerPacket(player, packet);
        }
    }
}
