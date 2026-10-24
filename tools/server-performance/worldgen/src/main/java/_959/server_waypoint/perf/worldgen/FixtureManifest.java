package _959.server_waypoint.perf.worldgen;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FixtureManifest {
    private FixtureManifest() {}

    public static String hash(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void write(FixtureRecipe recipe, JsonObject templates, Path output) throws IOException {
        JsonObject manifest = recipe.json().deepCopy();
        manifest.remove("structures");
        manifest.addProperty("recipeSha256", recipe.sha256());
        manifest.add("templates", templates);
        Files.writeString(output.resolve("manifest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n");
    }

    public static void verify(Path manifestPath, Path input) throws IOException {
        JsonObject manifest = JsonParser.parseString(Files.readString(manifestPath)).getAsJsonObject();
        if (manifest.get("schemaVersion").getAsInt() != 1) {
            throw new IllegalArgumentException("Unknown manifest schema");
        }
        for (var entry : manifest.getAsJsonObject("templates").entrySet()) {
            Path file = input.resolve(StructureWriter.safeName(entry.getKey()));
            if (Files.isSymbolicLink(file) || !hash(file).equals(entry.getValue().getAsString())) {
                throw new IllegalArgumentException("Template hash mismatch: " + file);
            }
        }
    }
}
