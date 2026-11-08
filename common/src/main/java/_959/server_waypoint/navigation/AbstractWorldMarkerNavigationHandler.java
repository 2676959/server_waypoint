package _959.server_waypoint.navigation;

import net.kyori.adventure.text.Component;
import _959.server_waypoint.util.NamespacedId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Private badge, distance and optional item rows sharing per-tick motion prediction. */
public abstract class AbstractWorldMarkerNavigationHandler<P, D> implements NavigationMethodHandler<P> {
    private final Map<UUID, DisplayState<D>> displays = new ConcurrentHashMap<>();
    private final Map<UUID, WorldMarkerMotion> motion = new ConcurrentHashMap<>();
    private final Map<UUID, MarkerCounters> markerCounters = new ConcurrentHashMap<>();

    @Override
    public final NavigationMethod method() {
        return NavigationMethod.WORLD_MARKER;
    }

    @Override
    public final NavigationResult enable(P player, NavigationSession session, NavigationSnapshot snapshot) {
        this.removeDisplay(player);
        this.markerCounters.put(this.playerUuid(player), new MarkerCounters());
        this.update(player, session, snapshot);
        return NavigationResult.success();
    }

    @Override
    public final void update(P player, NavigationSession session, NavigationSnapshot snapshot) {
        long start = System.nanoTime();
        MarkerCounters counters = this.markerCounters.computeIfAbsent(this.playerUuid(player),
                ignored -> new MarkerCounters());
        counters.updates++;
        try {
            this.updateMarker(player, session, snapshot, counters);
        } finally {
            counters.updateNanos += System.nanoTime() - start;
        }
    }

    @Override
    public final void disable(P player, NavigationSession session) {
        this.removeDisplay(player);
        this.markerCounters.remove(this.playerUuid(player));
    }

    @Override
    public final void cleanupPlayer(UUID playerUuid, NavigationSession session) {
        this.displays.remove(playerUuid);
        this.motion.remove(playerUuid);
        this.markerCounters.remove(playerUuid);
    }

    public final Optional<WorldMarkerStatistics> markerStatistics(UUID playerUuid) {
        MarkerCounters counters = this.markerCounters.get(playerUuid);
        return counters == null ? Optional.empty() : Optional.of(new WorldMarkerStatistics(
                counters.updates, counters.spawns, counters.moves, counters.metadata, counters.removals,
                counters.updateNanos));
    }

    protected abstract UUID playerUuid(P player);

    protected abstract int playerEntityId(P player);

    protected abstract Object worldIdentity(P player);

    protected abstract WorldMarkerPosition eyePosition(P player);

    protected abstract Object textLocale(P player);

    protected abstract D createDisplay(P player);

    protected abstract void configureBadge(D display, int rgb, float size);

    protected abstract void configureDistance(D display, float size);

    /** Returns null for an unregistered/non-item icon. VoxelMap icons are excluded before this call. */
    protected abstract D createItemDisplay(P player, NamespacedId icon, float size);

    protected abstract void configureItem(D display, float size);

    protected abstract long currentTick(P player);

    protected abstract int latencyMillis(P player);

    protected abstract void setPosition(D display, WorldMarkerPosition position);

    protected abstract void sendPosition(P player, D display);

    protected abstract void sendSpawn(P player, D display);

    protected abstract void sendRemove(P player, D display);

    protected abstract void setText(P player, D display, Component text);

    protected abstract boolean sendEntityData(P player, D display);

    private void updateMarker(P player, NavigationSession session, NavigationSnapshot snapshot,
                              MarkerCounters counters) {
        if (!snapshot.inTargetDimension()) {
            this.removeDisplay(player);
            return;
        }
        UUID uuid = this.playerUuid(player);
        DisplayState<D> state = this.displays.get(uuid);
        if (state != null && (state.hostEntityId != this.playerEntityId(player)
                || state.worldIdentity != this.worldIdentity(player))) {
            this.removeDisplay(player);
            state = null;
        }
        boolean spawned = state == null;
        int rgb = session.target().rgb();
        float size = session.worldMarkerSize();
        if (spawned) {
            D badge = this.createDisplay(player);
            D distance = this.createDisplay(player);
            if (badge == null || distance == null) {
                throw new IllegalStateException("Could not create navigation world marker");
            }
            this.configureBadge(badge, rgb, size);
            this.configureDistance(distance, size);
            state = new DisplayState<>(badge, distance, this.playerEntityId(player), this.worldIdentity(player),
                    rgb, size);
            this.displays.put(uuid, state);
        }
        boolean sizeChanged = state.size != size;
        boolean badgeStyleChanged = sizeChanged || state.rgb != rgb;
        if (badgeStyleChanged) {
            this.configureBadge(state.badge, rgb, size);
            state.rgb = rgb;
        }
        if (sizeChanged) {
            this.configureDistance(state.distance, size);
        }
        NamespacedId icon = session.target().waypointIcon();
        if (icon != null && icon.namespace().equals("voxelmap")) icon = null;
        boolean itemSpawned = false;
        if (!Objects.equals(icon, state.iconId)) {
            if (state.item != null) {
                this.sendRemove(player, state.item);
                counters.removals++;
            }
            state.iconId = icon;
            state.item = icon == null ? null : this.createItemDisplay(player, icon, size);
            itemSpawned = state.item != null;
        }
        if (sizeChanged && !itemSpawned && state.item != null) {
            this.configureItem(state.item, size);
        }
        long tick = this.currentTick(player);
        WorldMarkerPosition eye = this.eyePosition(player);
        WorldMarkerMotion motion = this.motion.computeIfAbsent(uuid, ignored -> new WorldMarkerMotion());
        motion.sample(eye, tick);
        boolean positionDue = spawned || tick >= state.nextPositionTick;
        if (positionDue || itemSpawned) {
            WorldMarkerPosition position = motion.project(
                    eye, session.target().position(), this.latencyMillis(player), session.worldMarkerDistance());
            if (positionDue) {
                this.setPosition(state.badge, position);
                this.setPosition(state.distance, position);
                state.nextPositionTick = tick + 1;
            }
            if (state.item != null) this.setPosition(state.item, position);
        }
        Component badgeText = NavigationDisplayText.buildWorldMarkerBadge(session.target());
        Component distanceText = NavigationDisplayText.buildWorldMarkerDistance(snapshot);
        Object locale = this.textLocale(player);
        boolean localeChanged = !Objects.equals(locale, state.locale);
        boolean badgeChanged = !badgeText.equals(state.badgeText) || localeChanged;
        boolean distanceChanged = !distanceText.equals(state.distanceText) || localeChanged;
        if (badgeChanged) {
            this.setText(player, state.badge, badgeText);
            state.badgeText = badgeText;
        }
        if (distanceChanged) {
            this.setText(player, state.distance, distanceText);
            state.distanceText = distanceText;
        }
        state.locale = locale;
        if (spawned) {
            this.spawnDisplay(player, state.badge, counters);
            this.spawnDisplay(player, state.distance, counters);
        } else {
            if (positionDue) {
                this.sendPosition(player, state.badge);
                this.sendPosition(player, state.distance);
                counters.moves += 2;
            }
            if (badgeChanged || badgeStyleChanged) this.sendMetadata(player, state.badge, counters);
            if (distanceChanged || sizeChanged) this.sendMetadata(player, state.distance, counters);
        }
        if (itemSpawned) {
            this.spawnDisplay(player, state.item, counters);
        } else if (state.item != null) {
            if (positionDue) {
                this.sendPosition(player, state.item);
                counters.moves++;
            }
            if (sizeChanged) this.sendMetadata(player, state.item, counters);
        }
        state.size = size;
    }

    private void spawnDisplay(P player, D display, MarkerCounters counters) {
        this.sendSpawn(player, display);
        counters.spawns++;
        this.sendMetadata(player, display, counters);
    }

    private void sendMetadata(P player, D display, MarkerCounters counters) {
        if (this.sendEntityData(player, display)) counters.metadata++;
    }

    private void removeDisplay(P player) {
        UUID uuid = this.playerUuid(player);
        this.motion.remove(uuid);
        DisplayState<D> state = this.displays.remove(uuid);
        if (state != null) {
            this.sendRemove(player, state.badge);
            this.sendRemove(player, state.distance);
            if (state.item != null) this.sendRemove(player, state.item);
            MarkerCounters counters = this.markerCounters.get(uuid);
            if (counters != null) counters.removals += state.item == null ? 2 : 3;
        }
    }

    private static final class MarkerCounters {
        private long updates;
        private long spawns;
        private long moves;
        private long metadata;
        private long removals;
        private long updateNanos;
    }

    private static final class DisplayState<D> {
        private final D badge;
        private final D distance;
        private D item;
        private NamespacedId iconId;
        private final int hostEntityId;
        private final Object worldIdentity;
        private int rgb;
        private float size;
        private long nextPositionTick;
        private Component badgeText;
        private Component distanceText;
        private Object locale;

        private DisplayState(D badge, D distance, int hostEntityId, Object worldIdentity, int rgb, float size) {
            this.badge = badge;
            this.distance = distance;
            this.hostEntityId = hostEntityId;
            this.worldIdentity = worldIdentity;
            this.rgb = rgb;
            this.size = size;
        }

        public D badge() { return this.badge; }
        public D distance() { return this.distance; }
        public D item() { return this.item; }
    }
}
