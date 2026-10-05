package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.DimensionStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.List;
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
                    List.of(),
                    new WaypointPos(10, 64, 20),
                    0x55FF55
            ),
            Set.of(NavigationMethod.ACTIONBAR),
            TextDisplayTransformation.defaultValue()
    );

    @Test
    void buildsNavigationItemNameAndLoreFromTargetDisplayText() {
        NavigationTarget target = this.session.target();

        TextComponent name = (TextComponent) NavigationDisplayText.buildItemTooltipName(target);
        assertEquals("villages › Village", plainText(name));
        assertEquals(NamedTextColor.WHITE, name.children().get(0).color());
        assertEquals(" › ", ((TextComponent) name.children().get(1)).content());
        assertEquals(NamedTextColor.GOLD, name.children().get(2).color());
        assertEquals(
                "A nearby village",
                ((TextComponent) NavigationDisplayText.buildItemLore(target).get(1)).content()
        );
    }

    @Test
    void itemTooltipIncludesListDimensionKeywordsAndDecodedDescriptionInOrder() {
        NavigationTarget target = itemTarget("{\"text\":\"Village\",\"color\":\"gold\"}",
                List.of("town", "trading"), "{\"text\":\"Nearby\",\"color\":\"aqua\"}");
        List<Component> lore = NavigationDisplayText.buildItemLore(target);

        assertEquals(3, lore.size());
        assertEquals("minecraft:overworld", ((TextComponent) lore.get(0)).content());
        assertEquals(DimensionStyle.colorOf("minecraft:overworld"), lore.get(0).color());
        assertEquals("town, trading", ((TextComponent) lore.get(1)).content());
        assertEquals(TextColor.color(0x447FFF), lore.get(1).color());
        assertEquals("Nearby", ((TextComponent) lore.get(2)).content());
        assertEquals(NamedTextColor.AQUA, lore.get(2).color());
        for (Component line : lore) {
            assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
        }
    }

    @Test
    void itemTooltipOmitsEmptyOptionalLinesAndUsesColoredIdentifier() {
        NavigationTarget target = itemTarget("", List.of(), "");
        TextComponent name = (TextComponent) NavigationDisplayText.buildItemTooltipName(target);
        List<Component> lore = NavigationDisplayText.buildItemLore(target);

        assertEquals("villages › Village", plainText(name));
        assertEquals(NamedTextColor.WHITE, name.children().get(0).color());
        assertEquals(TextColor.color(0x55FF55), name.children().get(2).color());
        assertEquals(1, lore.size());
        assertEquals("minecraft:overworld", ((TextComponent) lore.get(0)).content());
    }

    @Test
    void itemTooltipKeepsPlainDescriptionWhenKeywordsAreEmpty() {
        List<Component> lore = NavigationDisplayText.buildItemLore(itemTarget("Village", List.of(), "Nearby"));

        assertEquals(2, lore.size());
        assertEquals("Nearby", ((TextComponent) lore.get(1)).content());
    }

    @Test
    void itemTooltipKeepsKeywordsWhenDescriptionIsEmpty() {
        List<Component> lore = NavigationDisplayText.buildItemLore(itemTarget("Village", List.of("town"), ""));

        assertEquals(2, lore.size());
        assertEquals("town", ((TextComponent) lore.get(1)).content());
        assertEquals(TextColor.color(0x447FFF), lore.get(1).color());
    }

    @Test
    void itemTooltipSplitsActualAndLiteralNewlinesIntoSeparateLoreLines() {
        List<Component> lore = NavigationDisplayText.buildItemLore(
                itemTarget("Village", List.of(), "First\nSecond\\nThird\r\nFourth"));

        assertEquals(5, lore.size());
        assertEquals(List.of("First", "Second", "Third", "Fourth"),
                lore.subList(1, lore.size()).stream().map(NavigationDisplayTextTest::plainText).toList());
        for (Component line : lore.subList(1, lore.size())) {
            assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
        }
    }

    @Test
    void itemTooltipSplitsDecodedJsonNewlinesAndPreservesNestedStyles() {
        List<Component> lore = NavigationDisplayText.buildItemLore(itemTarget("Village", List.of(),
                "{\"text\":\"First\",\"color\":\"gold\",\"extra\":["
                        + "{\"text\":\"\\nSecond\\\\nThird\",\"color\":\"aqua\",\"bold\":true}]}"));

        assertEquals(4, lore.size());
        assertEquals(List.of("First", "Second", "Third"),
                lore.subList(1, lore.size()).stream().map(NavigationDisplayTextTest::plainText).toList());
        assertEquals(NamedTextColor.GOLD, lore.get(1).color());
        for (Component line : lore.subList(2, lore.size())) {
            assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
            assertEquals(NamedTextColor.AQUA, line.children().get(0).color());
            assertEquals(TextDecoration.State.TRUE, line.children().get(0).decoration(TextDecoration.BOLD));
        }
    }

    @Test
    void itemTooltipPreservesBlankDescriptionLines() {
        List<Component> lore = NavigationDisplayText.buildItemLore(itemTarget("Village", List.of(), "\nFirst\n\n"));

        assertEquals(List.of("", "First", "", ""),
                lore.subList(1, lore.size()).stream().map(NavigationDisplayTextTest::plainText).toList());
    }

    private static String plainText(Component component) {
        String content = component instanceof TextComponent text ? text.content() : "";
        return content + component.children().stream().map(NavigationDisplayTextTest::plainText)
                .collect(java.util.stream.Collectors.joining());
    }

    private static NavigationTarget itemTarget(String displayName, List<String> keywords, String description) {
        return new NavigationTarget("minecraft:overworld", new WaypointList("villages", "Village list", 0, List.of()),
                new SimpleWaypoint("Village", displayName, "V", new WaypointPos(10, 64, 20),
                        0x55FF55, 0, true, keywords, description));
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
