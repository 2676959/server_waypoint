package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavigationDisplayTextTest {
    private final NavigationSession session = new NavigationSession(
            UUID.randomUUID(),
            new NavigationTarget(
                    "minecraft:overworld",
                    "villages",
                    "villages",
                    "Village",
                    "{\"text\":\"Village\",\"color\":\"gold\"}",
                    "{\"text\":\"A nearby village\",\"italic\":false}",
                    new WaypointPos(10, 64, 20),
                    0x55FF55
            ),
            Set.of(NavigationMethod.ACTIONBAR),
            TextDisplayTransformation.defaultValue()
    );

    @Test
    void buildsNavigationItemNameAndLoreFromTargetDisplayText() {
        NavigationTarget target = this.session.target();

        TextComponent name = (TextComponent) NavigationDisplayText.buildItemName(target);
        assertEquals("Village", name.content());
        assertEquals(NamedTextColor.GOLD, name.color());
        assertEquals(
                "A nearby village",
                ((TextComponent) NavigationDisplayText.buildItemLore(target).get(0)).content()
        );
    }

    @Test
    void usesWrongDimensionMessageWithoutDirectionOrDistance() {
        TextComponent text = (TextComponent) NavigationDisplayText.build(
                this.session,
                NavigationSnapshot.wrongDimension()
        );

        Component travelMessage = text.children().get(2);
        assertEquals(
                "waypoint.navigation.wrong_dimension",
                ((TranslatableComponent) travelMessage).key()
        );
    }

    @Test
    void usesSymbolsForTurnDirectionAndAngle() {
        assertEquals(NavigationDisplayText.SYMBOL_LEFT + "38°", turnIndicator(-38.0D));
        assertEquals(NavigationDisplayText.SYMBOL_RIGHT + "38°", turnIndicator(38.0D));
        assertEquals(NavigationDisplayText.SYMBOL_FORWARD, turnIndicator(0.0D));
    }

    @Test
    void reusesMeterUnitForHorizontalAndVerticalDistance() {
        TextComponent display = display(12.6D, 38.0D);
        assertMeters(display.children().get(4), 143L);

        TextComponent vertical = (TextComponent) display.children().get(6);
        assertEquals(NavigationDisplayText.SYMBOL_HIGH, vertical.content());
        assertMeters(vertical.children().get(0), 13L);
    }

    @Test
    void textDisplayUsesDirectionAndDistancesOnSeparateLines() {
        TextComponent display = (TextComponent) NavigationDisplayText.buildTextDisplay(
                this.session,
                snapshot(12.6D, 38.0D)
        );

        assertEquals("Village", ((TextComponent) display.children().get(0)).content());
        assertEquals(" — ", ((TextComponent) display.children().get(1)).content());
        assertEquals(
                NavigationDisplayText.SYMBOL_RIGHT + "38°",
                ((TextComponent) display.children().get(2)).content()
        );
        assertEquals("\n", ((TextComponent) display.children().get(3)).content());
        assertMeters(display.children().get(4), 143L);
        assertEquals(" | ", ((TextComponent) display.children().get(5)).content());

        TextComponent vertical = (TextComponent) display.children().get(6);
        assertEquals(NavigationDisplayText.SYMBOL_HIGH, vertical.content());
        assertMeters(vertical.children().get(0), 13L);
    }

    @Test
    void textDisplayPutsWrongDimensionMessageOnSecondLine() {
        TextComponent display = (TextComponent) NavigationDisplayText.buildTextDisplay(
                this.session,
                NavigationSnapshot.wrongDimension()
        );

        assertEquals("Village", ((TextComponent) display.children().get(0)).content());
        assertEquals("\n", ((TextComponent) display.children().get(1)).content());
        assertEquals(
                "waypoint.navigation.wrong_dimension",
                ((TranslatableComponent) display.children().get(2)).key()
        );
    }

    @Test
    void usesSymbolsForVerticalDirection() {
        assertVerticalDifference(12.6D, NavigationDisplayText.SYMBOL_HIGH, 13L);
        assertVerticalDifference(-12.6D, NavigationDisplayText.SYMBOL_LOW, 13L);
        assertVerticalDifference(0.4D, NavigationDisplayText.SYMBOL_SAME_LEVEL, 0L);
    }

    private String turnIndicator(double signedTurn) {
        return ((TextComponent) display(0.0D, signedTurn).children().get(2)).content();
    }

    private void assertVerticalDifference(double signedDifference, String expectedSymbol, long expectedDistance) {
        TextComponent vertical = (TextComponent) display(signedDifference, 38.0D).children().get(6);
        assertEquals(expectedSymbol, vertical.content());
        assertMeters(vertical.children().get(0), expectedDistance);
    }

    private void assertMeters(Component component, long expectedDistance) {
        TranslatableComponent meters = (TranslatableComponent) component;
        assertEquals("waypoint.navigation.unit.meter", meters.key());
        TextComponent distance = (TextComponent) meters.arguments().get(0).value();
        assertEquals(Long.toString(expectedDistance), distance.content());
    }

    private TextComponent display(double verticalDifference, double signedTurn) {
        return (TextComponent) NavigationDisplayText.build(
                this.session,
                snapshot(verticalDifference, signedTurn)
        );
    }

    private NavigationSnapshot snapshot(double verticalDifference, double signedTurn) {
        return new NavigationSnapshot(
                true,
                0.0D,
                signedTurn,
                143.0D,
                verticalDifference,
                NavigationMath.headingProgress(signedTurn)
        );
    }
}
