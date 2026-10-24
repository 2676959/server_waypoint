package _959.server_waypoint.perf.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record FixtureRecipe(JsonObject json, String sha256) {
    public static FixtureRecipe load(Path path) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        if (json.get("schemaVersion").getAsInt() != 1 || json.get("seed").getAsLong() != 95920261024L) {
            throw new IllegalArgumentException("Unsupported recipe schema/seed");
        }
        for (String key : new String[]{"id", "targetRuntime", "dataVersion", "bounds", "structures", "placements", "initialInventories", "waypointDataset", "activation", "expectedState", "requiredTickingRegions", "hashes"}) {
            if (!json.has(key)) {
                throw new IllegalArgumentException("Missing recipe field: " + key);
            }
        }
        return new FixtureRecipe(json, FixtureManifest.hash(path));
    }
}
