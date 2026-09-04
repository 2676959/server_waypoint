package _959.server_waypoint.live;

import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class FoliaLiveTestFixtureTool {
    private static final String DIMENSION = "minecraft:overworld";
    private static final String CONTROL_LIST = "control";
    private static final String LARGE_LIST = "large";
    private static final int CONTROL_REVISION = 7;
    private static final int LARGE_REVISION = 19;
    private static final int LARGE_WAYPOINTS = 4_092;
    private static final int MINIMUM_LARGE_FRAMES = 9;
    private static final Type WAYPOINT_LISTS_TYPE = new TypeToken<List<WaypointList>>() { }.getType();

    private FoliaLiveTestFixtureTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: FoliaLiveTestFixtureTool <generate|verify> <fixture-dir> [server-waypoint-file]"
            );
        }
        Path fixtureDirectory = Path.of(args[1]).toAbsolutePath().normalize();
        switch (args[0]) {
            case "generate" -> generate(fixtureDirectory);
            case "verify" -> {
                if (args.length != 3) {
                    throw new IllegalArgumentException("verify requires the live server waypoint file");
                }
                verify(fixtureDirectory, Path.of(args[2]).toAbsolutePath().normalize());
            }
            default -> throw new IllegalArgumentException("Unknown fixture action: " + args[0]);
        }
    }

    private static void generate(Path fixtureDirectory) throws IOException {
        Files.createDirectories(fixtureDirectory);
        List<WaypointList> lists = createLists();
        Path waypointFile = fixtureDirectory.resolve("minecraft$overworld.json");
        Gson waypointGson = waypointGson();
        try (Writer writer = Files.newBufferedWriter(waypointFile, StandardCharsets.UTF_8)) {
            waypointGson.toJson(lists, WAYPOINT_LISTS_TYPE, writer);
        }

        DimensionWaypointData dimension = new DimensionWaypointData(DIMENSION, lists);
        int compressedFrames = ChunkedMessageManager.prepare(
                WaypointData.world(List.of(dimension)),
                true
        ).frames().size();
        int uncompressedFrames = ChunkedMessageManager.prepare(
                WaypointData.world(List.of(dimension)),
                false
        ).frames().size();
        if (compressedFrames < MINIMUM_LARGE_FRAMES) {
            throw new IllegalStateException(
                    "Large fixture compressed to " + compressedFrames
                            + " frames; expected at least " + MINIMUM_LARGE_FRAMES
            );
        }

        JsonObject manifest = new JsonObject();
        manifest.addProperty("dimension", DIMENSION);
        manifest.addProperty("waypointFile", waypointFile.getFileName().toString());
        manifest.addProperty("waypointFileSha256", sha256(waypointFile));
        manifest.addProperty("compressedFrames", compressedFrames);
        manifest.addProperty("uncompressedFrames", uncompressedFrames);
        manifest.addProperty("controlList", CONTROL_LIST);
        manifest.addProperty("controlRevision", CONTROL_REVISION);
        manifest.addProperty("controlWaypoints", controlWaypoints().size());
        manifest.addProperty("largeList", LARGE_LIST);
        manifest.addProperty("largeRevision", LARGE_REVISION);
        manifest.addProperty("largeWaypoints", LARGE_WAYPOINTS);
        JsonArray expectedControl = JsonParser.parseString(
                waypointGson.toJson(controlWaypoints())
        ).getAsJsonArray();
        manifest.add("expectedControlContents", expectedControl);
        try (Writer writer = Files.newBufferedWriter(
                fixtureDirectory.resolve("fixture-manifest.json"),
                StandardCharsets.UTF_8
        )) {
            new GsonBuilder().setPrettyPrinting().create().toJson(manifest, writer);
        }

        writeOfflineOperators(fixtureDirectory.resolve("ops.json"));
        System.out.printf(
                "Generated %s: control=%d, large=%d, compressedFrames=%d, sha256=%s%n",
                waypointFile,
                controlWaypoints().size(),
                LARGE_WAYPOINTS,
                compressedFrames,
                sha256(waypointFile)
        );
    }

    private static void verify(Path fixtureDirectory, Path serverWaypointFile) throws IOException {
        Gson gson = waypointGson();
        JsonObject manifest;
        try (Reader reader = Files.newBufferedReader(
                fixtureDirectory.resolve("fixture-manifest.json"),
                StandardCharsets.UTF_8
        )) {
            manifest = new Gson().fromJson(reader, JsonObject.class);
        }
        List<WaypointList> lists;
        try (Reader reader = Files.newBufferedReader(serverWaypointFile, StandardCharsets.UTF_8)) {
            lists = gson.fromJson(reader, WAYPOINT_LISTS_TYPE);
        }
        WaypointList control = lists.stream()
                .filter(list -> CONTROL_LIST.equals(list.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing control waypoint list"));
        int expectedRevision = manifest.get("controlRevision").getAsInt();
        if (control.getSyncNum() != expectedRevision) {
            throw new IllegalStateException(
                    "Control revision mismatch: expected " + expectedRevision
                            + ", found " + control.getSyncNum()
            );
        }
        JsonElement expected = manifest.get("expectedControlContents");
        JsonElement actual = JsonParser.parseString(gson.toJson(control.simpleWaypoints()));
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Control waypoint contents do not match the manifest");
        }
        System.out.printf(
                "Verified control fixture: revision=%d, waypoints=%d, file=%s%n",
                control.getSyncNum(),
                control.size(),
                serverWaypointFile
        );
    }

    private static List<WaypointList> createLists() {
        return List.of(
                new WaypointList(CONTROL_LIST, CONTROL_REVISION, controlWaypoints()),
                new WaypointList(LARGE_LIST, LARGE_REVISION, largeWaypoints())
        );
    }

    private static List<SimpleWaypoint> controlWaypoints() {
        // Xaero persists a 16-color palette, so these must survive a download/upload unchanged.
        return List.of(
                new SimpleWaypoint("alpha-home", "AH", new WaypointPos(12, 72, -30), 0xFF5555, 0, false),
                new SimpleWaypoint("bravo-home", "BH", new WaypointPos(8_204, 80, 8_180), 0x5555FF, 90, false),
                new SimpleWaypoint("shared-spawn", "SS", new WaypointPos(0, 96, 0), 0xAAAAAA, -90, true),
                new SimpleWaypoint("probe-anchor", "PA", new WaypointPos(16_384, 70, 0), 0xFFAA00, 180, false)
        );
    }

    private static List<SimpleWaypoint> largeWaypoints() {
        List<SimpleWaypoint> waypoints = new ArrayList<>(LARGE_WAYPOINTS);
        for (int index = 0; index < LARGE_WAYPOINTS; index++) {
            String first = deterministicHex("name-a-" + index);
            String second = deterministicHex("name-b-" + index).substring(0, 32);
            String name = "large-" + index + "-" + first + second;
            waypoints.add(new SimpleWaypoint(
                    name,
                    "L" + index % 10,
                    new WaypointPos(index * 3, 64 + index % 32, -(index * 5)),
                    (index * 2_654_435_761L & 0xFFFFFFL) == 0
                            ? 0x010101
                            : (int) (index * 2_654_435_761L & 0xFFFFFFL),
                    index % 360 - 180,
                    index % 17 == 0
            ));
        }
        return waypoints;
    }

    private static Gson waypointGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(WaypointPos.class, new WaypointPos.WaypointPosAdapter())
                .excludeFieldsWithoutExposeAnnotation()
                .setExclusionStrategies(WaypointList.exclusionStrategy(true))
                .create();
    }

    private static String deterministicHex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[16 * 1_024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void writeOfflineOperators(Path path) throws IOException {
        JsonArray operators = new JsonArray();
        for (String username : List.of("SWAlpha", "SWBravo", "SWProbe")) {
            JsonObject operator = new JsonObject();
            UUID uuid = UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)
            );
            operator.addProperty("uuid", uuid.toString());
            operator.addProperty("name", username);
            operator.addProperty("level", 4);
            operator.addProperty("bypassesPlayerLimit", false);
            operators.add(operator);
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(operators, writer);
        }
    }
}
