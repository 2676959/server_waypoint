package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.TextDisplayTransformation;
import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.ChatFont;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.translation.TranslationFilesTest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.translation.Translator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static _959.server_waypoint.text.feedback.Fixtures.END;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static _959.server_waypoint.text.feedback.Fixtures.TWILIGHT;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every screen with the spec's sample data (spec 21): it fits chat, uses vanilla glyphs and reads in plain
 * text with the colours of chat.
 */
class ScreenAuditTest {
    /** Screens only a player with the mod ever receives; they keep their tooltips. */
    private static final Set<String> PLAYER_ONLY = Set.of("upload result", "arrival");
    /**
     * Lines of translations that pass 320 px, so chat wraps them, and that the maintainers accepted on
     * 2026-10-02. Each is spelled as {@link #everyScreenFitsChatInEveryLocale()} reports it: add one to
     * accept a wrap, and delete it once its translation fits.
     */
    private static final Set<String> ACCEPTED_WRAPS = Set.of(
            "es_es, added: 331 px: ✔ Añadido [IF] Iron Farm a Farms   Detalles · Navegar · Deshacer",
            "es_es, created list: 331 px: ✔ Creada la lista Storage en Mundo principal   Añadir aquí · Abrir",
            "es_es, no list: 332 px: ✘ No hay ninguna lista llamada Farm en Mundo principal. Ver listas",
            "es_es, edit DIMENSION_NOT_FOUND: 345 px: ✘ No hay ninguna dimensión llamada minecraft:overworld. Dimensiones",
            "es_es, edit LIST_NOT_FOUND: 338 px: ✘ No hay ninguna lista llamada Farms en Mundo principal. Ver listas",
            "es_es, edit WAYPOINT_NOT_FOUND: 323 px: ✘ No hay ningún punto llamado Iron Farm en Farms. Abrir Farms",
            "es_es, edit ENCODING_FAILED: 337 px: ✘ Ese cambio es demasiado grande para enviarlo a los jugadores.",
            "es_es, edit UPLOAD_BUSY: 343 px: ✘ La edición está en pausa mientras se sube algo. Inténtalo pronto.",
            "es_es, navigation: 385 px: ✔ Brújula · ✔ Mapa · ✔ Barra de jefe · ✔ Barra de acción · ✔ Texto flotante",
            "es_es, upload result: 343 px: 2 conflictos mantuvieron la versión del servidor.  Preferir los míos",
            "es_es, upload result: 357 px: 2 dimensiones cambiaron mientras tanto; no se actualizaron. Reintentar",
            "es_es, switch STALE_CATALOG: 354 px: ✘ Survival está desactualizado; espera a que se actualice. Reintentar",
            "es_es, switch UNSUPPORTED: 327 px: ✘ Este servidor no puede llevar jugadores a otros servidores.",
            "es_es, switch INVALID_REQUEST: 351 px: ✘ Survival respondió algo que no coincide con la solicitud. Reintentar");

    private static Map<String, Component> screens(Viewer viewer) {
        DimensionStyle dims = Fixtures.dims(viewer);
        WaypointList home = Fixtures.homeBases();
        WaypointList farms = Fixtures.farms();
        SimpleWaypoint mainHome = home.getWaypointByName("Main Home");
        SimpleWaypoint iron = farms.getWaypointByName("Iron Farm");
        WaypointList storage = new WaypointList("Storage", 1, List.of());
        PlacedWaypoint target = new PlacedWaypoint(OVERWORLD, home, mainHome);
        Map<String, List<WaypointList>> overworld = Map.of(OVERWORLD, Fixtures.overworldLists());
        WaypointQueryEngine.Query all = new WaypointQueryEngine.Query("", SortMode.DEFAULT, viewer.position(), viewer.dimension(), false);
        WaypointQueryEngine.Query farm = new WaypointQueryEngine.Query("farm", SortMode.DEFAULT, viewer.position(), viewer.dimension(), false);
        ListScreen.Totals totals = new ListScreen.Totals(3, 14);
        Map<String, Component> screens = new LinkedHashMap<>();
        screens.put("menu", MenuScreen.menu(viewer, dims, target, true));
        screens.put("help", HelpScreen.index(viewer));
        for (HelpTopics.Topic topic : HelpTopics.Topic.values()) {
            screens.put("help " + topic.id(), HelpScreen.topic(viewer, topic, true));
        }
        screens.put("tree", ListScreen.dimension(dims, OVERWORLD, totals, WaypointQueryEngine.queryLists(overworld, all),
                ListQuery.DEFAULT, 10));
        screens.put("lists", ListScreen.dimension(dims, OVERWORLD, totals, WaypointQueryEngine.queryLists(overworld, all),
                ListQuery.DEFAULT.withView(ListView.LISTS), 10));
        screens.put("flat", ListScreen.dimension(dims, OVERWORLD, totals, WaypointQueryEngine.queryLists(overworld, all),
                ListQuery.DEFAULT.withView(ListView.FLAT), 10));
        screens.put("search", ListScreen.dimension(dims, OVERWORLD, totals, WaypointQueryEngine.queryLists(overworld, farm),
                ListQuery.DEFAULT.withSearch("farm"), 10));
        screens.put("list", ListScreen.list(dims, OVERWORLD, farms,
                WaypointQueryEngine.queryLists(Map.of(OVERWORLD, List.of(farms)), all), ListQuery.DEFAULT, 10));
        screens.put("empty list", ListScreen.list(dims, NETHER, storage,
                WaypointQueryEngine.queryLists(Map.of(NETHER, List.of(storage)), all), ListQuery.DEFAULT, 10));
        List<DimensionScreens.DimensionLists> dimensions = List.of(
                new DimensionScreens.DimensionLists(OVERWORLD, Fixtures.overworldLists()),
                new DimensionScreens.DimensionLists(NETHER, List.of(storage)),
                new DimensionScreens.DimensionLists(END, List.of()),
                new DimensionScreens.DimensionLists(TWILIGHT, List.of()));
        screens.put("dimensions", DimensionScreens.dimensionList(dims, dimensions, 1, 10));
        screens.put("all", DimensionScreens.all(dims, dimensions, ListQuery.DEFAULT, 10));
        screens.put("all search", DimensionScreens.allSearch(dims, WaypointQueryEngine.queryLists(overworld, farm),
                ListQuery.DEFAULT.withSearch("farm"), 10));
        screens.put("details", DetailsScreen.waypoint(dims, OVERWORLD, home, mainHome, translatable("wp.updated.color")));
        screens.put("list details", DetailsScreen.list(dims, OVERWORLD, home, null));
        screens.put("colour picker", PickerScreens.color(dims, OVERWORLD, home, mainHome));
        screens.put("facing picker", PickerScreens.facing(dims, OVERWORLD, home, mainHome));
        screens.put("add picker", PickerScreens.add(dims, OVERWORLD, Fixtures.overworldLists(), 1, 10));
        screens.put("added", Results.added(dims, OVERWORLD, farms, iron));
        screens.put("created list", Results.createdList(dims, OVERWORLD, storage));
        screens.put("removed", Results.removed(dims, OVERWORLD, home, mainHome, "r12"));
        screens.put("removed list", Results.removedList(dims, NETHER, storage));
        screens.put("restored", Results.restored(dims, OVERWORLD, home, mainHome));
        screens.put("teleported", Results.teleported(dims, OVERWORLD, home, mainHome));
        screens.put("reloaded", Results.reloaded(List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw")));
        screens.put("sent", Results.sent(12));
        screens.put("broadcast", Broadcasts.added(dims, text("Steve"), OVERWORLD, farms, iron));
        screens.put("broadcast list", Broadcasts.removedList(dims, text("Steve"), NETHER, storage));
        screens.put("no list", Errors.noList(dims, OVERWORLD, "Farm"));
        screens.put("no waypoint", Errors.noWaypoint(dims, OVERWORLD, "Farms", "Gate"));
        screens.put("list exists", Errors.listExists(dims, OVERWORLD, farms));
        screens.put("waypoint exists", Errors.waypointExists(dims, OVERWORLD, farms, iron));
        screens.put("list not empty", Errors.listNotEmpty(dims, OVERWORLD, farms));
        for (EditResultStatus status : EditResultStatus.values()) {
            if (status != EditResultStatus.SUCCESS) {
                screens.put("edit " + status, Errors.edit(dims, status, OVERWORLD, "Farms", "Iron Farm"));
            }
        }
        screens.put("sharing", SharingPrompt.found(dims, OVERWORLD, iron, Fixtures.overworldLists()));
        screens.put("sharing dimension", SharingPrompt.unknownDimension(dims, "mars:mars", iron));
        screens.put("navigation", NavigationScreens.panel(dims, target, EnumSet.allOf(NavigationMethod.class),
                EnumSet.allOf(NavigationMethod.class), translatable("wp.navigation.started")));
        screens.put("not navigating", NavigationScreens.idle(dims));
        screens.put("stopped", NavigationScreens.stopped(dims, target));
        screens.put("text display", NavigationScreens.textDisplay(viewer, TextDisplayTransformation.defaultValue(),
                translatable("wp.text_display.updated")));
        screens.put("upload", UploadScreens.panel(viewer));
        screens.put("upload result", UploadScreens.result(new UploadScreens.Outcome(UploadTarget.XAERO, 3, 1, 2, 9, 2, 1,
                2, true, true, "/wp upload xaero force local", "/wp upload xaero")));
        Server survival = new Server(new RemoteServerId("survival"), "Survival", RemoteCatalogState.AVAILABLE,
                Map.of(OVERWORLD, Fixtures.overworldLists()));
        Server creative = new Server(new RemoteServerId("creative-1"), "Creative Plots", RemoteCatalogState.STALE,
                Map.of(OVERWORLD, List.of(farms)));
        Server lobby = new Server(new RemoteServerId("lobby"), "Lobby", RemoteCatalogState.UNAVAILABLE, Map.of());
        List<Server> servers = List.of(survival, creative, lobby);
        screens.put("remote picker", RemoteScreens.picker(viewer, servers, 1, 10));
        screens.put("remote all", RemoteScreens.allServers(viewer, servers, ListQuery.DEFAULT, 10));
        screens.put("remote server", RemoteScreens.server(viewer, survival, ListQuery.DEFAULT, 10));
        screens.put("remote search", RemoteScreens.search(viewer, null, servers, ListQuery.DEFAULT.withSearch("farm"), 10));
        screens.put("remote dimension", RemoteScreens.dimension(viewer, survival, OVERWORLD, ListQuery.DEFAULT, 10));
        screens.put("remote list", RemoteScreens.list(viewer, survival, OVERWORLD, farms, ListQuery.DEFAULT, 10));
        screens.put("remote details", RemoteScreens.details(viewer, survival, OVERWORLD, home, mainHome));
        screens.put("unreachable", RemoteScreens.server(viewer, lobby, ListQuery.DEFAULT, 10));
        screens.put("switching", RemoteScreens.switching(viewer, survival, OVERWORLD, farms, iron));
        for (Result result : Result.values()) {
            if (result != Result.SUCCESS) {
                screens.put("switch " + result, RemoteScreens.teleportFailed(viewer, survival, "survival", OVERWORLD,
                        "Farms", "Iron Farm", result));
            }
        }
        screens.put("arrival", RemoteScreens.arrival(Result.SUCCESS, "survival", "Iron Farm", iron));
        return screens;
    }

    @Test
    void everyScreenFitsChatForPlayers() {
        screens(Fixtures.player()).forEach((name, screen) -> {
            try {
                ChatAssert.assertFitsChat(screen);
            } catch (AssertionError problem) {
                throw new AssertionError(name + ": " + problem.getMessage(), problem);
            }
            assertFalse(ChatAssert.render(screen).contains("<missing"), name + ": " + ChatAssert.render(screen));
        });
    }

    @Test
    void everyScreenFitsChatInEveryLocale() {
        Map<String, Component> screens = screens(Fixtures.player());
        List<String> problems = new ArrayList<>();
        for (String code : TranslationFilesTest.LOCALES) {
            Locale locale = Translator.parseLocale(code);
            screens.forEach((name, screen) -> ChatAssert.fitProblems(screen, locale)
                    .forEach(problem -> problems.add(code + ", " + name + ": " + problem)));
        }
        List<String> unaccepted = problems.stream().filter(problem -> !ACCEPTED_WRAPS.contains(problem)).toList();
        List<String> fitNow = ACCEPTED_WRAPS.stream().filter(wrap -> !problems.contains(wrap)).sorted().toList();
        assertTrue(unaccepted.isEmpty(), unaccepted.size() + " problems:\n" + String.join("\n", unaccepted));
        assertTrue(fitNow.isEmpty(), "Accepted wraps that no longer happen, delete them:\n" + String.join("\n", fitNow));
    }

    @Test
    void everyTooltipUsesTheVanillaFont() {
        screens(Fixtures.player()).forEach((name, screen) -> {
            for (ChatAssert.Run run : ChatAssert.runs(screen)) {
                HoverEvent<?> hover = run.style().hoverEvent();
                if (hover == null || !(hover.value() instanceof Component tooltip)) {
                    continue;
                }
                String text = ChatAssert.render(tooltip);
                assertFalse(text.contains("<missing"), name + " tooltip: " + text);
                text.codePoints().filter(codePoint -> codePoint != '\n' && !ChatFont.isVanillaGlyph(codePoint)).findFirst()
                        .ifPresent(codePoint -> fail(name + " tooltip uses " + new String(Character.toChars(codePoint))
                                + ": " + text));
            }
        });
    }

    @Test
    void plainTextScreensNeedNoHoverOrClick() {
        Map<String, Component> console = screens(Fixtures.console());
        console.forEach((name, screen) -> {
            if (!PLAYER_ONLY.contains(name)) {
                assertNoInteraction(name, screen);
            }
            assertFalse(ChatAssert.render(screen).contains("<missing"), name + ": " + ChatAssert.render(screen));
        });
        assertTrue(ChatAssert.render(console.get("tree")).startsWith("Overworld (minecraft:overworld)"));
        assertTrue(ChatAssert.render(console.get("tree")).contains("[MH] Main Home · 120, 64, -35"));
        assertTrue(ChatAssert.render(console.get("flat")).contains("[IF] Iron Farm · Farms · 300, 80, 150"));
        assertTrue(ChatAssert.render(console.get("remote dimension")).contains("[MH] Main Home · 120, 64, -35"));
        assertTrue(ChatAssert.render(console.get("remote picker")).contains("● Survival (survival) · 14"));
        assertTrue(ChatAssert.render(console.get("removed")).contains("Restore with /wp restore r12"));
    }

    @Test
    void plainTextScreensColourEveryPiece() {
        screens(Fixtures.console()).forEach((name, screen) -> {
            for (ChatAssert.Run run : ChatAssert.runs(screen)) {
                if (!run.text().isBlank() && run.style().color() == null) {
                    fail(name + " leaves \"" + run.text() + "\" uncoloured for plain-text viewers:\n"
                            + ChatAssert.render(screen));
                }
            }
        });
    }

    @Test
    void plainTextScreensColourWhatTheyShareWithChatTheSameWay() {
        Map<String, Component> chat = screens(Fixtures.player());
        screens(Fixtures.console()).forEach((name, screen) -> {
            Map<String, Set<TextColor>> chatColours = new HashMap<>();
            for (ChatAssert.Run run : ChatAssert.runs(chat.get(name))) {
                chatColours.computeIfAbsent(run.text().strip(), text -> new HashSet<>()).add(run.style().color());
            }
            for (ChatAssert.Run run : ChatAssert.runs(screen)) {
                Set<TextColor> colours = chatColours.get(run.text().strip());
                if (!run.text().isBlank() && colours != null && !colours.contains(run.style().color())) {
                    fail(name + ": \"" + run.text().strip() + "\" is " + run.style().color()
                            + " for plain-text viewers but " + colours + " in chat");
                }
            }
        });
    }

    private static void assertNoInteraction(String name, Component component) {
        if (component.clickEvent() != null || component.hoverEvent() != null) {
            fail(name + " has a " + (component.clickEvent() != null ? "click" : "tooltip")
                    + " for plain-text viewers: " + ChatAssert.render(component));
        }
        component.children().forEach(child -> assertNoInteraction(name, child));
        if (component instanceof TranslatableComponent translatable) {
            translatable.arguments().forEach(argument -> assertNoInteraction(name, argument.asComponent()));
        }
    }
}
