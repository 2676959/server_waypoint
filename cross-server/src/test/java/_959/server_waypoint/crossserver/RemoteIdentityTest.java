package _959.server_waypoint.crossserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class RemoteIdentityTest {
    @ParameterizedTest
    @ValueSource(strings = {"a", "0", "survival", "lobby-2", "creative_eu"})
    void acceptsStableServerIds(String value) {
        assertEquals(value, new RemoteServerId(value).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Survival", " survival", "survival ", "a b", "a\nb", "a\n",
            "-a", "_a", "a.b", "a/b", "a:b", "a\\b", "世界", "ａ", "a\u0000"})
    void rejectsInvalidServerIdsWithoutNormalization(String value) {
        assertThrows(IllegalArgumentException.class, () -> new RemoteServerId(value));
    }

    @Test
    void enforcesServerIdLengthBoundaryAndNullRejection() {
        String maximum = "a".repeat(64);
        assertEquals(maximum, new RemoteServerId(maximum).value());
        assertThrows(IllegalArgumentException.class, () -> new RemoteServerId(maximum + "a"));
        assertThrows(NullPointerException.class, () -> new RemoteServerId(null));
    }

    @Test
    void identicalLocalIdentitiesOnDifferentServersRemainDistinctMapKeys() {
        RemoteWaypointKey survival = key("survival", "minecraft:overworld", "public", "spawn");
        RemoteWaypointKey creative = key("creative", "minecraft:overworld", "public", "spawn");
        var entries = new HashMap<RemoteWaypointKey, String>();
        entries.put(survival, "survival spawn");
        entries.put(creative, "creative spawn");

        assertNotEquals(survival, creative);
        assertEquals(2, entries.size());
        assertEquals("survival spawn", entries.get(key("survival", "minecraft:overworld", "public", "spawn")));
        assertEquals("creative spawn", entries.get(key("creative", "minecraft:overworld", "public", "spawn")));
    }

    @Test
    void preservesExactIdentityWithoutTrimmingCaseFoldingOrUnicodeNormalization() {
        RemoteWaypointKey original = key("survival", "custom:世界", " Home Bases ", "Café");
        assertEquals("custom:世界", original.dimensionName());
        assertEquals(" Home Bases ", original.listName());
        assertEquals("Café", original.waypointName());
        assertNotEquals(original, key("survival", "custom:世界", "Home Bases", "Café"));
        assertNotEquals(original, key("survival", "custom:世界", " Home Bases ", "café"));
        assertNotEquals(original, key("survival", "custom:世界", " Home Bases ", "Cafe\u0301"));
        assertNotEquals(original, key("survival", "custom:other", " Home Bases ", "Café"));
        assertNotEquals(key("survival", "a:b", "c", "d"), key("survival", "a", "b:c", "d"));
        assertEquals("", key("survival", "", "", "").waypointName());
    }

    @Test
    void rejectsMissingIdentityComponents() {
        RemoteServerId server = new RemoteServerId("survival");
        assertThrows(NullPointerException.class, () -> new RemoteWaypointKey(null, "d", "l", "w"));
        assertThrows(NullPointerException.class, () -> new RemoteWaypointKey(server, null, "l", "w"));
        assertThrows(NullPointerException.class, () -> new RemoteWaypointKey(server, "d", null, "w"));
        assertThrows(NullPointerException.class, () -> new RemoteWaypointKey(server, "d", "l", null));
    }

    private static RemoteWaypointKey key(String server, String dimension, String list, String waypoint) {
        return new RemoteWaypointKey(new RemoteServerId(server), dimension, list, waypoint);
    }
}
