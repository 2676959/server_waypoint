package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.ClientWaypointSelection;
import _959.server_waypoint.common.client.ClientWaypointSource;
import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text the details panel shows, read from what it hands its font to break into lines, with the
 * mod's English strings loaded over the game's.
 */
class WaypointDetailsWidgetTextTest {
    @BeforeAll
    static void bootstrapRegistries() {
        // A waypoint's formatted display name decodes through Minecraft's text codecs.
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        } catch (Throwable failure) {
            // Forge-family registries need a loader that plain JUnit does not supply.
            Assumptions.abort("Minecraft could not be bootstrapped in this test runtime: " + failure);
        }
    }

    @Test
    void theCollectionRowNamesTheSelectedWaypointsCollection() throws Exception {
        Language original = Language.getInstance();
        Language.inject(new ModLanguage(original, read("en_us")));
        try {
            RecordingFont font = new RecordingFont();
            WaypointDetailsWidget panel = new WaypointDetailsWidget(0, 0, 200, 100, font);
            var identity = new ClientWaypointSelection(ClientWaypointSource.PRIVATE, 1, null,
                    "minecraft:overworld", "Bases", "Home base");
            var waypoint = new SimpleWaypoint("Home base", "HB", new WaypointPos(1, 64, 2), 0x3366CC, 0, false);

            panel.setSelection(new WaypointListWidget.WaypointSelection(
                    identity, "minecraft:overworld", "Bases", "Bases", waypoint));

            assertTrue(font.shown.contains("Collection: Private"), String.valueOf(font.shown));
        } finally {
            Language.inject(original);
        }
    }

    private JsonObject read(String locale) throws Exception {
        var stream = getClass().getResourceAsStream("/assets/server_waypoint/lang/" + locale + ".json");
        assertNotNull(stream, locale);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** The game's language with one locale of the mod's strings over it, as the game loads them. */
    private static final class ModLanguage extends Language {
        private final Language game;
        private final JsonObject strings;

        private ModLanguage(Language game, JsonObject strings) {
            this.game = game;
            this.strings = strings;
        }

        @Override
        public String getOrDefault(String key, String defaultValue) {
            return this.strings.has(key) ? this.strings.get(key).getAsString() : this.game.getOrDefault(key, defaultValue);
        }

        @Override
        public boolean has(String key) {
            return this.strings.has(key) || this.game.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return this.game.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return this.game.getVisualOrder(text);
        }
    }

    /**
     * A font whose characters are {@link TestFont#CHARACTER_WIDTH}
     * pixels wide. It never wraps, and it keeps every text it is asked to break into lines.
     */
    private static final class RecordingFont extends Font {
        final List<String> shown = new ArrayList<>();

        private RecordingFont() {
            // No glyph source: every method that would read one is overridden below.
            //? if >= 1.21.9 {
            super((Font.Provider) null);
            //?} else {
            /*super(null, false);
            *///?}
        }

        @Override
        public int width(String text) {
            return text.length() * TestFont.CHARACTER_WIDTH;
        }

        @Override
        public int width(FormattedText text) {
            return this.width(text.getString());
        }

        @Override
        public int width(FormattedCharSequence text) {
            int[] characters = {0};
            text.accept((index, style, codePoint) -> {
                characters[0]++;
                return true;
            });
            return characters[0] * TestFont.CHARACTER_WIDTH;
        }

        @Override
        public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
            this.shown.add(text.getString());
            return List.of(FormattedCharSequence.forward(text.getString(), Style.EMPTY));
        }
    }
}
