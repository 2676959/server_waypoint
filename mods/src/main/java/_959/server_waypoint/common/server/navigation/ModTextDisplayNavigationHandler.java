package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.navigation.AbstractTextDisplayNavigationHandler;
import com.mojang.math.Transformation;
import com.mojang.serialization.DataResult;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.21.5
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
//? if >=1.21.6 {
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//?}
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
//? if >=26.2
/*import net.minecraft.world.entity.EntityTypes;*/
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Packet-only text display navigation for the modded server platforms. The
 * display is never added to the level and is visible only to its owner.
 */
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
        Display.TextDisplay display = new Display.TextDisplay(
                textDisplayType(),
                player.level()
        );
        display.setPos(player.getX(), player.getY(), player.getZ());
        return display;
    }

    @Override
    protected void sendSpawn(ServerPlayer player, Display.TextDisplay display) {
        player.connection.send(new ClientboundAddEntityPacket(
                display.getId(),
                display.getUUID(),
                player.getX(),
                player.getY(),
                player.getZ(),
                0.0F,
                0.0F,
                textDisplayType(),
                0,
                Vec3.ZERO,
                0.0D
        ));
    }

    @Override
    protected void sendRemove(ServerPlayer player, Display.TextDisplay display) {
        player.connection.send(new ClientboundRemoveEntitiesPacket(display.getId()));
        player.connection.send(new ClientboundSetPassengersPacket(player));
    }

    @Override
    protected void setText(
            ServerPlayer player,
            Display.TextDisplay display,
            Component text
    ) {
        Component translated = GlobalTranslator.render(text, playerLocale(player));
        CompoundTag tag = saveDisplayData(display);
        //? if <1.21.5 {
        /*tag.putString("text", GsonComponentSerializer.gson().serialize(translated));
        *///?} else {
        tag.put(
                "text",
                encode(
                        ComponentSerialization.CODEC.encodeStart(
                                NbtOps.INSTANCE,
                                ModMessageSender.toVanillaText(translated)
                        ),
                        "text"
                )
        );
        //?}
        loadDisplayData(display, tag);
    }

    @Override
    protected void setTransformation(
            Display.TextDisplay display,
            Vector3f translation,
            Quaternionf rotation,
            Vector3f scale
    ) {
        CompoundTag tag = saveDisplayData(display);
        tag.put(
                "transformation",
                encode(
                        Transformation.EXTENDED_CODEC.encodeStart(
                                NbtOps.INSTANCE,
                                new Transformation(
                                        new Vector3f(translation),
                                        new Quaternionf(rotation),
                                        new Vector3f(scale),
                                        new Quaternionf()
                                )
                        ),
                        "transformation"
                )
        );
        tag.put(
                "brightness",
                encode(
                        Brightness.CODEC.encodeStart(NbtOps.INSTANCE, Brightness.FULL_BRIGHT),
                        "brightness"
                )
        );
        tag.putString("billboard", "center");
        tag.putInt("background", BACKGROUND_COLOR);
        tag.putInt("line_width", LINE_WIDTH);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", true);
        loadDisplayData(display, tag);
    }

    @Override
    protected void sendEntityData(ServerPlayer player, Display.TextDisplay display) {
        List<SynchedEntityData.DataValue<?>> data = display.getEntityData().packDirty();
        if (data != null) {
            player.connection.send(new ClientboundSetEntityDataPacket(display.getId(), data));
        }
    }

    @Override
    protected void sendPassengers(ServerPlayer player, Display.TextDisplay display) {
        player.connection.send(passengerPacket(player, display.getId()));
    }

    private static CompoundTag saveDisplayData(Display.TextDisplay display) {
        //? if >=1.21.6 {
        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                display.registryAccess()
        );
        display.saveWithoutId(output);
        return output.buildResult();
        //?} else {
        /*return display.saveWithoutId(new CompoundTag());
        *///?}
    }

    private static void loadDisplayData(Display.TextDisplay display, CompoundTag tag) {
        //? if >=1.21.6 {
        display.load(TagValueInput.create(
                ProblemReporter.DISCARDING,
                display.registryAccess(),
                tag
        ));
        //?} else {
        /*display.load(tag);
        *///?}
    }

    private static Tag encode(DataResult<Tag> result, String field) {
        return result.result().orElseThrow(() -> new IllegalStateException(
                "Could not encode text display " + field
        ));
    }

    private static Locale playerLocale(ServerPlayer player) {
        //? if <=1.20.1 {
        /*String language = ((_959.server_waypoint.access.PlayerLocaleAccessor) player)
                .sw$getLocale();
        *///?} else {
        String language = player.clientInformation().language();
        //?}
        Locale locale = Translator.parseLocale(language);
        return locale == null ? Locale.getDefault() : locale;
    }

    private static EntityType<Display.TextDisplay> textDisplayType() {
        //? if >=26.2 {
        /*return EntityTypes.TEXT_DISPLAY;
        *///?} else {
        return EntityType.TEXT_DISPLAY;
        //?}
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
