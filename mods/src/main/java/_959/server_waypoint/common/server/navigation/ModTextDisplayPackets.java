package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.util.PlayerLocaleHelper;
import com.mojang.math.Transformation;
import com.mojang.serialization.DataResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
//? if >=1.21.5
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
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

/** Entity construction, metadata encoding and owner-only packets shared by the two navigation handlers. */
final class ModTextDisplayPackets {
    private ModTextDisplayPackets() {
    }

    static Display.TextDisplay createDisplay(ServerPlayer player) {
        Display.TextDisplay display = new Display.TextDisplay(
                textDisplayType(),
                player.level()
        );
        display.setPos(player.getX(), player.getY(), player.getZ());
        return display;
    }

    static void sendSpawn(ServerPlayer player, Display display) {
        player.connection.send(new ClientboundAddEntityPacket(
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

    static void setText(
            ServerPlayer player,
            Display.TextDisplay display,
            Component text
    ) {
        Component translated = GlobalTranslator.render(text, PlayerLocaleHelper.forPlayer(player));
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

    static boolean sendEntityData(ServerPlayer player, Display display) {
        List<SynchedEntityData.DataValue<?>> data = display.getEntityData().packDirty();
        if (data != null) {
            player.connection.send(new ClientboundSetEntityDataPacket(display.getId(), data));
            return true;
        }
        return false;
    }

    static CompoundTag saveDisplayData(Display display) {
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

    static void loadDisplayData(Display display, CompoundTag tag) {
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

    private static EntityType<Display.TextDisplay> textDisplayType() {
        //? if >=26.2 {
        /*return EntityTypes.TEXT_DISPLAY;
        *///?} else {
        return EntityType.TEXT_DISPLAY;
        //?}
    }

    static void sendRemove(ServerPlayer player, Display display) {
        player.connection.send(new ClientboundRemoveEntitiesPacket(display.getId()));
    }

    static Tag transformation(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        return encode(Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE,
                new Transformation(new Vector3f(translation), new Quaternionf(rotation),
                        new Vector3f(scale), new Quaternionf())), "transformation");
    }

    static Tag fullBrightness() {
        return encode(Brightness.CODEC.encodeStart(NbtOps.INSTANCE, Brightness.FULL_BRIGHT), "brightness");
    }
}
