package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.stream.IntStream;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointListWidgetSessionStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    @TempDir
    private Path tempDir;
    private WaypointFilesManagerCore filesManager;
    private Field clientInstance;
    private Object previousClient;

    @BeforeEach
    void resetBeforeTest() throws ReflectiveOperationException {
        WaypointListWidget.resetSessionStates();
        this.filesManager = new WaypointFilesManagerCore(this.tempDir);
        putWaypoints(OVERWORLD, 20);
        putWaypoints(NETHER, 20);
        this.clientInstance = Minecraft.class.getDeclaredField("instance");
        this.clientInstance.setAccessible(true);
        this.previousClient = this.clientInstance.get(null);
        Minecraft client = allocate(Minecraft.class);
        GameRenderer renderer = allocate(GameRenderer.class);
        Field camera = GameRenderer.class.getDeclaredField("mainCamera");
        camera.setAccessible(true);
        camera.set(renderer, allocate(Camera.class));
        Field gameRenderer = Minecraft.class.getDeclaredField("gameRenderer");
        gameRenderer.setAccessible(true);
        gameRenderer.set(client, renderer);
        this.clientInstance.set(null, client);
    }

    @AfterEach
    void resetAfterTest() throws ReflectiveOperationException {
        WaypointListWidget.resetSessionStates();
        this.clientInstance.set(null, this.previousClient);
    }

    @Test
    void allDimensionsScrollAndExpansionStateAreRetainedTogether() {
        WaypointListWidget.rememberSessionScrollPosition(true, OVERWORLD, 45.0D);
        WaypointListWidget.setDimensionExpanded(OVERWORLD, false);

        assertEquals(45.0D, WaypointListWidget.getSessionScrollPosition(true, OVERWORLD));
        assertFalse(WaypointListWidget.isDimensionExpanded(OVERWORLD));
    }

    @Test
    void reopeningRestoresSelectedDimensionAfterInitialPreferencesAndLayout() {
        WaypointListWidget first = widget();
        first.setSelectedDimension(OVERWORLD);
        first.setScrollY(145.0D);

        WaypointListWidget reopened = widget();
        reopened.setGroupByLists(true);
        reopened.setShowAllDimensions(false);
        reopened.setHeight(80);
        reopened.setSelectedDimension(OVERWORLD);

        assertEquals(145.0D, reopened.getScrollY());
    }

    @Test
    void dimensionSwitchesRestoreIndependentPositions() {
        WaypointListWidget list = widget();
        list.setSelectedDimension(OVERWORLD);
        list.setScrollY(145.0D);
        list.setSelectedDimension(NETHER);
        assertEquals(0.0D, list.getScrollY());
        list.setScrollY(65.0D);
        list.setSelectedDimension(OVERWORLD);
        assertEquals(145.0D, list.getScrollY());
        list.setSelectedDimension(NETHER);
        assertEquals(65.0D, list.getScrollY());
    }

    @Test
    void reselectingTheSameDimensionPreservesScroll() {
        WaypointListWidget list = widget();
        list.setSelectedDimension(OVERWORLD);
        list.setScrollY(145.0D);
        list.setSelectedDimension(OVERWORLD);

        assertEquals(145.0D, list.getScrollY());
    }

    @Test
    void allDimensionsHasItsOwnPositionAndSidebarSelectionDoesNotResetIt() {
        WaypointListWidget list = widget();
        list.setSelectedDimension(OVERWORLD);
        list.setScrollY(145.0D);
        list.setShowAllDimensions(true);
        assertEquals(0.0D, list.getScrollY());
        list.setScrollY(245.0D);
        list.setSelectedDimension(NETHER);
        assertEquals(245.0D, list.getScrollY());
        list.setShowAllDimensions(false);
        assertEquals(0.0D, list.getScrollY());
        list.setScrollY(65.0D);
        list.setShowAllDimensions(true);
        assertEquals(245.0D, list.getScrollY());
        list.setShowAllDimensions(false);
        assertEquals(65.0D, list.getScrollY());
        list.setSelectedDimension(OVERWORLD);
        assertEquals(145.0D, list.getScrollY());
    }

    @Test
    void reopeningAllDimensionsRestoresAfterInitialQueriesAndLayout() {
        WaypointListWidget list = widget();
        list.setShowAllDimensions(true);
        list.setSelectedDimension(OVERWORLD);
        list.setScrollY(245.0D);

        WaypointListWidget reopened = widget();
        reopened.setGroupByLists(true);
        reopened.setShowAllDimensions(true);
        reopened.setHeight(100);
        reopened.setSelectedDimension(NETHER);

        assertEquals(245.0D, reopened.getScrollY());
    }

    @Test
    void scrollbarDragIsRememberedEvenBeforeMouseRelease() {
        WaypointListWidget list = widget();
        list.setSelectedDimension(OVERWORLD);
        assertTrue(list.mouseClicked(199, 10, InputConstants.MOUSE_BUTTON_LEFT));
        assertTrue(list.mouseDragged(199, 30, InputConstants.MOUSE_BUTTON_LEFT, 0, 20));
        double draggedPosition = list.getScrollY();
        assertTrue(draggedPosition > 0);

        WaypointListWidget reopened = widget();
        reopened.setSelectedDimension(OVERWORLD);
        assertEquals(draggedPosition, reopened.getScrollY());
    }

    @Test
    void reopeningClampsSavedPositionAgainstChangedRows() {
        WaypointListWidget list = widget();
        list.setSelectedDimension(OVERWORLD);
        list.setScrollY(300.0D);
        putWaypoints(OVERWORLD, 5);

        WaypointListWidget reopened = widget();
        reopened.setSelectedDimension(OVERWORLD);
        assertEquals(60.0D, reopened.getMaxScroll());
        assertEquals(reopened.getMaxScroll(), reopened.getScrollY());

        putWaypoints(OVERWORLD, 20);
        reopened.setSelectedDimension(NETHER);
        reopened.setSelectedDimension(OVERWORLD);
        assertEquals(60.0D, reopened.getScrollY());
    }

    @Test
    void refreshAndResizeClampAndRememberTheCurrentPosition() {
        WaypointListWidget list = widget();
        list.setSelectedDimension(OVERWORLD);
        list.setScrollY(300.0D);
        putWaypoints(OVERWORLD, 5);
        list.refreshView();
        assertEquals(60.0D, list.getScrollY());
        list.setHeight(100);
        assertEquals(20.0D, list.getScrollY());

        WaypointListWidget reopened = widget();
        reopened.setSelectedDimension(OVERWORLD);
        assertEquals(20.0D, reopened.getScrollY());
        putWaypoints(OVERWORLD, 0);
        reopened.refreshView();
        assertEquals(0.0D, reopened.getScrollY());
    }

    @Test
    void sessionResetClearsEveryDimensionsPosition() {
        WaypointListWidget list = widget();
        list.setSelectedDimension(OVERWORLD);
        list.setScrollY(145.0D);
        list.setSelectedDimension(NETHER);
        list.setScrollY(65.0D);

        WaypointListWidget.resetSessionStates();

        WaypointListWidget reopened = widget();
        reopened.setSelectedDimension(OVERWORLD);
        assertEquals(0.0D, reopened.getScrollY());
        reopened.setSelectedDimension(NETHER);
        assertEquals(0.0D, reopened.getScrollY());
    }

    @Test
    void sessionResetClearsScrollAndExpansionState() {
        WaypointListWidget.rememberSessionScrollPosition(true, OVERWORLD, 45.0D);
        WaypointListWidget.setDimensionExpanded(OVERWORLD, false);

        WaypointListWidget.resetSessionStates();

        assertEquals(0.0D, WaypointListWidget.getSessionScrollPosition(true, OVERWORLD));
        assertTrue(WaypointListWidget.isDimensionExpanded(OVERWORLD));
    }

    @Test
    void expansionStateIsStoredPerDimension() {
        WaypointListWidget.setDimensionExpanded(OVERWORLD, false);

        assertFalse(WaypointListWidget.isDimensionExpanded(OVERWORLD));
        assertTrue(WaypointListWidget.isDimensionExpanded(NETHER));
    }

    private WaypointListWidget widget() {
        return new WaypointListWidget(0, 0, 200, 60, null,
                new WaypointQueryEngine(this.filesManager), new TestFont(), selection -> {});
    }

    private void putWaypoints(String dimension, int count) {
        this.filesManager.putWaypointList(dimension, new WaypointList("places", 1,
                IntStream.range(0, count)
                        .mapToObj(index -> new SimpleWaypoint("waypoint " + index, "W", index, 64, 0, 0xFFFFFF, 0, false))
                        .toList()));
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }
}
