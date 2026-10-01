package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionStyleTest {
    private static final Map<String, String> LOADED = Map.of(
            "minecraft:overworld", "minecraft:overworld",
            "minecraft:the_nether", "minecraft:the_nether",
            "minecraft:the_end", "minecraft:the_end",
            "world_caves:caves", "minecraft:the_nether",
            "twilightforest:twilight_forest", "twilightforest:twilight_forest_type");

    private static DimensionStyle style(String here) {
        return DimensionStyle.local(new Viewer(Viewer.everything(), true, false, here,
                new WaypointPos(0, 64, 0), 0F), LOADED);
    }

    @Test
    void vanillaDimensionsAreTranslatedAndOthersUseTheirPathInTitleCase() {
        DimensionStyle style = style("minecraft:overworld");

        assertEquals("Overworld", render(style.name("minecraft:overworld")));
        assertEquals("Nether", render(style.name("minecraft:the_nether")));
        assertEquals("End", render(style.name("minecraft:the_end")));
        assertEquals("Twilight Forest", render(style.name("twilightforest:twilight_forest")));
        assertEquals("The Aether", DimensionStyle.titleCase("aether:the_aether"));
        assertEquals("A B C", DimensionStyle.titleCase("mod:a/b-c"));
        assertEquals("Plain", DimensionStyle.titleCase("plain"));
    }

    @Test
    void coloursFollowTheTypeAndFallBackToTheId() {
        DimensionStyle local = style("minecraft:overworld");
        DimensionStyle remote = DimensionStyle.remote(local.viewer());

        assertEquals(NamedTextColor.RED, local.color("world_caves:caves"));
        assertEquals(NamedTextColor.YELLOW, local.color("twilightforest:twilight_forest"));
        assertEquals(NamedTextColor.LIGHT_PURPLE, local.color("minecraft:the_end"));
        assertEquals(NamedTextColor.RED, remote.color("minecraft:the_nether"));
        assertEquals(NamedTextColor.YELLOW, remote.color("world_caves:caves"));
        assertNull(remote.hereMark("minecraft:overworld"));
        assertFalse(remote.viewer().isIn("minecraft:overworld"));
        assertEquals(NamedTextColor.GREEN, DimensionStyle.colorOf("minecraft:overworld"));
        assertEquals(NamedTextColor.RED, colorOf(local.name("world_caves:caves"), "Caves"));
    }

    @Test
    void unloadedDimensionsAreGrayAndSaySoInTheirTooltip() {
        DimensionStyle style = style("minecraft:overworld");

        assertTrue(style.isUnloaded("ad_astra:mars"));
        assertFalse(DimensionStyle.remote(style.viewer()).isUnloaded("ad_astra:mars"));
        assertEquals(NamedTextColor.GRAY, style.color("ad_astra:mars"));
        assertEquals("Mars\nad_astra:mars\nNot loaded\nClick to open",
                render(style.tooltip("ad_astra:mars", null, "wp.hint.open").build()));
    }

    @Test
    void theViewersDimensionComesFirstThenVanillaThenTheRestByName() {
        List<String> ids = new ArrayList<>(List.of("zeta:zone", "minecraft:the_end", "aether:the_aether",
                "minecraft:overworld", "twilightforest:twilight_forest", "minecraft:the_nether"));

        ids.sort(style("twilightforest:twilight_forest").order());

        assertEquals(List.of("twilightforest:twilight_forest", "minecraft:overworld", "minecraft:the_nether",
                "minecraft:the_end", "aether:the_aether", "zeta:zone"), ids);
    }

    @Test
    void pairedCoordinatesFollowTheType() {
        DimensionStyle style = style("minecraft:overworld");

        assertEquals("Nether -1, 64, -2", render(style.pairedCoordinates("minecraft:overworld", new WaypointPos(-1, 64, -9))));
        assertEquals("Overworld 80, 64, -16", render(style.pairedCoordinates("world_caves:caves", new WaypointPos(10, 64, -2))));
        assertNull(style.pairedCoordinates("minecraft:the_end", new WaypointPos(0, 64, 0)));
        assertEquals(NamedTextColor.RED, colorOf(style.pairedCoordinates("minecraft:overworld", new WaypointPos(8, 1, 8)), "Nether "));
    }

    @Test
    void tooltipsNameTheDimensionItsIdTypeCountsAndWhereYouAre() {
        DimensionStyle style = style("minecraft:overworld");

        assertEquals("Overworld\nminecraft:overworld\nOverworld type\n12 waypoints in 3 lists\n● You are here\nClick to open",
                render(style.tooltip("minecraft:overworld", DimensionStyle.counts(12, 3), "wp.hint.open").build()));
        assertEquals("Twilight Forest\ntwilightforest:twilight_forest\nModded type twilightforest:twilight_forest_type\nNo lists yet",
                render(style.tooltip("twilightforest:twilight_forest", DimensionStyle.counts(0, 0), null).build()));
    }

    @Test
    void plainTextViewersReadTheIdAndGetNoHereMark() {
        Viewer console = new Viewer(Viewer.everything(), false, true, "minecraft:overworld", null, 0F);
        DimensionStyle style = DimensionStyle.local(console, LOADED);

        assertEquals("Overworld (minecraft:overworld)", render(style.name("minecraft:overworld")));
        assertNull(style.hereMark("minecraft:overworld"));
        assertEquals("●", render(style("minecraft:overworld").hereMark("minecraft:overworld")));
        assertNull(style("minecraft:overworld").hereMark("minecraft:the_end"));
        assertEquals(NamedTextColor.GOLD, colorOf(style("minecraft:overworld").hereMark("minecraft:overworld"), "●"));
    }
}
