package _959.server_waypoint.mixin.xaeros_minimap;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class XaerosMinimapDropDownWidgetMixinTest {
    @Test
    void stringOptionsCaptureOwnershipBeforeRemovingMarkerAndResetForPersonalOptions() throws Exception {
        XaerosMinimapDropDownWidgetMixin mixin = new XaerosMinimapDropDownWidgetMixin();
        Method display = handler("sw$displaySyncedWaypointSetString", String.class);
        Method background = handler("sw$useSyncedWaypointSetBackground", int.class);

        assertEquals("Bases", display.invoke(mixin, "sw\u241FBases"));
        assertEquals(0x7F0D47A1, background.invoke(mixin, -939524096));
        assertEquals(0xDD26C6DA, background.invoke(mixin, -10496));
        assertEquals("Personal", display.invoke(mixin, "Personal"));
        assertEquals(-939524096, background.invoke(mixin, -939524096));
        assertEquals(-10496, background.invoke(mixin, -10496));
    }

    @Test
    void componentOptionsPreserveStyleAndResetOwnershipForPersonalOptions() throws Exception {
        XaerosMinimapDropDownWidgetMixin mixin = new XaerosMinimapDropDownWidgetMixin();
        Method display = handler("sw$displaySyncedWaypointSetComponent", Component.class);
        Method background = handler("sw$useSyncedWaypointSetBackground", int.class);
        Component managed = Component.literal("sw\u241FBases").withStyle(style -> style.withBold(true));

        Component result = (Component) display.invoke(mixin, managed);
        assertEquals("Bases", result.getString());
        assertEquals(managed.getStyle(), result.getStyle());
        assertEquals(0xCC00BCD4, background.invoke(mixin, -922757376));
        Component personal = Component.literal("Personal");
        assertSame(personal, display.invoke(mixin, personal));
        assertEquals(-922757376, background.invoke(mixin, -922757376));
    }

    private static Method handler(String name, Class<?> argument) throws NoSuchMethodException {
        Method method = XaerosMinimapDropDownWidgetMixin.class.getDeclaredMethod(name, argument);
        method.setAccessible(true);
        return method;
    }
}
