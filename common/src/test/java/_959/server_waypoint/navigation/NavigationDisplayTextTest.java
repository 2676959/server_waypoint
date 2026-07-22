package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavigationDisplayTextTest {
    private final NavigationSession session = new NavigationSession(
            UUID.randomUUID(),
            new NavigationTarget("minecraft:overworld", "villages", "Village", new WaypointPos(10, 64, 20), 0x55FF55),
            Set.of(NavigationMethod.ACTIONBAR)
    );

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
                NavigationMath.facingProgress(signedTurn)
        );
    }
}
