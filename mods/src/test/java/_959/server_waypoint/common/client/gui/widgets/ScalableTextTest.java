package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScalableTextTest {
    @Test
    void textWidthWithoutWrappingIsTheWholeText() {
        assertEquals(24, new ScalableText(0, 0, Component.literal("abcd"), 0xFFFFFFFF, new TestFont()).getTextWidth());
    }

    @Test
    void textWidthIsScaled() {
        assertEquals(12, new ScalableText(0, 0, Component.literal("abcd"), 0.5F, 0xFFFFFFFF, new TestFont()).getTextWidth());
    }

    @Test
    void textWidthWhileWrappingIsTheWidestLineNotTheMaximumWidth() {
        ScalableText text = new ScalableText(0, 0, Component.literal("abcd"), 1.0F, 0xFFFFFFFF, 100, new TestFont());
        assertEquals(100, text.getWidth());
        assertEquals(24, text.getTextWidth());
    }
}
