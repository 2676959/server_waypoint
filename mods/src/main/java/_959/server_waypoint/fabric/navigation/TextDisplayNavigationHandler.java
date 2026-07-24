package _959.server_waypoint.fabric.navigation;

import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.navigation.NavigationDisplayText;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.TextDisplayTransformation;
import _959.server_waypoint.navigation.TextDisplayTransformationHandler;
import com.mojang.math.Transformation;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Experimental, packet-only fake HUD for 26.1.2 Fabric. The text display is
 * never added to the server level; only the owning player receives it.
 */
public final class TextDisplayNavigationHandler
        implements TextDisplayTransformationHandler<ServerPlayer> {
    private static final int BACKGROUND_COLOR = 0x66000000;
    private static final int LINE_WIDTH = 1000;

    private final Map<UUID, DisplayState> displays = new HashMap<>();

    @Override
    public NavigationMethod method() {
        return NavigationMethod.TEXT_DISPLAY;
    }

    @Override
    public NavigationResult enable(
            ServerPlayer player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        this.removeDisplay(player);
        DisplayState state = this.createDisplay(player);
        this.displays.put(player.getUUID(), state);
        this.applyTransformationData(
                state.display(),
                session.textDisplayTransformation()
        );
        this.sendSpawn(player, state.display());
        this.update(player, session, snapshot);
        return NavigationResult.success();
    }

    @Override
    public void update(ServerPlayer player, NavigationSession session, NavigationSnapshot snapshot) {
        DisplayState state = this.displays.get(player.getUUID());
        if (state == null
                || state.hostEntityId() != player.getId()
                || state.display().level() != player.level()) {
            this.removeDisplay(player);
            state = this.createDisplay(player);
            this.displays.put(player.getUUID(), state);
            this.applyTransformationData(
                    state.display(),
                    session.textDisplayTransformation()
            );
            this.sendSpawn(player, state.display());
        }

        Component text = ModMessageSender.getInstance().getTranslatedText(
                player,
                NavigationDisplayText.buildTextDisplay(session, snapshot)
        );
        this.applyTextData(state.display(), text);
        this.sendEntityData(player, state.display());
        player.connection.send(passengerPacket(player, state.display().getId()));
    }

    @Override
    public void applyTransformation(
            ServerPlayer player,
            Vector3f translation,
            Quaternionf rotation,
            Vector3f scale
    ) {
        DisplayState state = this.displays.get(player.getUUID());
        if (state == null) {
            throw new IllegalStateException("Navigation text display is not active");
        }
        this.applyTransformationData(state.display(), translation, rotation, scale);
        this.sendEntityData(player, state.display());
    }

    @Override
    public void disable(ServerPlayer player, NavigationSession session) {
        this.removeDisplay(player);
    }

    @Override
    public void cleanupPlayer(UUID playerUuid, NavigationSession session) {
        this.displays.remove(playerUuid);
    }

    private DisplayState createDisplay(ServerPlayer player) {
        Display.TextDisplay display = (Display.TextDisplay) EntityType.TEXT_DISPLAY.create(
                player.level(),
                EntitySpawnReason.TRIGGERED
        );
        if (display == null) {
            throw new IllegalStateException("Could not create experimental navigation text display");
        }
        display.setPos(player.getX(), player.getY(), player.getZ());
        return new DisplayState(display, player.getId());
    }

    private void sendSpawn(ServerPlayer player, Display.TextDisplay display) {
        player.connection.send(new ClientboundAddEntityPacket(
                display.getId(),
                display.getUUID(),
                player.getX(),
                player.getY(),
                player.getZ(),
                0.0F,
                0.0F,
                EntityType.TEXT_DISPLAY,
                0,
                Vec3.ZERO,
                0.0D
        ));
    }

    private void removeDisplay(ServerPlayer player) {
        DisplayState state = this.displays.remove(player.getUUID());
        if (state == null) {
            return;
        }
        player.connection.send(new ClientboundRemoveEntitiesPacket(state.display().getId()));
        player.connection.send(new ClientboundSetPassengersPacket(player));
    }

    private void applyTextData(Display.TextDisplay display, Component text) {
        CompoundTag tag = saveDisplayData(display);
        tag.put("text", encode(ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, text), "text"));
        this.loadDisplayData(display, tag);
    }

    private void applyTransformationData(
            Display.TextDisplay display,
            TextDisplayTransformation transformation
    ) {
        this.applyTransformationData(
                display,
                transformation.resolvedTranslation(),
                transformation.rotationQuaternion(),
                transformation.resolvedScale()
        );
    }

    private void applyTransformationData(
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
        tag.put("brightness", encode(
                Brightness.CODEC.encodeStart(NbtOps.INSTANCE, Brightness.FULL_BRIGHT),
                "brightness"
        ));
        tag.putString("billboard", "center");
        tag.putInt("background", BACKGROUND_COLOR);
        tag.putInt("line_width", LINE_WIDTH);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", true);
        this.loadDisplayData(display, tag);
    }

    private static CompoundTag saveDisplayData(Display.TextDisplay display) {
        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                display.registryAccess()
        );
        display.saveWithoutId(output);
        return output.buildResult();
    }

    private void loadDisplayData(Display.TextDisplay display, CompoundTag tag) {
        display.load(TagValueInput.create(
                ProblemReporter.DISCARDING,
                display.registryAccess(),
                tag
        ));
    }

    private void sendEntityData(ServerPlayer player, Display.TextDisplay display) {
        List<SynchedEntityData.DataValue<?>> data = display.getEntityData().packDirty();
        if (data != null) {
            player.connection.send(new ClientboundSetEntityDataPacket(display.getId(), data));
        }
    }

    private static Tag encode(com.mojang.serialization.DataResult<Tag> result, String field) {
        return result.getOrThrow(message -> new IllegalStateException(
                "Could not encode text display " + field + ": " + message
        ));
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

    private record DisplayState(Display.TextDisplay display, int hostEntityId) {
    }
}
