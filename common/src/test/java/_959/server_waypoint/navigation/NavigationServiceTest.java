package _959.server_waypoint.navigation;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationServiceTest {
    private TestPlatform platform;
    private Map<NavigationMethod, TestHandler> handlers;
    private NavigationService<TestPlayer> service;
    private TestPlayer firstPlayer;
    private TestPlayer secondPlayer;

    @BeforeEach
    void setUp() {
        this.platform = new TestPlatform();
        this.handlers = new EnumMap<>(NavigationMethod.class);
        for (NavigationMethod method : NavigationMethod.values()) {
            this.handlers.put(method, new TestHandler(method));
        }
        this.service = new NavigationService<>(this.platform, this.handlers.values());
        this.firstPlayer = this.platform.addPlayer();
        this.secondPlayer = this.platform.addPlayer();
    }

    @Test
    void initialNavigationUsesActionbarAndSessionsAreIsolatedPerPlayer() {
        NavigationTarget firstTarget = target("First", 10, 64, 0);
        NavigationTarget secondTarget = target("Second", -10, 64, 0);

        NavigationResult firstResult = this.service.navigate(this.firstPlayer, firstTarget);
        NavigationResult secondResult = this.service.navigate(this.secondPlayer, secondTarget);

        assertEquals(NavigationResult.Code.NAVIGATION_STARTED, firstResult.code());
        assertEquals(Set.of(NavigationMethod.ACTIONBAR), firstResult.session().enabledMethods());
        assertEquals(firstTarget, this.service.findSession(this.firstPlayer.uuid()).orElseThrow().target());
        assertEquals(secondTarget, this.service.findSession(this.secondPlayer.uuid()).orElseThrow().target());
        assertEquals(2, this.handlers.get(NavigationMethod.ACTIONBAR).enableCount);
        assertEquals(2, this.service.sessionCount());
    }

    @Test
    void navigationWithoutUsingRetargetsAndPreservesEnabledMethods() {
        Set<NavigationMethod> methods = Set.of(NavigationMethod.COMPASS, NavigationMethod.BOSSBAR);
        this.service.navigate(this.firstPlayer, target("Old", 10, 64, 0), methods);
        NavigationTarget newTarget = target("New", 20, 70, 5);

        NavigationResult result = this.service.navigate(this.firstPlayer, newTarget);

        assertEquals(NavigationResult.Code.TARGET_CHANGED, result.code());
        assertEquals(newTarget, result.session().target());
        assertEquals(methods, result.session().enabledMethods());
        assertEquals(1, this.handlers.get(NavigationMethod.COMPASS).updateCount);
        assertEquals(1, this.handlers.get(NavigationMethod.BOSSBAR).updateCount);
    }

    @Test
    void retargetPreflightFailurePreservesTargetAndHandlerState() {
        NavigationTarget oldTarget = target("Old", 10, 64, 0);
        Set<NavigationMethod> methods = Set.of(NavigationMethod.COMPASS, NavigationMethod.BOSSBAR);
        this.service.navigate(this.firstPlayer, oldTarget, methods);
        this.platform.nextPreflightResult = NavigationResult.failure(
                NavigationResult.Code.TARGET_UNAVAILABLE
        );

        NavigationResult result = this.service.retarget(
                this.firstPlayer,
                target("New", 20, 64, 0)
        );

        assertEquals(NavigationResult.Code.TARGET_UNAVAILABLE, result.code());
        assertEquals(oldTarget, result.session().target());
        assertEquals(oldTarget, this.service.findSession(this.firstPlayer.uuid()).orElseThrow().target());
        assertEquals(methods, this.platform.lastProposedSession.enabledMethods());
        assertEquals(0, this.handlers.get(NavigationMethod.COMPASS).updateCount);
        assertEquals(0, this.handlers.get(NavigationMethod.BOSSBAR).updateCount);
    }

    @Test
    void retargetHandlerFailureRestoresEveryRetainedHandlerAndOldSession() {
        NavigationTarget oldTarget = target("Old", 10, 64, 0);
        NavigationTarget newTarget = target("New", 20, 64, 0);
        this.service.navigate(
                this.firstPlayer,
                oldTarget,
                Set.of(NavigationMethod.COMPASS, NavigationMethod.BOSSBAR)
        );
        TestHandler compass = this.handlers.get(NavigationMethod.COMPASS);
        TestHandler bossbar = this.handlers.get(NavigationMethod.BOSSBAR);
        compass.seenSessions.clear();
        bossbar.seenSessions.clear();
        bossbar.failNextUpdate = true;

        NavigationResult result = this.service.retarget(this.firstPlayer, newTarget);

        assertEquals(NavigationResult.Code.HANDLER_FAILED, result.code());
        assertEquals(NavigationMethod.BOSSBAR, result.method());
        assertEquals(oldTarget, result.session().target());
        assertEquals(oldTarget, this.service.findSession(this.firstPlayer.uuid()).orElseThrow().target());
        assertEquals(List.of("New", "Old"), compass.seenWaypointNames());
        assertEquals(List.of("New", "Old"), bossbar.seenWaypointNames());
    }

    @Test
    void refreshTargetUpdatesEveryMatchingSessionHandlerAndPersistence() {
        NavigationTarget oldTarget = target("Old", 10, 64, 0);
        NavigationTarget otherTarget = target("Other", 30, 64, 0);
        Set<NavigationMethod> methods = NavigationMethod.definedMethods();
        this.service.navigate(this.firstPlayer, oldTarget, methods);
        this.service.navigate(this.secondPlayer, oldTarget, methods);
        TestPlayer otherPlayer = this.platform.addPlayer();
        this.service.navigate(otherPlayer, otherTarget, methods);
        this.platform.findPlayerRequests.clear();
        for (TestHandler handler : this.handlers.values()) {
            handler.updateCount = 0;
            handler.seenSessions.clear();
        }
        NavigationTarget updatedTarget = new NavigationTarget(
                oldTarget.dimensionName(),
                oldTarget.listName(),
                oldTarget.listDisplayName(),
                "Renamed",
                "Renamed",
                "Updated description",
                new WaypointPos(80, 75, -40),
                0xABCDEF
        );

        this.service.refreshTarget(oldTarget, updatedTarget);

        assertEquals(2, this.platform.findPlayerRequests.size());
        assertEquals(
                Set.of(this.firstPlayer.uuid(), this.secondPlayer.uuid()),
                Set.copyOf(this.platform.findPlayerRequests)
        );
        assertEquals(updatedTarget, this.service.findSession(this.firstPlayer.uuid())
                .orElseThrow()
                .target());
        assertEquals(updatedTarget, this.service.findSession(this.secondPlayer.uuid())
                .orElseThrow()
                .target());
        assertEquals(otherTarget, this.service.findSession(otherPlayer.uuid())
                .orElseThrow()
                .target());
        for (TestHandler handler : this.handlers.values()) {
            assertEquals(2, handler.updateCount);
            assertEquals(List.of("Renamed", "Renamed"), handler.seenWaypointNames());
        }
        assertEquals(
                "Renamed",
                NavigationSessionCodec.decode(
                        this.platform.persistedSessions.get(this.firstPlayer.uuid())
                ).orElseThrow().waypointName()
        );
        assertEquals(
                "Renamed",
                NavigationSessionCodec.decode(
                        this.platform.persistedSessions.get(this.secondPlayer.uuid())
                ).orElseThrow().waypointName()
        );
        assertEquals(
                "Other",
                NavigationSessionCodec.decode(
                        this.platform.persistedSessions.get(otherPlayer.uuid())
                ).orElseThrow().waypointName()
        );

        this.platform.findPlayerRequests.clear();
        this.service.refreshTarget(oldTarget, updatedTarget);
        assertTrue(this.platform.findPlayerRequests.isEmpty());

        this.service.disableAll(this.firstPlayer);
        this.platform.findPlayerRequests.clear();
        NavigationTarget renamedAgain = new NavigationTarget(
                updatedTarget.dimensionName(),
                updatedTarget.listName(),
                updatedTarget.listDisplayName(),
                "Renamed Again",
                "Renamed Again",
                updatedTarget.waypointDescription(),
                updatedTarget.position(),
                updatedTarget.rgb()
        );
        this.service.refreshTarget(updatedTarget, renamedAgain);
        assertEquals(List.of(this.secondPlayer.uuid()), this.platform.findPlayerRequests);
        assertEquals(renamedAgain, this.service.findSession(this.secondPlayer.uuid())
                .orElseThrow()
                .target());
    }

    @Test
    void delayedRefreshDoesNotOverwriteANewerPlayerTarget() {
        NavigationTarget oldTarget = target("Old", 10, 64, 0);
        NavigationTarget updatedTarget = target("Updated", 20, 64, 0);
        NavigationTarget newerTarget = target("Newer", 30, 64, 0);
        this.service.navigate(this.firstPlayer, oldTarget);
        this.platform.deferPlayerActions = true;

        this.service.refreshTarget(oldTarget, updatedTarget);
        this.service.retarget(this.firstPlayer, newerTarget);
        this.platform.runDeferredPlayerActions();

        assertEquals(
                newerTarget,
                this.service.findSession(this.firstPlayer.uuid()).orElseThrow().target()
        );
    }

    @Test
    void explicitSelectionReplacesExistingMethods() {
        this.service.navigate(this.firstPlayer, target("Old", 10, 64, 0));
        Set<NavigationMethod> replacement = Set.of(NavigationMethod.COMPASS, NavigationMethod.MAP);

        NavigationResult result = this.service.navigate(
                this.firstPlayer,
                target("New", 20, 64, 0),
                replacement
        );

        assertEquals(NavigationResult.Code.SELECTION_REPLACED, result.code());
        assertEquals(replacement, result.session().enabledMethods());
        assertEquals(1, this.handlers.get(NavigationMethod.ACTIONBAR).disableCount);
        assertEquals(1, this.handlers.get(NavigationMethod.COMPASS).enableCount);
        assertEquals(1, this.handlers.get(NavigationMethod.MAP).enableCount);
    }

    @Test
    void explicitIdenticalSelectionRefreshesEveryRetainedHandler() {
        NavigationTarget target = target("Target", 10, 64, 0);
        Set<NavigationMethod> methods = Set.of(NavigationMethod.COMPASS, NavigationMethod.BOSSBAR);
        this.service.navigate(this.firstPlayer, target, methods);

        NavigationResult result = this.service.navigate(this.firstPlayer, target, methods);

        assertEquals(NavigationResult.Code.SELECTION_REPLACED, result.code());
        assertEquals(1, this.handlers.get(NavigationMethod.COMPASS).updateCount);
        assertEquals(1, this.handlers.get(NavigationMethod.BOSSBAR).updateCount);
        assertEquals(target, this.service.findSession(this.firstPlayer.uuid()).orElseThrow().target());
    }

    @Test
    void replacingWithEveryDefinedMethodPreflightFailureIsAtomicAndCarriesSlotCounts() {
        NavigationTarget oldTarget = target("Old", 10, 64, 0);
        this.service.navigate(this.firstPlayer, oldTarget);
        this.platform.nextPreflightResult = NavigationResult.insufficientInventory(2, 1);

        NavigationResult result = this.service.navigate(
                this.firstPlayer,
                target("New", 20, 64, 0),
                NavigationMethod.definedMethods()
        );

        assertEquals(NavigationResult.Code.INSUFFICIENT_INVENTORY, result.code());
        assertEquals(2, result.requiredSlots());
        assertEquals(1, result.availableSlots());
        assertEquals(oldTarget, result.session().target());
        assertEquals(Set.of(NavigationMethod.ACTIONBAR), result.session().enabledMethods());
        assertEquals(
                NavigationMethod.definedMethods(),
                this.platform.lastProposedSession.enabledMethods()
        );
        assertEquals(0, this.handlers.get(NavigationMethod.COMPASS).enableCount);
        assertEquals(0, this.handlers.get(NavigationMethod.MAP).enableCount);
        assertEquals(0, this.handlers.get(NavigationMethod.ACTIONBAR).disableCount);
    }

    @Test
    void handlerFailureRollsBackNewMethodsAndPreservesOldSession() {
        NavigationTarget oldTarget = target("Old", 10, 64, 0);
        this.service.navigate(this.firstPlayer, oldTarget);
        this.handlers.get(NavigationMethod.MAP).enableResult = NavigationResult.failure(
                NavigationResult.Code.HANDLER_FAILED
        );

        NavigationResult result = this.service.navigate(
                this.firstPlayer,
                target("New", 20, 64, 0),
                Set.of(NavigationMethod.COMPASS, NavigationMethod.MAP)
        );

        assertEquals(NavigationResult.Code.HANDLER_FAILED, result.code());
        assertEquals(oldTarget, result.session().target());
        assertEquals(Set.of(NavigationMethod.ACTIONBAR), result.session().enabledMethods());
        assertEquals(1, this.handlers.get(NavigationMethod.COMPASS).enableCount);
        assertEquals(1, this.handlers.get(NavigationMethod.COMPASS).disableCount);
        assertEquals(1, this.handlers.get(NavigationMethod.MAP).disableCount);
        assertEquals(0, this.handlers.get(NavigationMethod.ACTIONBAR).disableCount);
    }

    @Test
    void methodEnableAndDisableAreIdempotent() {
        this.service.navigate(this.firstPlayer, target("Target", 10, 64, 0));

        NavigationResult enabled = this.service.enableMethod(this.firstPlayer, NavigationMethod.BOSSBAR);
        NavigationResult alreadyEnabled = this.service.enableMethod(this.firstPlayer, NavigationMethod.BOSSBAR);
        NavigationResult disabled = this.service.disableMethod(this.firstPlayer, NavigationMethod.BOSSBAR);
        NavigationResult alreadyDisabled = this.service.disableMethod(this.firstPlayer, NavigationMethod.BOSSBAR);

        assertEquals(NavigationResult.Code.METHOD_ENABLED, enabled.code());
        assertEquals(NavigationResult.Code.METHOD_ALREADY_ENABLED, alreadyEnabled.code());
        assertEquals(NavigationResult.Code.METHOD_DISABLED, disabled.code());
        assertEquals(NavigationResult.Code.METHOD_ALREADY_DISABLED, alreadyDisabled.code());
        assertEquals(1, this.handlers.get(NavigationMethod.BOSSBAR).enableCount);
        assertEquals(1, this.handlers.get(NavigationMethod.BOSSBAR).disableCount);
        assertEquals(Set.of(NavigationMethod.ACTIONBAR), alreadyDisabled.session().enabledMethods());
    }

    @Test
    void disableMethodFailureRestoresHandlerAndKeepsMethodEnabled() {
        NavigationTarget target = target("Target", 10, 64, 0);
        this.service.navigate(this.firstPlayer, target);
        TestHandler actionbar = this.handlers.get(NavigationMethod.ACTIONBAR);
        actionbar.failNextDisable = true;

        NavigationResult result = this.service.disableMethod(
                this.firstPlayer,
                NavigationMethod.ACTIONBAR
        );

        assertEquals(NavigationResult.Code.HANDLER_FAILED, result.code());
        assertEquals(NavigationMethod.ACTIONBAR, result.method());
        assertEquals(Set.of(NavigationMethod.ACTIONBAR), result.session().enabledMethods());
        assertEquals(Set.of(NavigationMethod.ACTIONBAR), this.service.findSession(
                this.firstPlayer.uuid()
        ).orElseThrow().enabledMethods());
        assertEquals(1, actionbar.disableCount);
        assertEquals(2, actionbar.enableCount);
    }

    @Test
    void disableAllFailureRestoresPreviouslyDisabledHandlersAndSession() {
        Set<NavigationMethod> methods = Set.of(NavigationMethod.COMPASS, NavigationMethod.MAP);
        this.service.navigate(this.firstPlayer, target("Target", 10, 64, 0), methods);
        TestHandler compass = this.handlers.get(NavigationMethod.COMPASS);
        TestHandler map = this.handlers.get(NavigationMethod.MAP);
        map.failNextDisable = true;

        NavigationResult result = this.service.disableAll(this.firstPlayer);

        assertEquals(NavigationResult.Code.HANDLER_FAILED, result.code());
        assertEquals(NavigationMethod.MAP, result.method());
        assertEquals(methods, result.session().enabledMethods());
        assertEquals(methods, this.service.findSession(this.firstPlayer.uuid())
                .orElseThrow()
                .enabledMethods());
        assertEquals(1, compass.disableCount);
        assertEquals(2, compass.enableCount);
        assertEquals(1, map.disableCount);
        assertEquals(2, map.enableCount);
    }

    @Test
    void selectionReplacementDisableFailureRestoresOldEffectsAndSession() {
        NavigationTarget oldTarget = target("Old", 10, 64, 0);
        NavigationTarget newTarget = target("New", 20, 64, 0);
        Set<NavigationMethod> oldMethods = Set.of(
                NavigationMethod.COMPASS,
                NavigationMethod.ACTIONBAR
        );
        this.service.navigate(this.firstPlayer, oldTarget, oldMethods);
        TestHandler compass = this.handlers.get(NavigationMethod.COMPASS);
        TestHandler map = this.handlers.get(NavigationMethod.MAP);
        TestHandler actionbar = this.handlers.get(NavigationMethod.ACTIONBAR);
        compass.seenSessions.clear();
        map.seenSessions.clear();
        actionbar.seenSessions.clear();
        compass.failNextDisable = true;

        NavigationResult result = this.service.navigate(
                this.firstPlayer,
                newTarget,
                Set.of(NavigationMethod.MAP, NavigationMethod.ACTIONBAR)
        );

        assertEquals(NavigationResult.Code.HANDLER_FAILED, result.code());
        assertEquals(NavigationMethod.COMPASS, result.method());
        assertEquals(oldTarget, result.session().target());
        assertEquals(oldMethods, result.session().enabledMethods());
        assertEquals(oldTarget, this.service.findSession(this.firstPlayer.uuid()).orElseThrow().target());
        assertEquals(List.of("Old", "Old"), compass.seenWaypointNames());
        assertEquals(List.of("New", "New"), map.seenWaypointNames());
        assertEquals(List.of("New", "Old"), actionbar.seenWaypointNames());
        assertEquals(1, compass.disableCount);
        assertEquals(2, compass.enableCount);
        assertEquals(1, map.disableCount);
    }

    @Test
    void disablingLastMethodKeepsTargetedSessionUntilDisableAll() {
        this.service.navigate(this.firstPlayer, target("Target", 10, 64, 0));

        NavigationResult methodDisabled = this.service.disableMethod(
                this.firstPlayer,
                NavigationMethod.ACTIONBAR
        );
        NavigationResult status = this.service.status(this.firstPlayer);
        NavigationResult allDisabled = this.service.disableAll(this.firstPlayer);

        assertTrue(methodDisabled.session().enabledMethods().isEmpty());
        assertEquals(NavigationResult.Code.STATUS, status.code());
        assertTrue(status.session().enabledMethods().isEmpty());
        assertEquals(NavigationResult.Code.NAVIGATION_DISABLED, allDisabled.code());
        assertTrue(this.service.findSession(this.firstPlayer.uuid()).isEmpty());
    }

    @Test
    void successfulMutationsPersistAndDisconnectPreservesSessionRecord() {
        this.service.navigate(this.firstPlayer, target("Old", 10, 64, 0));
        this.service.enableMethod(this.firstPlayer, NavigationMethod.COMPASS);
        NavigationTarget newTarget = target("New", 20, 70, 5);
        this.service.retarget(this.firstPlayer, newTarget);
        this.service.disableMethod(this.firstPlayer, NavigationMethod.ACTIONBAR);

        StoredNavigationSession stored = NavigationSessionCodec.decode(
                this.platform.persistedSessions.get(this.firstPlayer.uuid())
        ).orElseThrow();
        assertEquals("New", stored.waypointName());
        assertEquals(Set.of(NavigationMethod.COMPASS), stored.enabledMethods());

        this.service.removePlayer(this.firstPlayer);

        assertTrue(this.service.findSession(this.firstPlayer.uuid()).isEmpty());
        assertTrue(this.platform.persistedSessions.containsKey(this.firstPlayer.uuid()));
    }

    @Test
    void textDisplayTransformationComponentsApplyAndPersistIndependently() {
        this.service.navigate(
                this.firstPlayer,
                target("Target", 10, 64, 0),
                Set.of(NavigationMethod.TEXT_DISPLAY)
        );
        TestHandler handler = this.handlers.get(NavigationMethod.TEXT_DISPLAY);
        Vector3f translation = new Vector3f(1.0F, 2.0F, 3.0F);
        Vector3f rotation = new Vector3f(10.0F, 20.0F, 30.0F);
        Vector3f scale = new Vector3f(1.0F, 2.0F, 1.0F);

        NavigationResult translated = this.service.updateTextDisplayTranslation(
                this.firstPlayer,
                translation
        );
        NavigationResult rotated = this.service.updateTextDisplayRotation(
                this.firstPlayer,
                rotation
        );
        NavigationResult scaled = this.service.updateTextDisplayScale(
                this.firstPlayer,
                scale
        );

        assertEquals(NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED, translated.code());
        assertEquals(NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED, rotated.code());
        assertEquals(NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED, scaled.code());
        TextDisplayTransformation transformation = scaled.session().textDisplayTransformation();
        assertEquals(translation, transformation.translation());
        assertEquals(rotation, transformation.rotation());
        assertEquals(scale, transformation.scale());
        assertEquals(transformation.resolvedTranslation(), handler.lastTranslation);
        assertEquals(transformation.resolvedScale(), handler.lastScale);
        assertEquals(transformation.rotationQuaternion(), handler.lastRotation);
        assertEquals(3, handler.transformationCount);
        assertEquals(
                transformation,
                NavigationSessionCodec.decode(
                        this.platform.persistedSessions.get(this.firstPlayer.uuid())
                ).orElseThrow().textDisplayTransformation()
        );

        String persistedBeforeFailure = this.platform.persistedSessions.get(
                this.firstPlayer.uuid()
        );
        handler.failNextTransformation = true;
        NavigationResult failed = this.service.updateTextDisplayTranslation(
                this.firstPlayer,
                new Vector3f(4.0F, 5.0F, 6.0F)
        );

        assertEquals(NavigationResult.Code.HANDLER_FAILED, failed.code());
        assertEquals(4, handler.transformationCount);
        assertEquals(1, this.platform.handlerExceptionCount);
        assertEquals(
                transformation,
                this.service.findSession(this.firstPlayer.uuid())
                        .orElseThrow()
                        .textDisplayTransformation()
        );
        assertEquals(
                persistedBeforeFailure,
                this.platform.persistedSessions.get(this.firstPlayer.uuid())
        );
    }

    @Test
    void textDisplayTransformationCanBePreparedWhileMethodIsDisabled() {
        this.service.navigate(this.firstPlayer, target("Target", 10, 64, 0));

        NavigationResult translated = this.service.updateTextDisplayTranslation(
                this.firstPlayer,
                new Vector3f(1.0F, 2.0F, 3.0F)
        );
        NavigationResult rotated = this.service.updateTextDisplayRotation(
                this.firstPlayer,
                new Vector3f(10.0F, 20.0F, 30.0F)
        );
        NavigationResult scaled = this.service.updateTextDisplayScale(
                this.firstPlayer,
                new Vector3f(1.0F, 2.0F, 1.0F)
        );

        assertEquals(NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED, translated.code());
        assertEquals(NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED, rotated.code());
        assertEquals(NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED, scaled.code());
        assertEquals(0, this.handlers.get(NavigationMethod.TEXT_DISPLAY).transformationCount);
        assertEquals(
                scaled.session().textDisplayTransformation(),
                NavigationSessionCodec.decode(
                        this.platform.persistedSessions.get(this.firstPlayer.uuid())
                ).orElseThrow().textDisplayTransformation()
        );

        NavigationResult enabled = this.service.enableMethod(
                this.firstPlayer,
                NavigationMethod.TEXT_DISPLAY
        );
        assertEquals(NavigationResult.Code.METHOD_ENABLED, enabled.code());
        assertEquals(
                scaled.session().textDisplayTransformation(),
                this.handlers.get(NavigationMethod.TEXT_DISPLAY)
                        .seenSessions
                        .get(0)
                        .textDisplayTransformation()
        );
    }

    @Test
    void restoreResolvesCurrentWaypointPropertiesAndRecreatesHandlers() {
        this.service.navigate(
                this.firstPlayer,
                target("Target", 10, 64, 0),
                Set.of(NavigationMethod.COMPASS, NavigationMethod.BOSSBAR)
        );
        this.service.updateTextDisplayTranslation(
                this.firstPlayer,
                new Vector3f(1.0F, 2.0F, 3.0F)
        );
        this.service.updateTextDisplayRotation(
                this.firstPlayer,
                new Vector3f(10.0F, 20.0F, 30.0F)
        );
        this.service.updateTextDisplayScale(
                this.firstPlayer,
                new Vector3f(1.0F, 2.0F, 1.0F)
        );
        TextDisplayTransformation storedTransformation = this.service.findSession(
                this.firstPlayer.uuid()
        ).orElseThrow().textDisplayTransformation();
        this.service.removePlayer(this.firstPlayer);
        TestWaypointServer waypointServer = waypointServer(
                "Target",
                new WaypointPos(80, 75, -40),
                0xABCDEF
        );

        NavigationResult result = this.service.restorePersistedSession(
                this.firstPlayer,
                waypointServer
        );

        assertEquals(NavigationResult.Code.NAVIGATION_STARTED, result.code());
        NavigationSession restored = this.service.findSession(this.firstPlayer.uuid()).orElseThrow();
        assertEquals(new WaypointPos(80, 75, -40), restored.target().position());
        assertEquals(0xABCDEF, restored.target().rgb());
        assertEquals(
                Set.of(NavigationMethod.COMPASS, NavigationMethod.BOSSBAR),
                restored.enabledMethods()
        );
        assertEquals(storedTransformation, restored.textDisplayTransformation());
        assertEquals(2, this.handlers.get(NavigationMethod.COMPASS).enableCount);
        assertEquals(2, this.handlers.get(NavigationMethod.BOSSBAR).enableCount);
    }

    @Test
    void restoreRetainsTargetedSessionWithNoEnabledMethods() {
        this.service.navigate(this.firstPlayer, target("Target", 10, 64, 0));
        this.service.disableMethod(this.firstPlayer, NavigationMethod.ACTIONBAR);
        this.service.removePlayer(this.firstPlayer);

        NavigationResult result = this.service.restorePersistedSession(
                this.firstPlayer,
                waypointServer("Target", new WaypointPos(10, 64, 0), 0x39C5BB)
        );

        assertEquals(NavigationResult.Code.NAVIGATION_STARTED, result.code());
        assertTrue(result.session().enabledMethods().isEmpty());
    }

    @Test
    void invalidOrMissingPersistentTargetIsCleared() {
        this.platform.persistedSessions.put(this.firstPlayer.uuid(), "invalid");

        NavigationResult malformed = this.service.restorePersistedSession(
                this.firstPlayer,
                waypointServer("Target", new WaypointPos(10, 64, 0), 0x39C5BB)
        );

        assertEquals(NavigationResult.Code.TARGET_UNAVAILABLE, malformed.code());
        assertFalse(this.platform.persistedSessions.containsKey(this.firstPlayer.uuid()));

        this.service.navigate(this.firstPlayer, target("Missing", 10, 64, 0));
        this.service.removePlayer(this.firstPlayer);
        NavigationResult missing = this.service.restorePersistedSession(
                this.firstPlayer,
                waypointServer("Other", new WaypointPos(10, 64, 0), 0x39C5BB)
        );

        assertEquals(NavigationResult.Code.TARGET_UNAVAILABLE, missing.code());
        assertFalse(this.platform.persistedSessions.containsKey(this.firstPlayer.uuid()));
    }

    @Test
    void explicitDisableAllClearsPersistentSession() {
        this.service.navigate(this.firstPlayer, target("Target", 10, 64, 0));
        assertTrue(this.platform.persistedSessions.containsKey(this.firstPlayer.uuid()));

        this.service.disableAll(this.firstPlayer);

        assertFalse(this.platform.persistedSessions.containsKey(this.firstPlayer.uuid()));
    }

    @Test
    void operationsThatNeedATargetReportNoActiveSession() {
        assertEquals(
                NavigationResult.Code.NO_ACTIVE_SESSION,
                this.service.retarget(this.firstPlayer, target("Target", 0, 0, 0)).code()
        );
        assertEquals(
                NavigationResult.Code.NO_ACTIVE_SESSION,
                this.service.enableMethod(this.firstPlayer, NavigationMethod.MAP).code()
        );
        assertEquals(
                NavigationResult.Code.NO_ACTIVE_SESSION,
                this.service.disableMethod(this.firstPlayer, NavigationMethod.MAP).code()
        );
        assertEquals(
                NavigationResult.Code.NO_ACTIVE_SESSION,
                this.service.updateTextDisplayTranslation(
                        this.firstPlayer,
                        new Vector3f()
                ).code()
        );
        assertEquals(NavigationResult.Code.NO_ACTIVE_SESSION, this.service.status(this.firstPlayer).code());
    }

    @Test
    void tickSharesOneSnapshotAcrossLiveDisplayHandlersEveryFiveTicks() {
        this.service.navigate(
                this.firstPlayer,
                target("Target", 10, 64, 0),
                NavigationMethod.definedMethods()
        );
        this.platform.snapshotCount = 0;
        for (TestHandler handler : this.handlers.values()) {
            handler.updateCount = 0;
            handler.lastSnapshot = null;
        }

        for (int i = 0; i < 4; i++) {
            this.service.tickPlayer(this.firstPlayer);
        }
        assertEquals(0, this.platform.snapshotCount);

        this.service.tickPlayer(this.firstPlayer);

        assertEquals(1, this.platform.snapshotCount);
        NavigationSnapshot sharedSnapshot = this.handlers.get(NavigationMethod.ACTIONBAR).lastSnapshot;
        assertEquals(0, this.handlers.get(NavigationMethod.COMPASS).updateCount);
        assertEquals(0, this.handlers.get(NavigationMethod.MAP).updateCount);
        assertEquals(1, this.handlers.get(NavigationMethod.BOSSBAR).updateCount);
        assertEquals(1, this.handlers.get(NavigationMethod.ACTIONBAR).updateCount);
        assertSame(sharedSnapshot, this.handlers.get(NavigationMethod.BOSSBAR).lastSnapshot);
    }

    @Test
    void tickDoesNotSnapshotItemOnlySessions() {
        this.service.navigate(
                this.firstPlayer,
                target("Target", 10, 64, 0),
                Set.of(NavigationMethod.COMPASS, NavigationMethod.MAP)
        );
        this.platform.snapshotCount = 0;

        for (int i = 0; i < NavigationService.DEFAULT_UPDATE_INTERVAL_TICKS; i++) {
            this.service.tickPlayer(this.firstPlayer);
        }

        assertEquals(0, this.platform.snapshotCount);
        assertEquals(0, this.handlers.get(NavigationMethod.COMPASS).updateCount);
        assertEquals(0, this.handlers.get(NavigationMethod.MAP).updateCount);
    }

    @Test
    void uuidRemovalAndShutdownCleanHandlersWithoutAccessingPlayers() {
        this.service.navigate(
                this.firstPlayer,
                target("First", 10, 64, 0),
                this.service.supportedNavigationMethods()
        );
        this.service.navigate(
                this.secondPlayer,
                target("Second", 20, 64, 0),
                this.service.supportedNavigationMethods()
        );

        this.service.removePlayer(this.firstPlayer.uuid());

        assertTrue(this.service.findSession(this.firstPlayer.uuid()).isEmpty());
        assertTrue(this.service.findSession(this.secondPlayer.uuid()).isPresent());
        for (TestHandler handler : this.handlers.values()) {
            assertEquals(0, handler.disableCount);
            assertEquals(1, handler.cleanupCount);
        }

        this.service.shutdown();

        assertEquals(0, this.service.sessionCount());
        assertTrue(this.platform.persistedSessions.containsKey(this.firstPlayer.uuid()));
        assertTrue(this.platform.persistedSessions.containsKey(this.secondPlayer.uuid()));
        for (TestHandler handler : this.handlers.values()) {
            assertEquals(0, handler.disableCount);
            assertEquals(2, handler.cleanupCount);
        }
    }

    @Test
    void uuidRemovalCleansUpAndRemovesAnUnresolvedSession() {
        this.service.navigate(this.firstPlayer, target("Target", 10, 64, 0));
        TestHandler actionbar = this.handlers.get(NavigationMethod.ACTIONBAR);
        this.platform.removePlayer(this.firstPlayer.uuid());

        this.service.removePlayer(this.firstPlayer.uuid());

        assertTrue(this.service.findSession(this.firstPlayer.uuid()).isEmpty());
        assertEquals(0, actionbar.disableCount);
        assertEquals(1, actionbar.cleanupCount);
        assertEquals(this.firstPlayer.uuid(), actionbar.lastCleanedPlayerUuid);
    }

    @Test
    void livePlayerRemovalContinuesAfterHandlerFailureAndAlwaysEndsSession() {
        this.service.navigate(
                this.firstPlayer,
                target("Target", 10, 64, 0),
                this.service.supportedNavigationMethods()
        );
        this.handlers.get(NavigationMethod.COMPASS).failNextDisable = true;

        this.service.removePlayer(this.firstPlayer);

        assertTrue(this.service.findSession(this.firstPlayer.uuid()).isEmpty());
        for (TestHandler handler : this.handlers.values()) {
            assertEquals(1, handler.disableCount);
            assertEquals(1, handler.cleanupCount);
        }
        assertEquals(1, this.platform.handlerExceptionCount);
    }

    @Test
    void shutdownInvokesUuidCleanupForUnresolvedSessions() {
        this.service.navigate(this.firstPlayer, target("Target", 10, 64, 0));
        TestHandler actionbar = this.handlers.get(NavigationMethod.ACTIONBAR);
        this.platform.removePlayer(this.firstPlayer.uuid());

        this.service.shutdown();

        assertEquals(0, this.service.sessionCount());
        assertEquals(0, actionbar.disableCount);
        assertEquals(1, actionbar.cleanupCount);
        assertEquals(this.firstPlayer.uuid(), actionbar.lastCleanedPlayerUuid);
    }

    @Test
    void disableAllIsIdempotent() {
        this.service.navigate(this.firstPlayer, target("Target", 10, 64, 0));

        NavigationResult first = this.service.disableAll(this.firstPlayer);
        NavigationResult second = this.service.disableAll(this.firstPlayer);

        assertEquals(NavigationResult.Code.NAVIGATION_DISABLED, first.code());
        assertEquals(NavigationResult.Code.NO_ACTIVE_SESSION, second.code());
        assertEquals(1, this.handlers.get(NavigationMethod.ACTIONBAR).disableCount);
    }

    private static NavigationTarget target(String name, int x, int y, int z) {
        return new NavigationTarget(
                "minecraft:overworld",
                "test-list",
                "test-list",
                name,
                name,
                "",
                new WaypointPos(x, y, z),
                0x39C5BB
        );
    }

    private static TestWaypointServer waypointServer(String name, WaypointPos position, int rgb) {
        TestWaypointServer waypointServer = new TestWaypointServer();
        waypointServer.addWaypoint(
                "minecraft:overworld",
                "test-list",
                new SimpleWaypoint(name, "T", position, rgb, 0, false),
                result -> {
                }
        );
        return waypointServer;
    }

    private record TestPlayer(UUID uuid) {
    }

    private static final class TestWaypointServer extends WaypointFilesManagerCore {
        private TestWaypointServer() {
            super(Path.of("build", "navigation-test", "waypoints"));
        }
    }

    private static final class TestPlatform implements NavigationPlatform<TestPlayer> {
        private final Map<UUID, TestPlayer> players = new HashMap<>();
        private final Map<UUID, String> persistedSessions = new HashMap<>();
        private final List<UUID> findPlayerRequests = new ArrayList<>();
        private NavigationResult nextPreflightResult = NavigationResult.success();
        private @Nullable NavigationSession lastProposedSession;
        private int snapshotCount;
        private int handlerExceptionCount;
        private boolean deferPlayerActions;
        private final List<Runnable> deferredPlayerActions = new ArrayList<>();

        private TestPlayer addPlayer() {
            TestPlayer player = new TestPlayer(UUID.randomUUID());
            this.players.put(player.uuid(), player);
            return player;
        }

        private void removePlayer(UUID playerUuid) {
            this.players.remove(playerUuid);
        }

        @Override
        public UUID playerUuid(TestPlayer player) {
            return player.uuid();
        }

        @Override
        public void executePlayer(UUID playerUuid, Consumer<TestPlayer> action) {
            this.findPlayerRequests.add(playerUuid);
            TestPlayer player = this.players.get(playerUuid);
            if (player != null) {
                Runnable task = () -> action.accept(player);
                if (this.deferPlayerActions) {
                    this.deferredPlayerActions.add(task);
                } else {
                    task.run();
                }
            }
        }

        private void runDeferredPlayerActions() {
            List<Runnable> tasks = List.copyOf(this.deferredPlayerActions);
            this.deferredPlayerActions.clear();
            this.deferPlayerActions = false;
            tasks.forEach(Runnable::run);
        }

        @Override
        public NavigationSnapshot snapshot(TestPlayer player, NavigationTarget target) {
            this.snapshotCount++;
            return NavigationMath.snapshot(
                    "minecraft:overworld",
                    0.0D,
                    64.0D,
                    0.0D,
                    0.0D,
                    target
            );
        }

        @Override
        public NavigationResult preflight(
                TestPlayer player,
                @Nullable NavigationSession currentSession,
                NavigationSession proposedSession
        ) {
            this.lastProposedSession = proposedSession;
            NavigationResult result = this.nextPreflightResult;
            this.nextPreflightResult = NavigationResult.success();
            return result;
        }

        @Override
        public void onHandlerException(
                UUID playerUuid,
                NavigationMethod method,
                RuntimeException exception
        ) {
            this.handlerExceptionCount++;
        }

        @Override
        public Optional<String> loadPersistedSession(TestPlayer player) {
            return Optional.ofNullable(this.persistedSessions.get(player.uuid()));
        }

        @Override
        public void savePersistedSession(TestPlayer player, String encodedSession) {
            this.persistedSessions.put(player.uuid(), encodedSession);
        }

        @Override
        public void clearPersistedSession(TestPlayer player) {
            this.persistedSessions.remove(player.uuid());
        }
    }

    private static final class TestHandler
            implements TextDisplayTransformationHandler<TestPlayer> {
        private final NavigationMethod method;
        private NavigationResult enableResult = NavigationResult.success();
        private int enableCount;
        private int updateCount;
        private int disableCount;
        private int cleanupCount;
        private boolean failNextUpdate;
        private boolean failNextDisable;
        private boolean failNextTransformation;
        private int transformationCount;
        private @Nullable NavigationSnapshot lastSnapshot;
        private @Nullable UUID lastCleanedPlayerUuid;
        private @Nullable Vector3f lastTranslation;
        private @Nullable Quaternionf lastRotation;
        private @Nullable Vector3f lastScale;
        private final List<NavigationSession> seenSessions = new ArrayList<>();

        private TestHandler(NavigationMethod method) {
            this.method = method;
        }

        @Override
        public NavigationMethod method() {
            return this.method;
        }

        @Override
        public NavigationResult enable(
                TestPlayer player,
                NavigationSession session,
                NavigationSnapshot snapshot
        ) {
            this.enableCount++;
            this.lastSnapshot = snapshot;
            this.seenSessions.add(session);
            return this.enableResult;
        }

        @Override
        public void update(
                TestPlayer player,
                NavigationSession session,
                NavigationSnapshot snapshot
        ) {
            this.updateCount++;
            this.lastSnapshot = snapshot;
            this.seenSessions.add(session);
            if (this.failNextUpdate) {
                this.failNextUpdate = false;
                throw new IllegalStateException("update failed");
            }
        }

        @Override
        public void disable(TestPlayer player, NavigationSession session) {
            this.disableCount++;
            this.seenSessions.add(session);
            if (this.failNextDisable) {
                this.failNextDisable = false;
                throw new IllegalStateException("disable failed");
            }
        }

        @Override
        public void cleanupPlayer(UUID playerUuid, NavigationSession session) {
            this.cleanupCount++;
            this.lastCleanedPlayerUuid = playerUuid;
        }

        @Override
        public void applyTransformation(
                TestPlayer player,
                Vector3f translation,
                Quaternionf rotation,
                Vector3f scale
        ) {
            this.transformationCount++;
            this.lastTranslation = new Vector3f(translation);
            this.lastRotation = new Quaternionf(rotation);
            this.lastScale = new Vector3f(scale);
            if (this.failNextTransformation) {
                this.failNextTransformation = false;
                throw new IllegalStateException("transformation failed");
            }
        }

        private List<String> seenWaypointNames() {
            return this.seenSessions.stream()
                    .map(seenSession -> seenSession.target().waypointName())
                    .toList();
        }
    }
}
