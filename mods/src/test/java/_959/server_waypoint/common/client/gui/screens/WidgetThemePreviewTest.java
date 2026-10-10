package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.screens.PreviewPacking.Placement;
import _959.server_waypoint.common.client.gui.screens.WidgetThemePreview.PreviewList;
import _959.server_waypoint.common.client.gui.screens.WidgetThemePreview.Sample;
import _959.server_waypoint.common.client.gui.screens.WidgetThemePreview.SampleDropdown;
import _959.server_waypoint.common.client.gui.screens.WidgetThemePreview.SampleRow;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.IconButton;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.SettingsListWidget;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The preview's parts that run without a game: its rows of samples, its list's popups and their routing,
 * the dropdown sample and the rules for activity and markers. The preview itself builds buttons and toggles,
 * which read the running game's font, so it isn't built here.
 */
class WidgetThemePreviewTest {
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    // A row holding an 11-pixel sample is 17 pixels high, the control centered 3 pixels down.
    private static final int ROW = 17;
    private static final int COMBOBOX_Y = 3;
    private static final int DROPDOWN_Y = ROW + 3;

    @Test
    void aRowPutsItsSamplesWhereThePackingPlacedThemCenteredOnTheTallest() {
        TestFont font = new TestFont();
        ScalableText text = new ScalableText(0, 0, Component.literal("ab"), 0xFFFFFFFF, font);
        ComboBoxWidget combobox = combobox(font);
        IconButton button = button(20);
        List<List<Sample>> families = List.of(List.of(Sample.of(text)), List.of(Sample.of(combobox)),
                List.of(Sample.of(button)));
        List<List<Placement>> rows = PreviewPacking.pack(List.of(List.of(12), List.of(112), List.of(13)), 195,
                WidgetThemePreview.SAMPLE_GAP, WidgetThemePreview.FAMILY_GAP);
        assertEquals(1, rows.size());

        SampleRow row = new SampleRow(195, rows.get(0), families);
        row.setPosition(10, 40);

        assertEquals(195, row.getWidth());
        assertEquals(20, row.getHeight(), "as tall as its tallest sample: the wide row around it adds the padding");
        // The families 12 pixels apart: the text, the combobox's outline and the button.
        assertEquals(10, text.getX());
        assertEquals(10 + 12 + 12, combobox.getX());
        assertEquals(10 + 12 + 12 + 112 + 12, button.getX());
        // Centered on the 20-pixel button, rounding toward the top.
        assertEquals(40 + 5, text.getY());
        assertEquals(40 + 4, combobox.getY());
        assertEquals(40, button.getY());
    }

    @Test
    void aRowReportsTheWidgetsAmongItsSamplesInOrder() {
        TestFont font = new TestFont();
        ScalableText text = new ScalableText(0, 0, Component.literal("ab"), 0xFFFFFFFF, font);
        IconButton first = button(13);
        IconButton second = button(13);
        List<List<Sample>> families = List.of(List.of(Sample.of(first), Sample.of(text), Sample.of(second)));
        SampleRow row = new SampleRow(195, PreviewPacking.pack(List.of(List.of(13, 12, 13)), 195,
                WidgetThemePreview.SAMPLE_GAP, WidgetThemePreview.FAMILY_GAP).get(0), families);

        List<AbstractWidget> widgets = new ArrayList<>();
        row.visitWidgets(widgets::add);

        assertEquals(List.of(first, second), widgets);
        // Samples of a family are 6 pixels apart: the 13-pixel button, the 12-pixel text, the other button.
        assertEquals(first.getX() + 13 + 6 + 12 + 6, second.getX());
        assertEquals(first.getX() + 13 + 6, text.getX());
    }

    @Test
    void aWideRowPadsItsSamplesOnceAndShowsOnlyWidgetsEntirelyInView() {
        TestFont font = new TestFont();
        ComboBoxWidget combobox = combobox(font);
        IconButton button = button(20);
        List<List<Sample>> families = List.of(List.of(Sample.of(combobox), Sample.of(button)));
        SampleRow row = new SampleRow(195, PreviewPacking.pack(List.of(List.of(112, 13)), 195,
                WidgetThemePreview.SAMPLE_GAP, WidgetThemePreview.FAMILY_GAP).get(0), families);
        SettingsListWidget list = new SettingsListWidget(font);
        list.setWidth(200);
        list.setHeight(20);

        list.setEntries(List.of(new SettingsListWidget.WideRow(row)));

        assertEquals(20 + 6, list.getContentHeight());
        // The button spans 3 to 23, past the 20-pixel view; the combobox spans 7 to 18.
        assertTrue(combobox.visible);
        assertFalse(button.visible);
    }

    @Test
    void anOpenPopupTakesAClickBeforeAnotherOwnerBeneathIt() {
        Fixture fixture = fixture(40, 0);
        // The dropdown in the second row opens upward, over the combobox in the first.
        fixture.dropdown.setExpansionDirection(LayoutFlow.Direction.REVERSE);

        assertSame(fixture.dropdown, fixture.list.clickPopup(30, DROPDOWN_Y + 5, LEFT));
        assertTrue(fixture.dropdown.isExpanded());
        // Choice 1 is chosen, so the popup shows Choice 2 at y -2 and Choice 3 at y 9, over the combobox.
        assertTrue(fixture.list.isMouseOverPopup(30, 11));

        assertSame(fixture.dropdown, fixture.list.clickPopup(30, 11, LEFT));

        assertEquals(SampleDropdown.choiceLabel(2), fixture.dropdown.triggerLabel());
        assertFalse(fixture.dropdown.isExpanded());
        assertFalse(fixture.combobox.isExpanded());
    }

    @Test
    void aClickElsewhereClosesThePopupAndGoesOn() {
        Fixture fixture = fixture(40, 0);
        // The combobox's arrow, in the last 14 pixels of its 112.
        assertSame(fixture.combobox, fixture.list.clickPopup(105, COMBOBOX_Y + 5, LEFT));
        assertTrue(fixture.combobox.isExpanded());
        assertTrue(fixture.list.isMouseOverPopup(50, COMBOBOX_Y + 11 + 5));

        assertNull(fixture.list.clickPopup(150, 30, LEFT));

        assertFalse(fixture.combobox.isExpanded());
        assertFalse(fixture.list.isMouseOverPopup(50, COMBOBOX_Y + 11 + 5));
    }

    @Test
    void theWheelOverAnOpenPopupGoesToThePopupAndLeavesTheListWhereItIs() {
        Fixture fixture = fixture(40, 2);
        fixture.combobox.setExpanded(true);

        // The popup's two choices are 112 pixels wide, under the combobox: y 14 to 36.
        assertTrue(fixture.list.scroll(50, 20, 0, -1));

        assertEquals(0.0, fixture.list.getScrollY());
        assertTrue(fixture.combobox.isExpanded());
    }

    @Test
    void theWheelElsewhereOverTheListClosesThePopupAndScrolls() {
        Fixture fixture = fixture(40, 2);
        fixture.combobox.setExpanded(true);

        assertTrue(fixture.list.scroll(150, 20, 0, -1));

        assertEquals(10.0, fixture.list.getScrollY());
        assertFalse(fixture.combobox.isExpanded());
    }

    @Test
    void aListThatFitsLeavesTheWheelToTheWidgetUnderThePointerAndKeepsThePopup() {
        Fixture fixture = fixture(100, 0);
        fixture.combobox.setExpanded(true);

        assertFalse(fixture.list.scroll(150, 20, 0, -1));

        assertTrue(fixture.combobox.isExpanded());
    }

    @Test
    void anyScrollOfTheListClosesThePopups() {
        Fixture fixture = fixture(40, 2);
        fixture.dropdown.setExpanded(true);

        // As reveal or a Tab target in view would scroll it.
        fixture.list.setScrollY(5);
        assertFalse(fixture.dropdown.isExpanded());

        fixture.dropdown.setExpanded(true);
        fixture.list.setScrollY(5);
        assertTrue(fixture.dropdown.isExpanded(), "setting the scroll it already has doesn't move the list");
    }

    @Test
    void theDropdownSampleKeepsTheChoiceMade() {
        SampleDropdown dropdown = new SampleDropdown(new TestFont());
        dropdown.setPosition(0, 0);
        assertEquals(SampleDropdown.choiceLabel(0), dropdown.triggerLabel());

        assertTrue(dropdown.mouseClicked(5, 5, LEFT));
        // Choice 1 is chosen, so the popup shows Choice 2 at y 11 and Choice 3 at y 22.
        assertTrue(dropdown.mouseClicked(5, 25, LEFT));

        assertEquals(SampleDropdown.choiceLabel(2), dropdown.triggerLabel());
        assertFalse(dropdown.isExpanded());
    }

    @Test
    void theDisabledButtonAndTheSecondSliderAreNeverActive() {
        for (PreviewSample sample : PreviewSample.values()) {
            boolean inactiveLook = sample == PreviewSample.DISABLED_BUTTON || sample == PreviewSample.DISABLED_SLIDER;
            assertEquals(!inactiveLook, WidgetThemePreview.sampleActive(sample, true), sample.name());
            assertFalse(WidgetThemePreview.sampleActive(sample, false), sample.name());
        }
    }

    @Test
    void aSampleIsMarkedWhileAnyOfItIsInView() {
        // A view from y 10 to y 50.
        assertFalse(WidgetThemePreview.partlyInView(0, 10, 10, 50), "it ends where the view starts");
        assertTrue(WidgetThemePreview.partlyInView(0, 11, 10, 50));
        assertTrue(WidgetThemePreview.partlyInView(20, 30, 10, 50));
        assertTrue(WidgetThemePreview.partlyInView(49, 60, 10, 50));
        assertFalse(WidgetThemePreview.partlyInView(50, 60, 10, 50), "it starts where the view ends");
    }

    /**
     * A list 200 wide and {@code height} high at the origin: the combobox's row, the dropdown's row, then
     * {@code extraRows} rows of an 11-pixel button, each 17 pixels high.
     */
    private static Fixture fixture(int height, int extraRows) {
        TestFont font = new TestFont();
        ComboBoxWidget combobox = combobox(font);
        SampleDropdown dropdown = new SampleDropdown(font);
        PreviewList list = new PreviewList(font, List.of(combobox, dropdown));
        list.setWidth(200);
        list.setHeight(height);
        List<SettingsListWidget.Entry> rows = new ArrayList<>();
        rows.add(new SettingsListWidget.WideRow(combobox));
        rows.add(new SettingsListWidget.WideRow(dropdown));
        for (int i = 0; i < extraRows; i++) {
            rows.add(new SettingsListWidget.WideRow(button(11)));
        }
        list.setEntries(rows);
        return new Fixture(list, combobox, dropdown);
    }

    /** The preview's combobox: 112 wide, moved back by its 2-pixel text inset so it is placed by its outline. */
    private static ComboBoxWidget combobox(TestFont font) {
        ComboBoxWidget combobox = new ComboBoxWidget(0, 0, 112, Component.empty(), font,
                List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"), "minecraft:overworld",
                value -> {
                });
        combobox.setOffsets(2, 2);
        return combobox;
    }

    private static IconButton button(int height) {
        return new IconButton(0, 0, 13, height, Component.literal("Button"), null, () -> {
        });
    }

    private record Fixture(PreviewList list, ComboBoxWidget combobox, SampleDropdown dropdown) {
    }
}
