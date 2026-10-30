package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeleportCommandStyleTest {
    @Test
    void placeholdersTakeTheirColorsAndOtherTextKeepsTheFieldColor() {
        String value = "/tp {x} {y} {z} {yaw} {dimension}";
        List<Integer> colors = colors(TeleportCommandStyle.format(value, value, 0));
        assertEquals(-1, colors.get(0));
        assertEquals(0xFF7B72, colors.get(value.indexOf("{x}")));
        assertEquals(-1, colors.get(value.indexOf("{x}") + 3));
        assertEquals(0x7EE787, colors.get(value.indexOf("{y}") + 1));
        assertEquals(0x79B8FF, colors.get(value.indexOf("{z}") + 2));
        assertEquals(0xFFC66D, colors.get(value.indexOf("{yaw}") + 4));
        assertEquals(0xD2A8FF, colors.get(value.indexOf("{dimension}")));
    }

    @Test
    void aPlaceholderCutByTheCaretKeepsItsColorOnBothSides() {
        String value = "tp {yaw}";
        assertEquals(List.of(-1, -1, -1, 0xFFC66D, 0xFFC66D), colors(TeleportCommandStyle.format(value, "tp {y", 0)));
        assertEquals(List.of(0xFFC66D, 0xFFC66D, 0xFFC66D), colors(TeleportCommandStyle.format(value, "aw}", 5)));
    }

    @Test
    void aScrolledFieldColorsThePlaceholderItStartsInside() {
        String value = "/execute in {dimension} run";
        int offset = value.indexOf("mension");
        List<Integer> colors = colors(TeleportCommandStyle.format(value, value.substring(offset), offset));
        assertEquals(0xD2A8FF, colors.get(0));
        assertEquals(0xD2A8FF, colors.get("mension}".length() - 1));
        assertEquals(-1, colors.get("mension}".length()));
    }

    @Test
    void mistakesUseTheDangerColorStruckThrough() {
        String value = "tp {pitch} }";
        List<Style> styles = styles(TeleportCommandStyle.format(value, value, 0));
        int danger = WidgetThemeManager.getColor(WidgetThemeVariable.DANGER) & 0xFFFFFF;
        for (int index : new int[]{3, 9, 11}) {
            assertEquals(danger, styles.get(index).getColor().getValue());
            assertTrue(styles.get(index).isStrikethrough());
        }
        assertFalse(styles.get(0).isStrikethrough());
    }

    @Test
    void theLegendListsEveryPlaceholderInItsColor() {
        var contents = (TranslatableContents) TeleportCommandStyle.legend().getContents();
        assertEquals("waypoint.profile.placeholders", contents.getKey());
        Component list = (Component) contents.getArgs()[0];
        assertEquals("{dimension} {x} {y} {z} {yaw}", list.getString());
        List<Integer> colors = new ArrayList<>();
        list.visit((style, text) -> {
            if (text.startsWith("{")) colors.add(style.getColor().getValue());
            return Optional.empty();
        }, Style.EMPTY);
        assertEquals(List.of(0xD2A8FF, 0xFF7B72, 0x7EE787, 0x79B8FF, 0xFFC66D), colors);
    }

    /** Each character's style color, or -1 for none. */
    private static List<Integer> colors(FormattedCharSequence text) {
        return styles(text).stream().map(style -> style.getColor() == null ? -1 : style.getColor().getValue()).toList();
    }

    private static List<Style> styles(FormattedCharSequence text) {
        List<Style> styles = new ArrayList<>();
        text.accept((index, style, codePoint) -> styles.add(style));
        return styles;
    }
}
