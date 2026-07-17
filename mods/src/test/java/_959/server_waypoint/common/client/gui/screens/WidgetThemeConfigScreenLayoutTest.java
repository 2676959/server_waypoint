package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WidgetThemeConfigScreenLayoutTest {
    private static final int CONTENT_X = 17;
    private static final int CONTENT_Y = 25;
    private static final int FOOTER_WIDTH = 202;
    private static final int FOOTER_HEIGHT = 13;

    @Test
    void markedOuterColumnAndFooterGapsUseEightPixels() {
        WidgetThemeConfigScreen.ThemeEditorLayoutGeometry geometry = geometry();

        assertEquals(8, geometry.contentX() - geometry.panel().x());
        assertEquals(8, geometry.gallery().x() - geometry.variableList().right());
        assertEquals(8, geometry.panel().right() - geometry.gallery().right());
        assertEquals(8, geometry.footer().y() - geometry.gallery().bottom());
        assertEquals(8, geometry.panel().bottom() - geometry.footer().bottom());
    }

    @Test
    void leftPanelsUseTheListsVisualBoundsAndShareTheirBorder() {
        WidgetThemeConfigScreen.ThemeEditorLayoutGeometry geometry = geometry();
        WidgetThemeConfigScreen.LayoutRectangle list = geometry.variableList();
        WidgetThemeConfigScreen.LayoutRectangle controls = geometry.editorControls();
        WidgetThemeConfigScreen.LayoutRectangle listContent = list.inset(2);

        assertEquals(controls.x(), list.x());
        assertEquals(controls.right(), list.right());
        assertEquals(controls.y(), list.bottom());
        assertEquals(geometry.gallery().y(), list.y());
        assertEquals(geometry.gallery().bottom(), controls.bottom());
        assertEquals(19, listContent.x());
        assertEquals(45, listContent.y());
        assertEquals(138, listContent.width());
        assertEquals(74, listContent.height());
    }

    @Test
    void lowerEditorAndGalleryContentKeepTheirInnerClearance() {
        WidgetThemeConfigScreen.ThemeEditorLayoutGeometry geometry = geometry();
        WidgetThemeConfigScreen.LayoutRectangle status = geometry.status(6);
        int dangerChipBottom = geometry.gallery().y() + 136;

        assertEquals(8, geometry.editorControls().bottom() - (geometry.opacitySliderY() + 11));
        assertEquals(3, status.y() - dangerChipBottom);
        assertEquals(7, geometry.gallery().bottom() - status.bottom());
        assertEquals(geometry.gallery().x() + 7, status.x());
        assertEquals(geometry.gallery().width() - 14, status.width());
    }

    @Test
    void footerRemainsCenteredAfterCompactingTheStatusBand() {
        WidgetThemeConfigScreen.ThemeEditorLayoutGeometry geometry = geometry();

        assertEquals(
                geometry.contentX() + 196,
                geometry.footer().x() + geometry.footer().width() / 2
        );
    }

    private static WidgetThemeConfigScreen.ThemeEditorLayoutGeometry geometry() {
        return WidgetThemeConfigScreen.calculateLayoutGeometry(
                CONTENT_X,
                CONTENT_Y,
                FOOTER_WIDTH,
                FOOTER_HEIGHT
        );
    }
}
