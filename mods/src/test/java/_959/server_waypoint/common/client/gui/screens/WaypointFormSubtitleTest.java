package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The subtitle under the form's title wraps to the panel's width on up to two lines instead of being cut
 * to one, and the header grows with each line so a second line never overlaps the panel.
 * {@link TestFont#wrapping()} makes every character 6 pixels wide and breaks lines between characters.
 */
class WaypointFormSubtitleTest {
    private static final int LINE_HEIGHT = 9;

    @BeforeAll
    static void bootstrapRegistries() {
        //? if fabric {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        //?} else {
        /*org.junit.jupiter.api.Assumptions.abort("A form needs Minecraft's registries, which plain JUnit bootstraps only on Fabric");
        *///?}
    }

    @Test
    void aSubtitleAsWideAsThePanelStaysOnOneLine() throws Exception {
        WaypointFormTestForm.withClient(TestFont.wrapping(), () -> {
            int line = charactersPerLine();

            assertEquals(contentHeight("x"), contentHeight("x".repeat(line)));
        });
    }

    @Test
    void aSubtitleWiderThanThePanelWrapsOntoASecondLineAndTheHeaderGrowsByIt() throws Exception {
        WaypointFormTestForm.withClient(TestFont.wrapping(), () -> {
            int line = charactersPerLine();

            assertEquals(contentHeight("x") + LINE_HEIGHT, contentHeight("x".repeat(line + 1)));
        });
    }

    @Test
    void aSubtitleNeedingMoreThanTwoLinesStopsAtTwo() throws Exception {
        WaypointFormTestForm.withClient(TestFont.wrapping(), () -> {
            int line = charactersPerLine();

            assertEquals(contentHeight("x") + LINE_HEIGHT, contentHeight("x".repeat(3 * line)));
        });
    }

    /** The screen is tall enough that the rows keep their widest gap, whatever the header holds. */
    private static WaypointFormTestForm layOut(String subtitle) throws Exception {
        return new WaypointFormTestForm(subtitle, List.of()).layOut(400, 1000);
    }

    private static int contentHeight(String subtitle) throws Exception {
        return layOut(subtitle).getContentHeight();
    }

    /** How many characters fit on a line as wide as the panel. */
    private static int charactersPerLine() throws Exception {
        return layOut("x").getContentWidth() / TestFont.CHARACTER_WIDTH;
    }
}
