package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServerListWidgetStateTest {
    @Test
    void stateColorsFollowSeverity() {
        assertEquals(WidgetThemeVariable.TEXT_MUTED, ServerListWidget.stateColor(RemoteCatalogState.AVAILABLE));
        assertEquals(WidgetThemeVariable.WARNING, ServerListWidget.stateColor(RemoteCatalogState.STALE));
        assertEquals(WidgetThemeVariable.DANGER, ServerListWidget.stateColor(RemoteCatalogState.UNAVAILABLE));
        assertEquals(WidgetThemeVariable.DANGER, ServerListWidget.stateColor(RemoteCatalogState.UNAUTHORIZED));
    }

    @Test
    void onlyDegradedServersGetABadge() {
        assertNull(ServerListWidget.badgeColor(RemoteCatalogState.AVAILABLE));
        assertEquals(WidgetThemeVariable.WARNING, ServerListWidget.badgeColor(RemoteCatalogState.STALE));
        assertEquals(WidgetThemeVariable.DANGER, ServerListWidget.badgeColor(RemoteCatalogState.UNAVAILABLE));
        assertEquals(WidgetThemeVariable.DANGER, ServerListWidget.badgeColor(RemoteCatalogState.UNAUTHORIZED));
    }

    @Test
    void stateLabelsUseTheRemoteStateTranslations() {
        assertEquals("waypoint.remote.state.available",
                ServerListWidget.stateTranslationKey(RemoteCatalogState.AVAILABLE));
        assertEquals("waypoint.remote.state.stale",
                ServerListWidget.stateTranslationKey(RemoteCatalogState.STALE));
        assertEquals("waypoint.remote.state.unavailable",
                ServerListWidget.stateTranslationKey(RemoteCatalogState.UNAVAILABLE));
    }
}
