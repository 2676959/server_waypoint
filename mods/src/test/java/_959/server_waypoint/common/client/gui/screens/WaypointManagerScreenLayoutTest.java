package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointManagerScreenLayoutTest {
    @Test
    void allThreePanelsAreCenteredTogether() {
        int screenWidth = 854;
        WaypointManagerScreen.ManagerLayoutGeometry geometry =
                WaypointManagerScreen.calculateLayoutGeometry(screenWidth, 480);

        assertEquals(333, geometry.middlePanelWidth());
        assertEquals(24, geometry.leftPanelWidth());
        assertEquals(screenWidth / 2, (geometry.leftPanelX()
                + geometry.detailsPanelX() + geometry.detailsPanelWidth()) / 2);
        assertEquals(136, geometry.middleX());
        assertEquals(110, geometry.leftX());
        assertEquals(2, geometry.panelGap());
        assertEquals(467, geometry.detailsPanelX());
        assertEquals(281, geometry.detailsPanelWidth());
        assertEquals(471, geometry.detailsContentX());
        assertEquals(273, geometry.detailsContentWidth());
    }

    @Test
    void panelWidthsGrowWithTheViewport() {
        WaypointManagerScreen.ManagerLayoutGeometry wide =
                WaypointManagerScreen.calculateLayoutGeometry(854, 480);
        WaypointManagerScreen.ManagerLayoutGeometry narrower =
                WaypointManagerScreen.calculateLayoutGeometry(740, 480);

        assertEquals(333, wide.middlePanelWidth());
        assertEquals(289, narrower.middlePanelWidth());
        assertEquals(281, wide.detailsPanelWidth());
        assertEquals(245, narrower.detailsPanelWidth());
        assertTrue(wide.completeWidth() > narrower.completeWidth());
    }

    @Test
    void panelWidthsStopGrowingAtTheirClamps() {
        WaypointManagerScreen.ManagerLayoutGeometry clamped =
                WaypointManagerScreen.calculateLayoutGeometry(1280, 720);
        WaypointManagerScreen.ManagerLayoutGeometry wider =
                WaypointManagerScreen.calculateLayoutGeometry(1920, 1080);

        assertEquals(368, clamped.middlePanelWidth());
        assertEquals(328, clamped.detailsPanelWidth());
        assertEquals(clamped.middlePanelWidth(), wider.middlePanelWidth());
        assertEquals(clamped.detailsPanelWidth(), wider.detailsPanelWidth());
    }

    @Test
    void verticalSpaceGoesToTheDimensionAndWaypointLists() {
        WaypointManagerScreen.ManagerLayoutGeometry geometry =
                WaypointManagerScreen.calculateLayoutGeometry(854, 480);

        assertEquals(394, geometry.contentHeight());
        assertEquals(292, geometry.dimensionListHeight());
        assertEquals(379, geometry.waypointListHeight(11));

        WaypointManagerScreen.ManagerLayoutGeometry shorter =
                WaypointManagerScreen.calculateLayoutGeometry(854, 200);
        assertTrue(shorter.dimensionListHeight() < geometry.dimensionListHeight());
        assertTrue(shorter.waypointListHeight(11) < geometry.waypointListHeight(11));
    }

    @Test
    void panelHeightStaysWithinTheConfiguredScreenMargins() {
        WaypointManagerScreen.ManagerLayoutGeometry geometry =
                WaypointManagerScreen.calculateLayoutGeometry(854, 200);

        assertEquals(164, geometry.contentHeight());
        assertEquals(172, geometry.panelHeight());
        assertEquals(14, geometry.panelY());
        assertEquals(186, geometry.panelY() + geometry.panelHeight());
    }

    @Test
    void completeGroupRemainsCenteredAtCompactWidths() {
        WaypointManagerScreen.ManagerLayoutGeometry commonCompactWidth =
                WaypointManagerScreen.calculateLayoutGeometry(400, 480);
        WaypointManagerScreen.ManagerLayoutGeometry extremelyCompactWidth =
                WaypointManagerScreen.calculateLayoutGeometry(320, 480);

        assertEquals(13, commonCompactWidth.leftPanelX());
        assertEquals(commonCompactWidth.leftX(), commonCompactWidth.controlX());
        assertEquals(-55, commonCompactWidth.leftDropdownEdge(4));
        assertEquals(67, commonCompactWidth.dropdownXOffset(4));
        assertEquals(400 / 2, (commonCompactWidth.leftPanelX()
                + commonCompactWidth.detailsPanelX() + commonCompactWidth.detailsPanelWidth()) / 2);
        assertEquals(12, extremelyCompactWidth.leftPanelX());
        assertEquals(-56, extremelyCompactWidth.leftDropdownEdge(4));
        assertEquals(68, extremelyCompactWidth.dropdownXOffset(4));
        assertEquals(320 / 2, (extremelyCompactWidth.leftPanelX()
                + extremelyCompactWidth.detailsPanelX() + extremelyCompactWidth.detailsPanelWidth()) / 2);
        assertEquals(145, extremelyCompactWidth.middlePanelWidth());
        assertEquals(158, commonCompactWidth.detailsPanelWidth());
        assertEquals(123, extremelyCompactWidth.detailsPanelWidth());
    }

    @Test
    void completeLayoutFitsCompactGuiScaleFourViewports() {
        WaypointManagerScreen.ManagerLayoutGeometry geometry =
                WaypointManagerScreen.calculateLayoutGeometry(427, 240);

        assertEquals(26, geometry.leftPanelX());
        assertEquals(400, geometry.detailsPanelX() + geometry.detailsPanelWidth());
        assertEquals(374, geometry.completeWidth());
        assertTrue(geometry.panelY() >= 12);
        assertTrue(geometry.panelY() + geometry.panelHeight() <= 240 - 12);
    }

    @Test
    void markedPanelAndSectionPaddingsUseTheSameCompactRhythm() {
        WaypointManagerScreen.ManagerLayoutGeometry geometry =
                WaypointManagerScreen.calculateLayoutGeometry(854, 480);

        assertEquals(4, geometry.contentY() - geometry.panelY());
        assertEquals(4, geometry.panelY() + geometry.panelHeight()
                - (geometry.contentY() + geometry.contentHeight()));
        assertEquals(4, geometry.leftX() - geometry.leftPanelX());
        assertEquals(4, geometry.leftPanelX() + geometry.leftPanelWidth()
                - (geometry.leftX() + 16));
        assertEquals(2, geometry.panelGap());
        assertEquals(2, geometry.detailsPanelX()
                - (geometry.middlePanelX() + geometry.middlePanelWidth()));
        assertEquals(4, geometry.contentHeight() - 11 - geometry.waypointListHeight(11));
    }

    @Test
    void contentHeightUsesPercentageAndMaximumClamp() {
        WaypointManagerScreen.ManagerLayoutGeometry medium =
                WaypointManagerScreen.calculateLayoutGeometry(854, 480);
        WaypointManagerScreen.ManagerLayoutGeometry tall =
                WaypointManagerScreen.calculateLayoutGeometry(1280, 1000);

        assertEquals(394, medium.contentHeight());
        assertEquals(400, tall.contentHeight());
    }
}
