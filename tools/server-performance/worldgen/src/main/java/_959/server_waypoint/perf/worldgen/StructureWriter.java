package _959.server_waypoint.perf.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.tag.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StructureWriter {
    private StructureWriter() {}

    static String safeName(String value) {
        if (!value.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]*") || value.contains("..")) {
            throw new IllegalArgumentException("Unsafe template name");
        }
        return value;
    }

    public static void generate(Path recipePath, Path output) throws IOException {
        output = output.toAbsolutePath();
        for (Path parent = output; parent != null; parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) {
                throw new IllegalArgumentException("Symlink output/ancestor refused");
            }
        }
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Output must be new; never edit a live save");
        }
        FixtureRecipe recipe = FixtureRecipe.load(recipePath);
        Files.createDirectories(output);
        JsonObject templates = new JsonObject();
        for (JsonElement value : recipe.json().getAsJsonArray("structures")) {
            JsonObject structure = value.getAsJsonObject().deepCopy();
            String name = safeName(structure.remove("file").getAsString());
            structure.addProperty("DataVersion", recipe.json().get("dataVersion").getAsInt());
            CompoundTag tag = (CompoundTag) tag(structure, "");
            Path file = output.resolve(name);
            NBTUtil.write(tag, file.toFile(), true);
            templates.addProperty(name, FixtureManifest.hash(file));
        }
        FixtureManifest.write(recipe, templates, output);
    }

    private static Tag<?> tag(JsonElement value, String key) {
        if (value.isJsonObject()) {
            CompoundTag compound = new CompoundTag();
            for (var entry : value.getAsJsonObject().entrySet()) {
                compound.put(entry.getKey(), tag(entry.getValue(), entry.getKey()));
            }
            return compound;
        }
        if (value.isJsonArray()) {
            var array = value.getAsJsonArray();
            ListTag<?> list = ListTag.createUnchecked(array.isEmpty() ? EndTag.class : tag(array.get(0), "").getClass());
            for (JsonElement item : value.getAsJsonArray()) {
                list.addUnchecked(tag(item, ""));
            }
            return list;
        }
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) {
            return new StringTag(primitive.getAsString());
        }
        if (primitive.isBoolean()) {
            return new ByteTag((byte) (primitive.getAsBoolean() ? 1 : 0));
        }
        if (key.equals("Slot")) {
            return new ByteTag(primitive.getAsByte());
        }
        return new IntTag(primitive.getAsInt());
    }
}
