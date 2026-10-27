package _959.server_waypoint.common.server.navigation;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModNavigationItemDataTest {
    private static final String KEY = "server_waypoint:navigation_item";

    @Test
    void absentMarkerIsNotProtected() {
        assertFalse(ModNavigationItemData.hasMarker(null));
        assertFalse(ModNavigationItemData.hasMarker(new CompoundTag()));
    }

    @Test
    void onlyTheExactStringMarkerIsProtected() {
        CompoundTag tag = new CompoundTag();
        for (String value : new String[]{"", "false", "TRUE", "1"}) {
            tag.putString(KEY, value);
            assertFalse(ModNavigationItemData.hasMarker(tag));
        }
        tag.putString(KEY, "true");
        assertTrue(ModNavigationItemData.hasMarker(tag));
    }

    @Test
    void numericAndCompoundMarkersAreNotCoercedIntoTheStringMarker() {
        CompoundTag tag = new CompoundTag();
        tag.put(KEY, ByteTag.valueOf((byte) 1));
        assertFalse(ModNavigationItemData.hasMarker(tag));
        tag.put(KEY, IntTag.valueOf(1));
        assertFalse(ModNavigationItemData.hasMarker(tag));
        tag.put(KEY, new CompoundTag());
        assertFalse(ModNavigationItemData.hasMarker(tag));
    }

    @Test
    void nestedMarkerDoesNotProtectTheOuterTag() {
        CompoundTag nested = new CompoundTag();
        nested.putString(KEY, "true");
        CompoundTag tag = new CompoundTag();
        tag.put("nested", nested);
        assertFalse(ModNavigationItemData.hasMarker(tag));
        assertEquals(StringTag.valueOf("true"), nested.get(KEY));
    }

    @Test
    void readingPreservesTheMarkerAndAllUnrelatedData() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, "true");
        tag.putIntArray("ordinary", new int[]{1, 2, 3});
        CompoundTag original = tag.copy();
        assertTrue(ModNavigationItemData.hasMarker(tag));
        assertTrue(ModNavigationItemData.hasMarker(tag));
        assertEquals(original, tag);
    }

    @Test
    void resultsFollowMarkerChangesWithoutCaching() {
        CompoundTag tag = new CompoundTag();
        assertFalse(ModNavigationItemData.hasMarker(tag));
        tag.putString(KEY, "true");
        assertTrue(ModNavigationItemData.hasMarker(tag));
        tag.remove(KEY);
        assertFalse(ModNavigationItemData.hasMarker(tag));
    }
}
