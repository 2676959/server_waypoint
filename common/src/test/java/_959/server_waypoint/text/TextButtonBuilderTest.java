package _959.server_waypoint.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextButtonBuilderTest {
    @Test
    void showMoreRunsWhilePropertyAndRemovalControlsSuggest() {
        Component showMore = TextButtonBuilder.showMoreButton("/wp details list d l");
        Component edit = TextButtonBuilder.propertyEditButton("/wp edit", Component.text("Edit"));
        Component remove = TextButtonBuilder.suggestCommandButton(
                Component.text("Remove"),
                net.kyori.adventure.text.format.NamedTextColor.RED,
                "/wp remove",
                Component.text("Remove")
        );

        assertEquals(ClickEvent.Action.RUN_COMMAND, showMore.style().clickEvent().action());
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, edit.style().clickEvent().action());
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, remove.style().clickEvent().action());
    }

    @Test
    void disabledPermissionControlHasHoverAndNoClickEvent() {
        Component disabled = TextButtonBuilder.disabledPermissionButton(
                "x",
                Component.text("Permission required")
        );

        assertNull(disabled.style().clickEvent());
        assertNotNull(disabled.style().hoverEvent());
    }
}
