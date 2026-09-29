package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The suggestion list of a text field placed at (10, 20) and 60 wide. With {@link TestFont} the
 * field's outline covers columns 8-67 and rows 18-28, its text starts at column 10, and the list's
 * rows are 12 pixels high from row 29, at most five of them.
 */
class SuggestingTextInputTest {
    /** A column inside the list, which is at least 60 pixels wide. */
    private static final int LIST_COLUMN = 20;

    @Test
    void listFillsTheWidthOfTheFieldOutlineDirectlyBelowIt() {
        TranslucentTextField field = fieldSuggesting("alpha", "beta");

        assertFalse(field.isMouseOverSuggestion(7, rowY(0)), "left of the outline");
        assertTrue(field.isMouseOverSuggestion(8, rowY(0)), "outline's first column");
        assertTrue(field.isMouseOverSuggestion(67, rowY(0)), "outline's last column");
        assertFalse(field.isMouseOverSuggestion(68, rowY(0)), "right of the outline");
        assertFalse(field.isMouseOverSuggestion(LIST_COLUMN, 28), "the outline's last row belongs to the field");
        assertTrue(field.isMouseOverSuggestion(LIST_COLUMN, 29), "the row after the outline");
    }

    @Test
    void aSuggestionWiderThanTheFieldWidensTheListToTheRightOnly() {
        // 20 characters are 120 pixels: the text fills columns 10-129 and the border follows in 130.
        TranslucentTextField field = fieldSuggesting("a".repeat(20));

        assertFalse(field.isMouseOverSuggestion(7, rowY(0)), "left of the outline");
        assertTrue(field.isMouseOverSuggestion(8, rowY(0)), "outline's first column");
        assertTrue(field.isMouseOverSuggestion(130, rowY(0)), "the border after the text");
        assertFalse(field.isMouseOverSuggestion(131, rowY(0)), "right of the border");
    }

    @Test
    void wheelDownScrollsTheListByOneRow() {
        TranslucentTextField field = eightSuggestions();

        assertTrue(field.scrollPopupIfOver(LIST_COLUMN, rowY(0), -1.0));

        // Clicking the first row also runs the redraw path, which must not scroll the list back.
        assertEquals("s2", suggestionAtRow(field, 0));
    }

    @Test
    void wheelUpScrollsTheListBack() {
        TranslucentTextField field = eightSuggestions();
        field.scrollPopupIfOver(LIST_COLUMN, rowY(0), -1.0);
        field.scrollPopupIfOver(LIST_COLUMN, rowY(0), -1.0);

        assertTrue(field.scrollPopupIfOver(LIST_COLUMN, rowY(0), 1.0));

        assertEquals("s2", suggestionAtRow(field, 0));
    }

    @Test
    void wheelDownStopsWhenTheLastSuggestionIsShown() {
        TranslucentTextField field = eightSuggestions();

        assertTrue(field.scrollPopupIfOver(LIST_COLUMN, rowY(0), -10.0));

        assertEquals("s4", suggestionAtRow(field, 0), "the last five suggestions are s4 to s8");
    }

    @Test
    void wheelUpStopsAtTheFirstSuggestion() {
        TranslucentTextField field = eightSuggestions();

        assertTrue(field.scrollPopupIfOver(LIST_COLUMN, rowY(0), 10.0));

        assertEquals("s1", suggestionAtRow(field, 0));
    }

    @Test
    void aFractionOfANotchStillScrollsOneRow() {
        // Smooth-scrolling devices report fractions of a notch.
        TranslucentTextField field = eightSuggestions();

        assertTrue(field.scrollPopupIfOver(LIST_COLUMN, rowY(0), -0.25));

        assertEquals("s2", suggestionAtRow(field, 0));
    }

    @Test
    void severalNotchesScrollThatManyRows() {
        TranslucentTextField field = eightSuggestions();

        assertTrue(field.scrollPopupIfOver(LIST_COLUMN, rowY(0), -2.0));

        assertEquals("s3", suggestionAtRow(field, 0));
    }

    @Test
    void wheelOutsideTheListIsLeftToOtherWidgets() {
        TranslucentTextField field = eightSuggestions();

        assertFalse(field.scrollPopupIfOver(7, rowY(0), -1.0), "left of the list");
        assertFalse(field.scrollPopupIfOver(LIST_COLUMN, 28, -1.0), "over the field");
        assertFalse(field.scrollPopupIfOver(LIST_COLUMN, 89, -1.0), "just below the last row");

        assertEquals("s1", suggestionAtRow(field, 0));
    }

    @Test
    void horizontalWheelIsLeftToOtherWidgets() {
        TranslucentTextField field = eightSuggestions();

        assertFalse(field.scrollPopupIfOver(LIST_COLUMN, rowY(0), 0.0));
    }

    @Test
    void wheelOverAListThatFitsIsUsedWithoutScrolling() {
        // Otherwise the wheel would scroll whatever is behind the list.
        TranslucentTextField field = fieldSuggesting("s1", "s2", "s3");

        assertTrue(field.scrollPopupIfOver(LIST_COLUMN, rowY(0), -1.0));

        assertEquals("s1", suggestionAtRow(field, 0));
    }

    @Test
    void scrollingSelectsTheRowUnderThePointer() {
        TranslucentTextField field = eightSuggestions();

        field.scrollPopupIfOver(LIST_COLUMN, rowY(2), -1.0);

        // The list now shows s2 to s6, so the pointer is over s4, which Tab takes.
        assertTrue(field.keyPressed(InputConstants.KEY_TAB, 0, 0));
        assertEquals("s4", field.getValue());
    }

    @Test
    void hoveringARowSelectsIt() {
        TranslucentTextField field = eightSuggestions();

        assertTrue(field.layoutSuggestions(LIST_COLUMN, rowY(3)));

        assertTrue(field.keyPressed(InputConstants.KEY_TAB, 0, 0));
        assertEquals("s4", field.getValue());
    }

    @Test
    void hoveringJustBelowAScrolledListSelectsNothing() {
        TranslucentTextField field = eightSuggestions();
        // Five Down presses select s6, which scrolls the list to s2-s6.
        for (int press = 0; press < 5; press++) {
            assertTrue(field.keyPressed(InputConstants.KEY_DOWN, 0, 0));
        }

        // Row 89 is just below the list, where the hidden s7 would be next; selecting it scrolls the list again.
        assertTrue(field.layoutSuggestions(LIST_COLUMN, 89));

        assertEquals("s2", suggestionAtRow(field, 0));
    }

    /**
     * A field showing these suggestions. From 26.1 a real text field asks the game client to start
     * text input when it takes focus, so the field only reports being focused.
     */
    private static TranslucentTextField fieldSuggesting(String... suggestions) {
        TranslucentTextField field = new TranslucentTextField(10, 20, 60, Component.empty(), new TestFont()) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
        field.setSuggestionsProvider(() -> List.of(suggestions));
        return field;
    }

    private static TranslucentTextField eightSuggestions() {
        return fieldSuggesting("s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8");
    }

    /** A pixel row inside the given list row, counted from 0. */
    private static int rowY(int row) {
        return 30 + 12 * row;
    }

    /** Clicks a list row and returns the suggestion it took, which shows what the list displays there. */
    private static String suggestionAtRow(TranslucentTextField field, int row) {
        assertTrue(field.mouseClickedSuggestion(LIST_COLUMN, rowY(row)));
        return field.getValue();
    }
}
