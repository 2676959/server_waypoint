package _959.server_waypoint.navigation;

import net.kyori.adventure.text.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared lifecycle for a client-only text display mounted to its owning
 * player. Platform adapters own entity creation, metadata, and packet I/O.
 */
public abstract class AbstractTextDisplayNavigationHandler<P, D>
        implements TextDisplayTransformationHandler<P> {
    private final Map<UUID, DisplayState<D>> displays = new ConcurrentHashMap<>();

    @Override
    public final NavigationMethod method() {
        return NavigationMethod.TEXT_DISPLAY;
    }

    @Override
    public final NavigationResult enable(
            P player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        this.removeDisplay(player);
        DisplayState<D> state = this.createState(player);
        this.displays.put(this.playerUuid(player), state);
        this.setTransformation(state.display(), session.textDisplayTransformation());
        this.sendSpawn(player, state.display());
        this.update(player, session, snapshot);
        return NavigationResult.success();
    }

    @Override
    public final void update(
            P player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        UUID playerUuid = this.playerUuid(player);
        DisplayState<D> state = this.displays.get(playerUuid);
        if (state == null
                || state.hostEntityId() != this.playerEntityId(player)
                || state.worldIdentity() != this.worldIdentity(player)) {
            this.removeDisplay(player);
            state = this.createState(player);
            this.displays.put(playerUuid, state);
            this.setTransformation(state.display(), session.textDisplayTransformation());
            this.sendSpawn(player, state.display());
        }

        this.setText(
                player,
                state.display(),
                NavigationDisplayText.buildTextDisplay(session, snapshot)
        );
        this.sendEntityData(player, state.display());
        this.sendPassengers(player, state.display());
    }

    @Override
    public final void applyTransformation(
            P player,
            Vector3f translation,
            Quaternionf rotation,
            Vector3f scale
    ) {
        DisplayState<D> state = this.displays.get(this.playerUuid(player));
        if (state == null) {
            throw new IllegalStateException("Navigation text display is not active");
        }
        this.setTransformation(state.display(), translation, rotation, scale);
        this.sendEntityData(player, state.display());
    }

    @Override
    public final void disable(P player, NavigationSession session) {
        this.removeDisplay(player);
    }

    @Override
    public final void cleanupPlayer(UUID playerUuid, NavigationSession session) {
        this.displays.remove(playerUuid);
    }

    protected abstract UUID playerUuid(P player);

    protected abstract int playerEntityId(P player);

    protected abstract Object worldIdentity(P player);

    protected abstract D createDisplay(P player);

    protected abstract void sendSpawn(P player, D display);

    protected abstract void sendRemove(P player, D display);

    protected abstract void setText(P player, D display, Component text);

    protected abstract void setTransformation(
            D display,
            Vector3f translation,
            Quaternionf rotation,
            Vector3f scale
    );

    protected abstract void sendEntityData(P player, D display);

    protected abstract void sendPassengers(P player, D display);

    private DisplayState<D> createState(P player) {
        D display = this.createDisplay(player);
        if (display == null) {
            throw new IllegalStateException("Could not create navigation text display");
        }
        return new DisplayState<>(
                display,
                this.playerEntityId(player),
                this.worldIdentity(player)
        );
    }

    private void setTransformation(D display, TextDisplayTransformation transformation) {
        this.setTransformation(
                display,
                transformation.resolvedTranslation(),
                transformation.rotationQuaternion(),
                transformation.resolvedScale()
        );
    }

    private void removeDisplay(P player) {
        DisplayState<D> state = this.displays.remove(this.playerUuid(player));
        if (state != null) {
            this.sendRemove(player, state.display());
        }
    }

    private record DisplayState<D>(D display, int hostEntityId, Object worldIdentity) {
    }
}
