package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointManagerScreenLayoutTest {
    @Test
    void onlyTheFixedWidthMiddlePartIsCentered() {
        int screenWidth = 854;
        WaypointManagerScreen.ManagerLayoutGeometry geometry =
                WaypointManagerScreen.calculateLayoutGeometry(screenWidth, 480);

        assertEquals(228, geometry.middlePanelWidth());
        assertEquals(24, geometry.leftPanelWidth());
        assertEquals(screenWidth / 2, geometry.middleX() + 110);
        assertEquals(317, geometry.middleX());
        assertEquals(289, geometry.leftX());
        assertEquals(4, geometry.panelGap());
    }

    @Test
    void middleWidthRemainsFixedAtDifferentScreenWidths() {
        WaypointManagerScreen.ManagerLayoutGeometry wide =
                WaypointManagerScreen.calculateLayoutGeometry(854, 480);
        WaypointManagerScreen.ManagerLayoutGeometry narrower =
                WaypointManagerScreen.calculateLayoutGeometry(740, 480);

        assertEquals(228, wide.middlePanelWidth());
        assertEquals(228, narrower.middlePanelWidth());
        assertEquals(317, wide.middleX());
        assertEquals(260, narrower.middleX());
        assertEquals(232, narrower.leftX());
    }

    @Test
    void verticalSpaceGoesToTheDimensionAndWaypointLists() {
        WaypointManagerScreen.ManagerLayoutGeometry geometry =
                WaypointManagerScreen.calculateLayoutGeometry(854, 480);

        assertEquals(260, geometry.contentHeight());
        assertEquals(178, geometry.dimensionListHeight());
        assertEquals(245, geometry.waypointListHeight(11));

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
    void reducedMiddleWidthKeepsTheLeftPartOnScreenAtCompactWidths() {
        WaypointManagerScreen.ManagerLayoutGeometry commonCompactWidth =
                WaypointManagerScreen.calculateLayoutGeometry(400, 480);
        WaypointManagerScreen.ManagerLayoutGeometry extremelyCompactWidth =
                WaypointManagerScreen.calculateLayoutGeometry(320, 480);

        assertEquals(58, commonCompactWidth.leftPanelX());
        assertEquals(commonCompactWidth.leftX(), commonCompactWidth.controlX());
        assertEquals(-10, commonCompactWidth.leftDropdownEdge(4));
        assertEquals(22, commonCompactWidth.dropdownXOffset(4));
        assertEquals(400 / 2, commonCompactWidth.middleX() + 110);
        assertEquals(18, extremelyCompactWidth.leftPanelX());
        assertEquals(-50, extremelyCompactWidth.leftDropdownEdge(4));
        assertEquals(62, extremelyCompactWidth.dropdownXOffset(4));
        assertEquals(320 / 2, extremelyCompactWidth.middleX() + 110);
        assertEquals(228, extremelyCompactWidth.middlePanelWidth());
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
        assertEquals(4, geometry.panelGap());
        assertEquals(4, geometry.contentHeight() - 11 - geometry.waypointListHeight(11));
    }
}
