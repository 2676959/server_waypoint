package _959.server_waypoint.common.network;

//? if <= 1.20.1
/*import _959.server_waypoint.access.PlayerLocaleAccessor;*/
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;

import static _959.server_waypoint.common.network.BufferPayloadMapping.getPayload;
import static net.kyori.adventure.text.Component.text;

//? if >= 1.20.3 {
import com.mojang.serialization.JsonOps;
import net.minecraft.text.TextCodecs;
//?}

//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?} elif neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}

public class ModMessageSender implements PlatformMessageSender<ServerCommandSource, ServerPlayerEntity> {
    private static final ModMessageSender INSTANCE = new ModMessageSender();

    public static ModMessageSender getInstance() {
        return INSTANCE;
    }

    public static Text toVanillaText(Component component) {
        //? if >= 1.20.3 {
        var result = TextCodecs.CODEC.decode(JsonOps.INSTANCE, GsonComponentSerializer.gson().serializeToTree(component)).result();
        if (result.isPresent()) {
            return result.get().getFirst();
        } else {
            return Text.literal("failed to decode message component");
        }
        //?} else {
        /*return Text.Serializer.fromJson(GsonComponentSerializer.gson().serializeToTree(component));
        *///?}
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
        //? if <= 1.20.1 {
        /*String language = ((PlayerLocaleAccessor) player).sw$getLocale();
        *///?} else {
        String language = player.getClientOptions().language();
        //?}
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
    public void sendError(ServerCommandSource source, Component component) {
        source.sendMessage(getTranslatedText(source, component.color(NamedTextColor.RED)));
    }

    @Override
    public void broadcastWaypointModification(ServerCommandSource source, WaypointModificationBuffer modification) {
        Component info = this.getModificationMessage(text(source.getName()), modification);
//        if (executorPlayer != null) {
//            info = Component.translatable("waypoint.modification.broadcast.player", Component.text(executorPlayer.getName().getString()), modification.type().toTranslatable(), waypointText);
//        } else {
//            info = Component.translatable("waypoint.modification.broadcast.server", modification.type().toTranslatable(), waypointText);
//        }
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
