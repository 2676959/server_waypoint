package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Field;
import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Input;
import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Kind;
import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Lookup;
import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Message;
import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Problem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointFormCheckTest {
    private static final String OVERWORLD = "minecraft:overworld";
    // The Base list, shown as "Base list", holds Home and Farm.
    private static final Lookup DATA = new Lookup() {
        @Override
        public boolean listExists(String dimension, String list) {
            return dimension.equals(OVERWORLD) && list.equals("Base");
        }

        @Override
        public boolean hasWaypoint(String dimension, String list, String name) {
            return listExists(dimension, list) && Set.of("Home", "Farm").contains(name);
        }

        @Override
        public String listDisplayName(String dimension, String list) {
            return Map.of("Base", "Base list").getOrDefault(list, list);
        }
    };

    @Test
    void aValidAddHasNoProblem() {
        assertNull(WaypointFormCheck.firstProblem(add("Base", "Camp"), DATA));
    }

    @Test
    void anEmptyDimensionAsksForOneBeforeAnythingElse() {
        Problem problem = WaypointFormCheck.firstProblem(
                new Input(true, "", "", "", "", "", "", null), DATA);

        assertProblem(problem, Message.CHOOSE_DIMENSION, Kind.HINT, Field.NONE, List.of());
        assertTrue(problem.blocks());
    }

    @Test
    void anEmptyListAsksForOneBeforeTheName() {
        Problem problem = WaypointFormCheck.firstProblem(add("", ""), DATA);

        assertProblem(problem, Message.ENTER_LIST, Kind.HINT, Field.NONE, List.of());
    }

    @Test
    void anEmptyNameAsksForOneOnBothScreens() {
        assertProblem(WaypointFormCheck.firstProblem(add("Base", ""), DATA),
                Message.ENTER_NAME, Kind.HINT, Field.NONE, List.of());
        assertProblem(WaypointFormCheck.firstProblem(edit("", "", "", "Home"), DATA),
                Message.ENTER_NAME, Kind.HINT, Field.NONE, List.of());
    }

    @Test
    void aNameTheListAlreadyHasIsAnErrorOnTheNameFieldNamingTheListByItsDisplayName() {
        Problem problem = WaypointFormCheck.firstProblem(add("Base", "Home"), DATA);

        assertProblem(problem, Message.NAME_TAKEN, Kind.ERROR, Field.NAME, List.of("Base list", "Home"));
        assertTrue(problem.blocks());
    }

    @Test
    void namesAreComparedExactlyAndCaseSensitively() {
        assertNull(WaypointFormCheck.firstProblem(add("Base", "home"), DATA));
        assertNull(WaypointFormCheck.firstProblem(add("Base", "Home "), DATA));
    }

    @Test
    void editAcceptsTheWaypointsOwnSavedNameButNotAnotherWaypointsName() {
        assertNull(WaypointFormCheck.firstProblem(edit("Home", "", "", "Home"), DATA));
        assertProblem(WaypointFormCheck.firstProblem(edit("Farm", "", "", "Home"), DATA),
                Message.NAME_TAKEN, Kind.ERROR, Field.NAME, List.of("Base list", "Farm"));
    }

    @Test
    void aDisplayNameThatIsNotValidFormattedTextIsAnErrorOnEditOnly() {
        Problem problem = WaypointFormCheck.firstProblem(edit("Home", "{not valid json}", "", "Home"), DATA);

        assertProblem(problem, Message.INVALID_DISPLAY_NAME, Kind.ERROR, Field.DISPLAY_NAME, List.of());
        assertNull(WaypointFormCheck.firstProblem(edit("Home", "{\"text\":\"Home\"}", "", "Home"), DATA));
        assertNull(WaypointFormCheck.firstProblem(new Input(true, OVERWORLD, "Base", "Camp", "{not valid json}", "", "", null), DATA));
    }

    @Test
    void aKeywordThatAppearsTwiceIgnoringCaseIsAnErrorNamingIt() {
        Problem problem = WaypointFormCheck.firstProblem(addWith("home, base, HOME"), DATA);

        assertProblem(problem, Message.DUPLICATE_KEYWORD, Kind.ERROR, Field.KEYWORDS, List.of("HOME"));
    }

    @Test
    void emptyEntriesAreNotDuplicates() {
        assertNull(WaypointFormCheck.firstProblem(addWith("home,, ,base,"), DATA));
    }

    @Test
    void moreThan32KeywordsIsAnError() {
        assertNull(WaypointFormCheck.firstProblem(addWith(keywords(32)), DATA));
        assertProblem(WaypointFormCheck.firstProblem(addWith(keywords(33)), DATA),
                Message.TOO_MANY_KEYWORDS, Kind.ERROR, Field.KEYWORDS, List.of("32"));
    }

    @Test
    void aKeywordLongerThan64CharactersIsAnError() {
        assertNull(WaypointFormCheck.firstProblem(addWith("a".repeat(64)), DATA));
        assertProblem(WaypointFormCheck.firstProblem(addWith("a".repeat(65)), DATA),
                Message.KEYWORD_TOO_LONG, Kind.ERROR, Field.KEYWORDS, List.of("64"));
    }

    @Test
    void theKeywordsFieldTakes32KeywordsOf64CharactersWithTheirSeparators() {
        assertEquals(2110, WaypointFormCheck.MAX_KEYWORDS_TEXT_LENGTH);
        String longest = String.join(", ", java.util.Collections.nCopies(32, "a".repeat(64)));
        assertEquals(WaypointFormCheck.MAX_KEYWORDS_TEXT_LENGTH, longest.length());
    }

    @Test
    void aDescriptionThatIsNotValidFormattedTextIsAnErrorOnTheDescriptionField() {
        Problem problem = WaypointFormCheck.firstProblem(
                new Input(true, OVERWORLD, "Base", "Camp", "", "", "[broken", null), DATA);

        assertProblem(problem, Message.INVALID_DESCRIPTION, Kind.ERROR, Field.DESCRIPTION, List.of());
        assertNull(WaypointFormCheck.firstProblem(
                new Input(true, OVERWORLD, "Base", "Camp", "", "", "Beds upstairs", null), DATA));
    }

    @Test
    void aListThatDoesNotExistYetIsANoteThatDoesNotBlock() {
        Problem problem = WaypointFormCheck.firstProblem(add("Outposts", "Camp"), DATA);

        assertProblem(problem, Message.NEW_LIST, Kind.NOTE, Field.NONE, List.of("Outposts"));
        assertFalse(problem.blocks());
    }

    @Test
    void theNoteShowsOnlyWhenNothingElseDoes() {
        Problem problem = WaypointFormCheck.firstProblem(add("Outposts", ""), DATA);

        assertProblem(problem, Message.ENTER_NAME, Kind.HINT, Field.NONE, List.of());
        Problem keywords = WaypointFormCheck.firstProblem(new Input(true, OVERWORLD, "Outposts", "Camp", "", "a, A", "", null), DATA);
        assertEquals(Message.DUPLICATE_KEYWORD, keywords.message());
    }

    @Test
    void editNeverNotesANewList() {
        assertNull(WaypointFormCheck.firstProblem(edit("Camp", "", "", "Camp"), DATA));
    }

    @Test
    void theFirstProblemInTheListedOrderWins() {
        // Everything wrong at once: the dimension is named first.
        Input everything = new Input(true, "", "", "", "{bad", "a, a", "[bad", null);
        assertEquals(Message.CHOOSE_DIMENSION, WaypointFormCheck.firstProblem(everything, DATA).message());
        assertEquals(Message.ENTER_LIST, WaypointFormCheck.firstProblem(
                new Input(true, OVERWORLD, "", "", "", "", "", null), DATA).message());
        // A taken name comes before a bad display name, keywords and description.
        assertEquals(Message.NAME_TAKEN, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Farm", "{bad", "a, a", "[bad", "Home"), DATA).message());
        assertEquals(Message.INVALID_DISPLAY_NAME, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Home", "{bad", "a, a", "[bad", "Home"), DATA).message());
        assertEquals(Message.DUPLICATE_KEYWORD, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Home", "", "a, a, " + keywords(40), "[bad", "Home"), DATA).message());
        assertEquals(Message.TOO_MANY_KEYWORDS, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Home", "", keywords(40) + ", " + "b".repeat(70), "[bad", "Home"), DATA).message());
        assertEquals(Message.KEYWORD_TOO_LONG, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Home", "", "b".repeat(70), "[bad", "Home"), DATA).message());
        assertEquals(Message.INVALID_DESCRIPTION, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Home", "", "", "[bad", "Home"), DATA).message());
    }

    @Test
    void aChangeInTheSyncedDataChangesTheAnswer() {
        Set<String> names = new java.util.HashSet<>();
        Lookup changing = new Lookup() {
            @Override
            public boolean listExists(String dimension, String list) {
                return true;
            }

            @Override
            public boolean hasWaypoint(String dimension, String list, String name) {
                return names.contains(name);
            }

            @Override
            public String listDisplayName(String dimension, String list) {
                return list;
            }
        };
        Input camp = add("Base", "Camp");

        assertNull(WaypointFormCheck.firstProblem(camp, changing));
        names.add("Camp");
        assertEquals(Message.NAME_TAKEN, WaypointFormCheck.firstProblem(camp, changing).message());
        names.clear();
        assertNull(WaypointFormCheck.firstProblem(camp, changing));
    }

    @Test
    void everyMessageHasATranslationKey() {
        for (Message message : Message.values()) {
            assertNotNull(message.translationKey());
            assertTrue(message.translationKey().startsWith("waypoint."), message.name());
        }
        assertEquals("waypoint.edit.error.invalid_display_text", Message.INVALID_DISPLAY_NAME.translationKey());
    }

    private static Input add(String list, String name) {
        return new Input(true, OVERWORLD, list, name, "", "", "", null);
    }

    private static Input addWith(String keywords) {
        return new Input(true, OVERWORLD, "Base", "Camp", "", keywords, "", null);
    }

    private static Input edit(String name, String displayName, String keywords, String savedName) {
        return new Input(false, OVERWORLD, "Base", name, displayName, keywords, "", savedName);
    }

    private static String keywords(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append(i == 0 ? "" : ", ").append("k").append(i);
        }
        return text.toString();
    }

    private static void assertProblem(Problem problem, Message message, Kind kind, Field field, List<String> arguments) {
        assertNotNull(problem);
        assertEquals(message, problem.message());
        assertEquals(kind, problem.kind());
        assertEquals(field, problem.field());
        assertEquals(arguments, problem.arguments());
    }
}
