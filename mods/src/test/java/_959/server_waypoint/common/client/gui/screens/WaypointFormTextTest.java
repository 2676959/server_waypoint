package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The header text is one line as wide as the panel: text that doesn't fit is cut and ends with an
 * ellipsis. {@link TestFont} makes every character 6 pixels wide.
 */
class WaypointFormTextTest {
    private static final TestFont FONT = new TestFont();

    @Test
    void textThatFitsIsLeftAlone() {
        Component text = Component.literal("Home");

        assertSame(text, AbstractWaypointPropertiesScreen.cutToWidth(FONT, text, 24));
    }

    @Test
    void textThatDoesNotFitIsCutAndEndsWithAnEllipsis() {
        Component cut = AbstractWaypointPropertiesScreen.cutToWidth(FONT, Component.literal("Home Base"), 6 * 6);

        // Six characters fit: five of the text and the ellipsis.
        assertEquals("Home …", cut.getString());
        assertEquals(6 * 6, FONT.width(cut));
    }

    @Test
    void aWidthTooSmallForAnyTextLeavesOnlyTheEllipsis() {
        Component cut = AbstractWaypointPropertiesScreen.cutToWidth(FONT, Component.literal("Home Base"), 3);

        assertEquals("…", cut.getString());
    }
}
