package _959.server_waypoint.core;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.EditTarget;
import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointListPatch;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointPatchTest {
    private static final String DIMENSION = "minecraft:overworld";

    @TempDir
    private Path tempDir;

    @Test
    void addPreservesExactIdentifiersAndCreatesNoDisplayNameOverrides() {
        WaypointFilesManagerCore manager = new WaypointFilesManagerCore(this.tempDir);
        List<String> identifiers = List.of("", "   ", "quoted \"value\"", "search", "{\"text\":\"json-looking\"}");

        for (String identifier : identifiers) {
            String listIdentifier = "list:" + identifier;
            manager.addWaypointList(DIMENSION, listIdentifier, listIdentifier, ignored -> {
            });
            manager.addWaypoint(DIMENSION, listIdentifier, listIdentifier, waypoint(identifier), ignored -> {
            });

            WaypointList list = manager.getWaypointFileManager(DIMENSION)
                    .getWaypointListByName(listIdentifier);
            assertFalse(list.hasDisplayNameOverride());
            assertFalse(list.getWaypointByName(identifier).hasDisplayNameOverride());
        }
    }

    @Test
    void waypointPatchSetsAndClearsEveryOptionalFieldAtomically() {
        WaypointFilesManagerCore manager = populatedManager();
        WaypointList list = list(manager, "source");
        int revision = list.getSyncNum();
        WaypointPatch setPatch = new WaypointPatch(
                PatchField.set("renamed"),
                PatchField.set("{\"text\":\"Shown\",\"color\":\"gold\"}"),
                PatchField.set("RN"),
                PatchField.set(new WaypointPos(8, 90, -4)),
                PatchField.set(0x123456),
                PatchField.set(135),
                PatchField.set(false),
                PatchField.set(List.of("one", "two")),
                PatchField.set("{\"text\":\"Description\"}")
        );

        var setResult = manager.updateWaypoint(
                EditTarget.waypoint(DIMENSION, "source", "original"),
                revision,
                setPatch,
                ignored -> {
                }
        );

        assertEquals(EditResultStatus.SUCCESS, setResult.status());
        SimpleWaypoint updated = setResult.afterSnapshot();
        assertEquals("renamed", updated.name());
        assertTrue(updated.hasDisplayNameOverride());
        assertEquals("RN", updated.initials());
        assertEquals(new WaypointPos(8, 90, -4), updated.pos());
        assertEquals(0x123456, updated.rgb());
        assertEquals(135, updated.yaw());
        assertFalse(updated.global());
        assertEquals(List.of("one", "two"), updated.keywords());
        assertEquals("{\"text\":\"Description\"}", updated.description());

        WaypointPatch clearPatch = new WaypointPatch(
                PatchField.unchanged(), PatchField.clear(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.clear(), PatchField.clear()
        );
        var clearResult = manager.updateWaypoint(
                EditTarget.waypoint(DIMENSION, "source", "renamed"),
                setResult.syncNum(),
                clearPatch,
                ignored -> {
                }
        );

        assertEquals(EditResultStatus.SUCCESS, clearResult.status());
        assertFalse(clearResult.afterSnapshot().hasDisplayNameOverride());
        assertEquals("renamed", clearResult.afterSnapshot().displayName());
        assertEquals(List.of(), clearResult.afterSnapshot().keywords());
        assertEquals("", clearResult.afterSnapshot().description());
    }

    @Test
    void staleRevisionAndIdentifierCollisionsDoNotPartiallyMutate() {
        WaypointFilesManagerCore manager = populatedManager();
        manager.addWaypoint(DIMENSION, "source", "source", waypoint("occupied"), ignored -> {
        });
        WaypointList list = list(manager, "source");
        int revision = list.getSyncNum();

        var stale = manager.updateWaypoint(
                EditTarget.waypoint(DIMENSION, "source", "original"),
                revision - 1,
                patchIdentifier("should-not-appear"),
                ignored -> {
                }
        );
        var collision = manager.updateWaypoint(
                EditTarget.waypoint(DIMENSION, "source", "original"),
                revision,
                patchIdentifier("occupied"),
                ignored -> {
                }
        );

        assertEquals(EditResultStatus.STALE_REVISION, stale.status());
        assertEquals(EditResultStatus.IDENTIFIER_COLLISION, collision.status());
        assertEquals(revision, list.getSyncNum());
        assertEquals("Original display", list.getWaypointByName("original").displayName());
        assertNull(list.getWaypointByName("should-not-appear"));
    }

    @Test
    void duplicateKeywordPatchIsRejectedWithoutMutation() {
        WaypointFilesManagerCore manager = populatedManager();
        WaypointList list = list(manager, "source");
        int revision = list.getSyncNum();
        WaypointPatch patch = new WaypointPatch(
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.set(List.of("home", "HOME")),
                PatchField.unchanged()
        );

        var result = manager.updateWaypoint(
                EditTarget.waypoint(DIMENSION, "source", "original"),
                revision,
                patch,
                ignored -> {
                }
        );

        assertEquals(EditResultStatus.DUPLICATE_KEYWORD, result.status());
        assertEquals(revision, list.getSyncNum());
        assertEquals(List.of("old"), list.getWaypointByName("original").keywords());
    }

    @Test
    void listRenameRekeysAtomicallyAndPreservesOrClearsDisplayOverride() {
        WaypointFilesManagerCore manager = populatedManager();
        manager.addWaypointList(DIMENSION, "occupied", "occupied", ignored -> {
        });
        WaypointList original = list(manager, "source");
        int revision = original.getSyncNum();

        var collision = manager.updateWaypointList(
                EditTarget.list(DIMENSION, "source"),
                revision,
                new WaypointListPatch(PatchField.set("occupied"), PatchField.unchanged()),
                ignored -> {
                }
        );
        assertEquals(EditResultStatus.IDENTIFIER_COLLISION, collision.status());
        assertEquals(original, list(manager, "source"));

        var renamed = manager.updateWaypointList(
                EditTarget.list(DIMENSION, "source"),
                revision,
                new WaypointListPatch(PatchField.set("renamed-list"), PatchField.clear()),
                ignored -> {
                }
        );
        assertEquals(EditResultStatus.SUCCESS, renamed.status());
        assertNull(manager.getWaypointFileManager(DIMENSION).getWaypointListByName("source"));
        WaypointList rekeyed = list(manager, "renamed-list");
        assertFalse(rekeyed.hasDisplayNameOverride());
        assertEquals("renamed-list", rekeyed.displayName());
        assertEquals(revision + 1, rekeyed.getSyncNum());
    }

    @Test
    void emptyDisplayNameIsAnIntentionalOverride() {
        WaypointFilesManagerCore manager = populatedManager();

        var result = manager.updateWaypoint(
                EditTarget.waypoint(DIMENSION, "source", "original"),
                null,
                new WaypointPatch(
                        PatchField.unchanged(), PatchField.set(""), PatchField.unchanged(),
                        PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                        PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged()
                ),
                ignored -> {
                }
        );

        assertEquals(EditResultStatus.SUCCESS, result.status());
        assertTrue(result.afterSnapshot().hasDisplayNameOverride());
        assertEquals("", result.afterSnapshot().displayName());
    }

    @Test
    void failedEncodingPreflightRollsBackTheMutation() {
        WaypointFilesManagerCore manager = populatedManager();
        int revision = list(manager, "source").getSyncNum();

        assertThrows(MessageEncodingException.class, () -> manager.updateWaypoint(
                EditTarget.waypoint(DIMENSION, "source", "original"),
                revision,
                patchIdentifier("must-not-commit"),
                ignored -> {
                    throw new MessageEncodingException("simulated encoding failure");
                },
                ignored -> {
                }
        ));

        WaypointList restored = list(manager, "source");
        assertEquals(revision, restored.getSyncNum());
        assertEquals("Original display", restored.getWaypointByName("original").displayName());
        assertNull(restored.getWaypointByName("must-not-commit"));
    }

    private WaypointFilesManagerCore populatedManager() {
        WaypointFilesManagerCore manager = new WaypointFilesManagerCore(this.tempDir);
        manager.addWaypointList(DIMENSION, "source", "List display", ignored -> {
        });
        manager.addWaypoint(DIMENSION, "source", "List display", new SimpleWaypoint(
                "original", "Original display", "O", new WaypointPos(1, 64, 2),
                0x39C5BB, 0, true, List.of("old"), "Old description"
        ), ignored -> {
        });
        return manager;
    }

    private static WaypointList list(WaypointFilesManagerCore manager, String identifier) {
        return manager.getWaypointFileManager(DIMENSION).getWaypointListByName(identifier);
    }

    private static WaypointPatch patchIdentifier(String identifier) {
        return new WaypointPatch(
                PatchField.set(identifier), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged()
        );
    }

    private static SimpleWaypoint waypoint(String identifier) {
        return new SimpleWaypoint(
                identifier, identifier, "I", new WaypointPos(0, 64, 0),
                0x39C5BB, 0, true, List.of(), ""
        );
    }
}
