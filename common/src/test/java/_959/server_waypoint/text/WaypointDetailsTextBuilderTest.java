package _959.server_waypoint.text;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WaypointDetailsTextBuilderTest {
    @Test
    void listEditAndClearControlsLeadThePropertyLine() {
        Component details = WaypointDetailsTextBuilder.listDetails(
                "minecraft:overworld",
                new WaypointList("bases", "Home Bases", 1, List.of()),
                true,
                true,
                true
        );

        assertEquals(
                List.of("edit", "clear", "property"),
                displayNameLineOrder(details)
        );
        assertEquals(
                TextDecoration.State.FALSE,
                requireTranslation(details, "waypoint.details.display_name")
                        .decoration(TextDecoration.BOLD)
        );
        assertFalse(hasEffectiveClickEvent(details, "waypoint.details.display_name"));
    }

    @Test
    void waypointEditAndClearControlsLeadThePropertyLine() {
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "home",
                "Main Home",
                "H",
                new WaypointPos(1, 64, 2),
                0xFFAA00,
                0,
                true,
                List.of(),
                ""
        );
        Component details = WaypointDetailsTextBuilder.waypointDetails(
                "minecraft:overworld",
                new WaypointList("bases", 1, List.of(waypoint)),
                waypoint,
                true,
                true,
                true,
                true
        );

        assertEquals(
                List.of("edit", "clear", "property"),
                displayNameLineOrder(details)
        );
        assertEquals(
                TextDecoration.State.FALSE,
                requireTranslation(details, "waypoint.details.display_name")
                        .decoration(TextDecoration.BOLD)
        );
        assertFalse(hasEffectiveClickEvent(details, "waypoint.details.display_name"));
        assertEquals(TextColor.color(0xFFAA00), requireText(details, "■").color());
        assertEquals(NamedTextColor.WHITE, requireText(details, "#FFAA00").color());
    }

    private static List<String> displayNameLineOrder(Component component) {
        List<String> order = new ArrayList<>();
        collectDisplayNameLineOrder(component, order);
        return order;
    }

    private static void collectDisplayNameLineOrder(Component component, List<String> order) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null && clickEvent.action() == ClickEvent.Action.SUGGEST_COMMAND) {
            if (clickEvent.value().contains(" set display-name ")) {
                order.add("edit");
            } else if (clickEvent.value().contains(" clear display-name")) {
                order.add("clear");
            }
        }
        if (component instanceof TranslatableComponent translatable
                && translatable.key().equals("waypoint.details.display_name")) {
            order.add("property");
        }
        for (Component child : component.children()) {
            collectDisplayNameLineOrder(child, order);
        }
    }

    private static TranslatableComponent requireTranslation(Component component, String key) {
        return findTranslation(component, key)
                .orElseThrow(() -> new AssertionError("Missing translation component: " + key));
    }

    private static Optional<TranslatableComponent> findTranslation(Component component, String key) {
        if (component instanceof TranslatableComponent translatable
                && translatable.key().equals(key)) {
            return Optional.of(translatable);
        }
        for (Component child : component.children()) {
            Optional<TranslatableComponent> found = findTranslation(child, key);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static TextComponent requireText(Component component, String content) {
        return findText(component, content)
                .orElseThrow(() -> new AssertionError("Missing text component: " + content));
    }

    private static Optional<TextComponent> findText(Component component, String content) {
        if (component instanceof TextComponent textComponent
                && textComponent.content().equals(content)) {
            return Optional.of(textComponent);
        }
        for (Component child : component.children()) {
            Optional<TextComponent> found = findText(child, content);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static boolean hasEffectiveClickEvent(Component component, String key) {
        return findEffectiveClickEvent(component, key, null)
                .orElseThrow(() -> new AssertionError("Missing translation component: " + key));
    }

    private static Optional<Boolean> findEffectiveClickEvent(
            Component component,
            String key,
            ClickEvent inheritedClickEvent
    ) {
        ClickEvent effectiveClickEvent = component.clickEvent() == null
                ? inheritedClickEvent
                : component.clickEvent();
        if (component instanceof TranslatableComponent translatable
                && translatable.key().equals(key)) {
            return Optional.of(effectiveClickEvent != null);
        }
        for (Component child : component.children()) {
            Optional<Boolean> found = findEffectiveClickEvent(child, key, effectiveClickEvent);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
