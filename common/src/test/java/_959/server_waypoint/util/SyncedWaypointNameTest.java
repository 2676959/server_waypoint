package _959.server_waypoint.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SyncedWaypointNameTest {
    @Test
    void formatsAndParsesServerWaypointName() {
        String formatted = SyncedWaypointName.format("Bases", "Spawn");
        SyncedWaypointName.ParsedName parsed = SyncedWaypointName.parse(formatted);

        assertEquals("sw\u241FBases\u241FSpawn", formatted);
        assertEquals("Bases", parsed.listName());
        assertEquals("Spawn", parsed.waypointName());
    }

    @Test
    void rejectsAmbiguousNames() {
        assertNull(SyncedWaypointName.format("Bad\u241FList", "Spawn"));
        assertNull(SyncedWaypointName.format("Bases", "Bad\u241FName"));
        assertNull(SyncedWaypointName.parse("Spawn"));
        assertNull(SyncedWaypointName.parse("sw\u241FBases"));
        assertNull(SyncedWaypointName.parse("sw\u241FBases\u241FSpawn\u241FExtra"));
    }

    @Test
    void formatsAndParsesSingleSyncedName() {
        String formatted = SyncedWaypointName.formatSyncedName("Bases");

        assertEquals("sw\u241FBases", formatted);
        assertEquals("Bases", SyncedWaypointName.parseSyncedName(formatted));
    }

    @Test
    void rejectsAmbiguousSingleSyncedName() {
        assertNull(SyncedWaypointName.formatSyncedName("Bad\u241FName"));
        assertNull(SyncedWaypointName.parseSyncedName("Bases"));
        assertNull(SyncedWaypointName.parseSyncedName("sw\u241FBases\u241FSpawn"));
    }

    @Test
    void displaysSyncedWaypointNamesWithoutMarker() {
        assertEquals("Spawn", SyncedWaypointName.toDisplayWaypointName("sw\u241FSpawn"));
        assertEquals("Spawn", SyncedWaypointName.toDisplayVoxelMapWaypointName("sw\u241FBases\u241FSpawn"));
    }

    @Test
    void leavesUnsyncedDisplayNamesUnchanged() {
        assertEquals("Spawn", SyncedWaypointName.toDisplayWaypointName("Spawn"));
        assertEquals("Spawn", SyncedWaypointName.toDisplayVoxelMapWaypointName("Spawn"));
    }
}
