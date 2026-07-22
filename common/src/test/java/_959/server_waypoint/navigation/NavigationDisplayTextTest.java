package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
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
        TranslatableComponent text = (TranslatableComponent) NavigationDisplayText.build(
                this.session,
                NavigationSnapshot.wrongDimension()
        );

        assertEquals("waypoint.navigation.display.wrong_dimension", text.key());
        Component travelMessage = (Component) text.arguments().get(1).value();
        assertEquals(
                "waypoint.navigation.wrong_dimension",
                ((TranslatableComponent) travelMessage).key()
        );
    }

    @Test
    void choosesTurnDirectionFromSignedAngle() {
        assertEquals("waypoint.navigation.turn.left", turnKey(-38.0D));
        assertEquals("waypoint.navigation.turn.right", turnKey(38.0D));
    }

    private String turnKey(double signedTurn) {
        NavigationSnapshot snapshot = new NavigationSnapshot(
                true,
                0.0D,
                signedTurn,
                143.0D,
                0.0D,
                NavigationMath.facingProgress(signedTurn)
        );
        TranslatableComponent text = (TranslatableComponent) NavigationDisplayText.build(this.session, snapshot);
        Component turnMessage = (Component) text.arguments().get(2).value();
        return ((TranslatableComponent) turnMessage).key();
    }
}
