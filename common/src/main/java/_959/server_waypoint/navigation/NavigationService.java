package _959.server_waypoint.navigation;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns server-thread-only, per-player navigation sessions and delegates all
 * platform effects to method handlers.
 */
public final class NavigationService<P> {
    public static final int DEFAULT_UPDATE_INTERVAL_TICKS = 5;

    private final NavigationPlatform<P> platform;
    private final Map<NavigationMethod, NavigationMethodHandler<P>> handlers;
    private final Map<UUID, NavigationSession> sessions = new LinkedHashMap<>();
    private final Set<NavigationMethod> defaultMethods;
    private final int updateIntervalTicks;
    private int tickCounter;

    public NavigationService(
            NavigationPlatform<P> platform,
            Collection<? extends NavigationMethodHandler<P>> handlers
    ) {
        this(platform, handlers, NavigationMethod.defaultSelection(), DEFAULT_UPDATE_INTERVAL_TICKS);
    }

    public NavigationService(
            NavigationPlatform<P> platform,
            Collection<? extends NavigationMethodHandler<P>> handlers,
            Set<NavigationMethod> defaultMethods
    ) {
        this(platform, handlers, defaultMethods, DEFAULT_UPDATE_INTERVAL_TICKS);
    }

    public NavigationService(
            NavigationPlatform<P> platform,
            Collection<? extends NavigationMethodHandler<P>> handlers,
            Set<NavigationMethod> defaultMethods,
            int updateIntervalTicks
    ) {
        this.platform = Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(handlers, "handlers");
        Objects.requireNonNull(defaultMethods, "defaultMethods");
        if (defaultMethods.isEmpty()) {
            throw new IllegalArgumentException("At least one default navigation method is required");
        }
        if (updateIntervalTicks < 1) {
            throw new IllegalArgumentException("Update interval must be at least one tick");
        }

        this.handlers = new EnumMap<>(NavigationMethod.class);
        for (NavigationMethodHandler<P> handler : handlers) {
            Objects.requireNonNull(handler, "handler");
            NavigationMethod method = Objects.requireNonNull(handler.method(), "handler.method()");
            if (this.handlers.putIfAbsent(method, handler) != null) {
                throw new IllegalArgumentException("Duplicate handler for navigation method " + method.id());
            }
        }
        this.defaultMethods = NavigationMethod.immutableSet(defaultMethods);
        this.updateIntervalTicks = updateIntervalTicks;
    }

    /**
     * Starts navigation with the configured default methods, or retargets an
     * existing session without changing its enabled methods.
     */
    public NavigationResult navigate(P player, NavigationTarget target) {
        this.platform.assertServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        UUID playerUuid = this.platform.playerUuid(player);
        if (this.sessions.containsKey(playerUuid)) {
            return this.retargetInternal(player, playerUuid, target);
        }
        return this.replaceSelection(player, playerUuid, target, this.defaultMethods);
    }

    /**
     * Starts or retargets navigation while replacing the complete method
     * selection atomically.
     */
    public NavigationResult navigate(
            P player,
            NavigationTarget target,
            Set<NavigationMethod> selection
    ) {
        this.platform.assertServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(selection, "selection");
        return this.replaceSelection(
                player,
                this.platform.playerUuid(player),
                target,
                NavigationMethod.immutableSet(selection)
        );
    }

    public NavigationResult retarget(P player, NavigationTarget target) {
        this.platform.assertServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        return this.retargetInternal(player, this.platform.playerUuid(player), target);
    }

    public NavigationResult enableMethod(P player, NavigationMethod method) {
        this.platform.assertServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(method, "method");
        UUID playerUuid = this.platform.playerUuid(player);
        NavigationSession currentSession = this.sessions.get(playerUuid);
        if (currentSession == null) {
            return NavigationResult.result(NavigationResult.Code.NO_ACTIVE_SESSION, null, method);
        }
        if (currentSession.isEnabled(method)) {
            return NavigationResult.result(
                    NavigationResult.Code.METHOD_ALREADY_ENABLED,
                    currentSession,
                    method
            );
        }

        NavigationMethodHandler<P> handler = this.handlers.get(method);
        if (handler == null) {
            return NavigationResult.result(
                    NavigationResult.Code.METHOD_UNAVAILABLE,
                    currentSession,
                    method
            );
        }

        EnumSet<NavigationMethod> proposedMethods = copyMethods(currentSession.enabledMethods());
        proposedMethods.add(method);
        NavigationSession proposedSession = currentSession.withEnabledMethods(proposedMethods);
        NavigationResult preflight = this.preflight(player, currentSession, proposedSession);
        if (!preflight.successful()) {
            return preflight.withSession(currentSession).withMethod(method);
        }

        NavigationSnapshot snapshot = this.platform.snapshot(player, currentSession.target());
        NavigationResult enableResult = this.enableHandler(player, proposedSession, snapshot, handler);
        if (!enableResult.successful()) {
            this.tryDisable(player, proposedSession, handler);
            return enableResult.withSession(currentSession).withMethod(method);
        }

        this.sessions.put(playerUuid, proposedSession);
        this.persistSession(player, proposedSession);
        return NavigationResult.result(
                NavigationResult.Code.METHOD_ENABLED,
                proposedSession,
                method
        );
    }

    public NavigationResult disableMethod(P player, NavigationMethod method) {
        this.platform.assertServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(method, "method");
        UUID playerUuid = this.platform.playerUuid(player);
        NavigationSession currentSession = this.sessions.get(playerUuid);
        if (currentSession == null) {
            return NavigationResult.result(NavigationResult.Code.NO_ACTIVE_SESSION, null, method);
        }
        if (!currentSession.isEnabled(method)) {
            return NavigationResult.result(
                    NavigationResult.Code.METHOD_ALREADY_DISABLED,
                    currentSession,
                    method
            );
        }

        NavigationMethodHandler<P> handler = this.handlers.get(method);
        if (handler == null) {
            return NavigationResult.result(
                    NavigationResult.Code.METHOD_UNAVAILABLE,
                    currentSession,
                    method
            );
        }
        NavigationSnapshot currentSnapshot = this.platform.snapshot(player, currentSession.target());
        if (!this.tryDisable(player, currentSession, handler)) {
            this.restoreHandler(player, currentSession, currentSnapshot, handler);
            return handlerFailure(currentSession, method);
        }
        EnumSet<NavigationMethod> enabledMethods = copyMethods(currentSession.enabledMethods());
        enabledMethods.remove(method);
        NavigationSession updatedSession = currentSession.withEnabledMethods(enabledMethods);
        this.sessions.put(playerUuid, updatedSession);
        this.persistSession(player, updatedSession);
        return NavigationResult.result(
                NavigationResult.Code.METHOD_DISABLED,
                updatedSession,
                method
        );
    }

    public NavigationResult disableAll(P player) {
        this.platform.assertServerThread();
        Objects.requireNonNull(player, "player");
        UUID playerUuid = this.platform.playerUuid(player);
        NavigationSession session = this.sessions.get(playerUuid);
        if (session == null) {
            return NavigationResult.result(NavigationResult.Code.NO_ACTIVE_SESSION, null, null);
        }
        NavigationSnapshot snapshot = session.enabledMethods().isEmpty()
                ? null
                : this.platform.snapshot(player, session.target());
        List<NavigationMethodHandler<P>> disabledHandlers = new ArrayList<>();
        for (NavigationMethod method : session.enabledMethods()) {
            NavigationMethodHandler<P> handler = this.handlers.get(method);
            if (handler == null) {
                this.restoreDisabledHandlers(player, session, snapshot, disabledHandlers);
                return NavigationResult.result(
                        NavigationResult.Code.METHOD_UNAVAILABLE,
                        session,
                        method
                );
            }
            if (!this.tryDisable(player, session, handler)) {
                this.restoreHandler(player, session, snapshot, handler);
                this.restoreDisabledHandlers(player, session, snapshot, disabledHandlers);
                return handlerFailure(session, method);
            }
            disabledHandlers.add(handler);
        }
        this.sessions.remove(playerUuid);
        this.clearPersistedSession(player);
        return NavigationResult.result(NavigationResult.Code.NAVIGATION_DISABLED, null, null);
    }

    /**
     * Restores a saved session after resolving its waypoint identity against
     * the currently loaded waypoint files. Invalid or stale identities are
     * removed so they cannot fail on every future login.
     */
    public NavigationResult restorePersistedSession(P player, WaypointFilesManagerCore waypointFiles) {
        this.platform.assertServerThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(waypointFiles, "waypointFiles");

        Optional<String> encodedSession;
        try {
            encodedSession = Objects.requireNonNull(
                    this.platform.loadPersistedSession(player),
                    "NavigationPlatform.loadPersistedSession returned null"
            );
        } catch (RuntimeException exception) {
            this.reportPersistenceException(player, "load", exception);
            return NavigationResult.result(NavigationResult.Code.NO_ACTIVE_SESSION, null, null);
        }
        if (encodedSession.isEmpty()) {
            return NavigationResult.result(NavigationResult.Code.NO_ACTIVE_SESSION, null, null);
        }

        Optional<StoredNavigationSession> storedSession = NavigationSessionCodec.decode(
                encodedSession.get()
        );
        if (storedSession.isEmpty()) {
            this.clearPersistedSession(player);
            return NavigationResult.failure(NavigationResult.Code.TARGET_UNAVAILABLE);
        }
        Optional<NavigationTarget> target = storedSession.get().resolve(waypointFiles);
        if (target.isEmpty()) {
            this.clearPersistedSession(player);
            return NavigationResult.failure(NavigationResult.Code.TARGET_UNAVAILABLE);
        }
        return this.replaceSelection(
                player,
                this.platform.playerUuid(player),
                target.get(),
                storedSession.get().enabledMethods(),
                true
        );
    }

    public NavigationResult status(P player) {
        this.platform.assertServerThread();
        Objects.requireNonNull(player, "player");
        NavigationSession session = this.sessions.get(this.platform.playerUuid(player));
        if (session == null) {
            return NavigationResult.result(NavigationResult.Code.NO_ACTIVE_SESSION, null, null);
        }
        return NavigationResult.result(NavigationResult.Code.STATUS, session, null);
    }

    public Optional<NavigationSession> findSession(UUID playerUuid) {
        this.platform.assertServerThread();
        return Optional.ofNullable(this.sessions.get(Objects.requireNonNull(playerUuid, "playerUuid")));
    }

    public void tick() {
        this.platform.assertServerThread();
        this.tickCounter++;
        if (this.tickCounter < this.updateIntervalTicks) {
            return;
        }
        this.tickCounter = 0;

        for (NavigationSession session : List.copyOf(this.sessions.values())) {
            boolean hasLiveDisplay = session.enabledMethods().stream()
                    .anyMatch(NavigationMethod::isLiveDisplay);
            if (!hasLiveDisplay) {
                continue;
            }
            Optional<P> player = this.platform.findPlayer(session.playerUuid());
            if (player.isEmpty()) {
                continue;
            }
            NavigationSnapshot snapshot = this.platform.snapshot(player.get(), session.target());
            for (NavigationMethod method : session.enabledMethods()) {
                if (!method.isLiveDisplay()) {
                    continue;
                }
                NavigationMethodHandler<P> handler = this.handlers.get(method);
                if (handler == null) {
                    continue;
                }
                try {
                    handler.update(player.get(), session, snapshot);
                } catch (RuntimeException exception) {
                    this.platform.onHandlerException(session.playerUuid(), method, exception);
                }
            }
        }
    }

    public void removePlayer(UUID playerUuid) {
        this.platform.assertServerThread();
        Objects.requireNonNull(playerUuid, "playerUuid");
        NavigationSession session = this.sessions.get(playerUuid);
        if (session == null) {
            return;
        }
        Optional<P> player = this.platform.findPlayer(playerUuid);
        this.cleanupSession(player.orElse(null), session);
    }

    /**
     * Removes a session while the platform still has the disconnecting player
     * object, ensuring every handler can clean up immediately.
     */
    public void removePlayer(P player) {
        this.platform.assertServerThread();
        Objects.requireNonNull(player, "player");
        UUID playerUuid = this.platform.playerUuid(player);
        NavigationSession session = this.sessions.get(playerUuid);
        if (session == null) {
            return;
        }
        this.cleanupSession(player, session);
    }

    public void shutdown() {
        this.platform.assertServerThread();
        for (NavigationSession session : List.copyOf(this.sessions.values())) {
            Optional<P> player = this.platform.findPlayer(session.playerUuid());
            this.cleanupSession(player.orElse(null), session);
        }
        this.sessions.clear();
        this.tickCounter = 0;
    }

    public int sessionCount() {
        this.platform.assertServerThread();
        return this.sessions.size();
    }

    public Set<NavigationMethod> defaultMethods() {
        return this.defaultMethods;
    }

    private NavigationResult retargetInternal(P player, UUID playerUuid, NavigationTarget target) {
        NavigationSession currentSession = this.sessions.get(playerUuid);
        if (currentSession == null) {
            return NavigationResult.result(NavigationResult.Code.NO_ACTIVE_SESSION, null, null);
        }
        NavigationSession retargetedSession = currentSession.withTarget(target);
        NavigationResult preflight = this.preflight(player, currentSession, retargetedSession);
        if (!preflight.successful()) {
            return preflight.withSession(currentSession);
        }
        if (!retargetedSession.enabledMethods().isEmpty()) {
            NavigationSnapshot currentSnapshot = this.platform.snapshot(player, currentSession.target());
            NavigationSnapshot retargetedSnapshot = this.platform.snapshot(player, target);
            List<NavigationMethodHandler<P>> updatedHandlers = new ArrayList<>();
            for (NavigationMethod method : retargetedSession.enabledMethods()) {
                NavigationMethodHandler<P> handler = this.handlers.get(method);
                if (handler == null) {
                    this.restoreUpdatedHandlers(
                            player,
                            currentSession,
                            currentSnapshot,
                            updatedHandlers,
                            null
                    );
                    return NavigationResult.result(
                            NavigationResult.Code.METHOD_UNAVAILABLE,
                            currentSession,
                            method
                    );
                }
                if (!this.tryUpdate(player, retargetedSession, retargetedSnapshot, handler)) {
                    this.restoreUpdatedHandlers(
                            player,
                            currentSession,
                            currentSnapshot,
                            updatedHandlers,
                            handler
                    );
                    return handlerFailure(currentSession, method);
                }
                updatedHandlers.add(handler);
            }
        }
        this.sessions.put(playerUuid, retargetedSession);
        this.persistSession(player, retargetedSession);
        return NavigationResult.result(
                NavigationResult.Code.TARGET_CHANGED,
                retargetedSession,
                null
        );
    }

    private NavigationResult replaceSelection(
            P player,
            UUID playerUuid,
            NavigationTarget target,
            Set<NavigationMethod> selection
    ) {
        return this.replaceSelection(player, playerUuid, target, selection, false);
    }

    private NavigationResult replaceSelection(
            P player,
            UUID playerUuid,
            NavigationTarget target,
            Set<NavigationMethod> selection,
            boolean allowEmptySelection
    ) {
        NavigationSession currentSession = this.sessions.get(playerUuid);
        if (selection.isEmpty() && !allowEmptySelection) {
            return NavigationResult.result(
                    NavigationResult.Code.INVALID_SELECTION,
                    currentSession,
                    null
            );
        }

        for (NavigationMethod method : selection) {
            if (!this.handlers.containsKey(method)) {
                return NavigationResult.result(
                        NavigationResult.Code.METHOD_UNAVAILABLE,
                        currentSession,
                        method
                );
            }
        }

        NavigationSession proposedSession = new NavigationSession(playerUuid, target, selection);
        NavigationResult preflight = this.preflight(player, currentSession, proposedSession);
        if (!preflight.successful()) {
            return preflight.withSession(currentSession);
        }

        EnumSet<NavigationMethod> currentMethods = currentSession == null
                ? EnumSet.noneOf(NavigationMethod.class)
                : copyMethods(currentSession.enabledMethods());
        EnumSet<NavigationMethod> addedMethods = copyMethods(selection);
        addedMethods.removeAll(currentMethods);
        EnumSet<NavigationMethod> removedMethods = copyMethods(currentMethods);
        removedMethods.removeAll(selection);
        EnumSet<NavigationMethod> retainedMethods = copyMethods(selection);
        retainedMethods.retainAll(currentMethods);

        NavigationSnapshot snapshot = this.platform.snapshot(player, target);
        NavigationSnapshot currentSnapshot = currentSession == null
                ? null
                : this.platform.snapshot(player, currentSession.target());
        List<NavigationMethodHandler<P>> enabledHandlers = new ArrayList<>();
        for (NavigationMethod method : addedMethods) {
            NavigationMethodHandler<P> handler = this.handlers.get(method);
            NavigationResult enableResult = this.enableHandler(player, proposedSession, snapshot, handler);
            if (!enableResult.successful()) {
                this.tryDisable(player, proposedSession, handler);
                this.rollbackEnabledHandlers(player, proposedSession, enabledHandlers);
                return enableResult.withSession(currentSession).withMethod(method);
            }
            enabledHandlers.add(handler);
        }

        List<NavigationMethodHandler<P>> updatedHandlers = new ArrayList<>();
        if (currentSession != null) {
            for (NavigationMethod method : retainedMethods) {
                NavigationMethodHandler<P> handler = this.handlers.get(method);
                if (!this.tryUpdate(player, proposedSession, snapshot, handler)) {
                    this.restoreUpdatedHandlers(
                            player,
                            currentSession,
                            currentSnapshot,
                            updatedHandlers,
                            handler
                    );
                    this.rollbackEnabledHandlers(player, proposedSession, enabledHandlers);
                    return handlerFailure(currentSession, method);
                }
                updatedHandlers.add(handler);
            }
        }

        List<NavigationMethodHandler<P>> disabledHandlers = new ArrayList<>();
        if (currentSession != null) {
            for (NavigationMethod method : removedMethods) {
                NavigationMethodHandler<P> handler = this.handlers.get(method);
                if (!this.tryDisable(player, currentSession, handler)) {
                    this.restoreHandler(player, currentSession, currentSnapshot, handler);
                    this.restoreDisabledHandlers(
                            player,
                            currentSession,
                            currentSnapshot,
                            disabledHandlers
                    );
                    this.restoreUpdatedHandlers(
                            player,
                            currentSession,
                            currentSnapshot,
                            updatedHandlers,
                            null
                    );
                    this.rollbackEnabledHandlers(player, proposedSession, enabledHandlers);
                    return handlerFailure(currentSession, method);
                }
                disabledHandlers.add(handler);
            }
        }

        this.sessions.put(playerUuid, proposedSession);
        this.persistSession(player, proposedSession);
        NavigationResult.Code resultCode = currentSession == null
                ? NavigationResult.Code.NAVIGATION_STARTED
                : NavigationResult.Code.SELECTION_REPLACED;
        return NavigationResult.result(resultCode, proposedSession, null);
    }

    private NavigationResult preflight(
            P player,
            @Nullable NavigationSession currentSession,
            NavigationSession proposedSession
    ) {
        NavigationResult result = Objects.requireNonNull(
                this.platform.preflight(player, currentSession, proposedSession),
                "NavigationPlatform.preflight returned null"
        );
        return result.withSession(result.successful() ? proposedSession : currentSession);
    }

    private NavigationResult enableHandler(
            P player,
            NavigationSession session,
            NavigationSnapshot snapshot,
            NavigationMethodHandler<P> handler
    ) {
        try {
            return Objects.requireNonNull(
                    handler.enable(player, session, snapshot),
                    "NavigationMethodHandler.enable returned null"
            );
        } catch (RuntimeException exception) {
            this.platform.onHandlerException(session.playerUuid(), handler.method(), exception);
            return NavigationResult.failure(NavigationResult.Code.HANDLER_FAILED)
                    .withSession(session)
                    .withMethod(handler.method());
        }
    }

    private void rollbackEnabledHandlers(
            P player,
            NavigationSession proposedSession,
            List<NavigationMethodHandler<P>> enabledHandlers
    ) {
        for (int index = enabledHandlers.size() - 1; index >= 0; index--) {
            this.tryDisable(player, proposedSession, enabledHandlers.get(index));
        }
    }

    private void restoreUpdatedHandlers(
            P player,
            NavigationSession currentSession,
            NavigationSnapshot currentSnapshot,
            List<NavigationMethodHandler<P>> updatedHandlers,
            @Nullable NavigationMethodHandler<P> failedHandler
    ) {
        if (failedHandler != null) {
            this.tryUpdate(player, currentSession, currentSnapshot, failedHandler);
        }
        for (int index = updatedHandlers.size() - 1; index >= 0; index--) {
            this.tryUpdate(player, currentSession, currentSnapshot, updatedHandlers.get(index));
        }
    }

    private void restoreDisabledHandlers(
            P player,
            NavigationSession currentSession,
            @Nullable NavigationSnapshot currentSnapshot,
            List<NavigationMethodHandler<P>> disabledHandlers
    ) {
        for (int index = disabledHandlers.size() - 1; index >= 0; index--) {
            this.restoreHandler(player, currentSession, currentSnapshot, disabledHandlers.get(index));
        }
    }

    private void restoreHandler(
            P player,
            NavigationSession currentSession,
            @Nullable NavigationSnapshot currentSnapshot,
            NavigationMethodHandler<P> handler
    ) {
        if (currentSnapshot == null) {
            return;
        }
        NavigationResult result = this.enableHandler(player, currentSession, currentSnapshot, handler);
        if (!result.successful()) {
            this.platform.onHandlerException(
                    currentSession.playerUuid(),
                    handler.method(),
                    new IllegalStateException(
                            "Could not restore navigation handler after rollback: " + result.code()
                    )
            );
        }
    }

    private boolean tryUpdate(
            P player,
            NavigationSession session,
            NavigationSnapshot snapshot,
            NavigationMethodHandler<P> handler
    ) {
        try {
            handler.update(player, session, snapshot);
            return true;
        } catch (RuntimeException exception) {
            this.platform.onHandlerException(session.playerUuid(), handler.method(), exception);
            return false;
        }
    }

    private boolean tryDisable(
            P player,
            NavigationSession session,
            NavigationMethodHandler<P> handler
    ) {
        try {
            handler.disable(player, session);
            return true;
        } catch (RuntimeException exception) {
            this.platform.onHandlerException(session.playerUuid(), handler.method(), exception);
            return false;
        }
    }

    private void cleanupSession(@Nullable P player, NavigationSession session) {
        try {
            for (NavigationMethod method : session.enabledMethods()) {
                NavigationMethodHandler<P> handler = this.handlers.get(method);
                if (handler == null) {
                    continue;
                }
                if (player != null) {
                    this.tryDisable(player, session, handler);
                }
                this.tryCleanupPlayer(session, handler);
            }
        } finally {
            this.sessions.remove(session.playerUuid());
        }
    }

    private void tryCleanupPlayer(
            NavigationSession session,
            NavigationMethodHandler<P> handler
    ) {
        try {
            handler.cleanupPlayer(session.playerUuid(), session);
        } catch (RuntimeException exception) {
            this.platform.onHandlerException(session.playerUuid(), handler.method(), exception);
        }
    }

    private static NavigationResult handlerFailure(
            NavigationSession currentSession,
            NavigationMethod method
    ) {
        return NavigationResult.result(
                NavigationResult.Code.HANDLER_FAILED,
                currentSession,
                method
        );
    }

    private void persistSession(P player, NavigationSession session) {
        try {
            this.platform.savePersistedSession(player, NavigationSessionCodec.encode(session));
        } catch (RuntimeException exception) {
            this.reportPersistenceException(player, "save", exception);
        }
    }

    private void clearPersistedSession(P player) {
        try {
            this.platform.clearPersistedSession(player);
        } catch (RuntimeException exception) {
            this.reportPersistenceException(player, "clear", exception);
        }
    }

    private void reportPersistenceException(
            P player,
            String operation,
            RuntimeException exception
    ) {
        try {
            this.platform.onPersistenceException(
                    this.platform.playerUuid(player),
                    operation,
                    exception
            );
        } catch (RuntimeException ignored) {
        }
    }

    private static EnumSet<NavigationMethod> copyMethods(Set<NavigationMethod> methods) {
        return methods.isEmpty()
                ? EnumSet.noneOf(NavigationMethod.class)
                : EnumSet.copyOf(methods);
    }
}
