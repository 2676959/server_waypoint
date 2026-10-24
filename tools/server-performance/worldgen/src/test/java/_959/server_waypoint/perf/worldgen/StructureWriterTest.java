package _959.server_waypoint.perf.worldgen;

import com.google.gson.JsonParser;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StructureWriterTest {
    @TempDir Path temporary;

    @BeforeEach void canonicalTemporaryRoot() throws Exception {
        temporary = temporary.toRealPath();
    }

    private Path recipe(String id) {
        return Path.of("../config/worlds/" + id + "-small.json");
    }

    @Test void sameRecipeProducesSameTemplates() throws Exception {
        StructureWriter.generate(recipe("container-stone"), temporary.resolve("a"));
        StructureWriter.generate(recipe("container-stone"), temporary.resolve("b"));
        assertArrayEquals(Files.readAllBytes(temporary.resolve("a/cell.nbt")), Files.readAllBytes(temporary.resolve("b/cell.nbt")));
        assertArrayEquals(Files.readAllBytes(temporary.resolve("a/manifest.json")), Files.readAllBytes(temporary.resolve("b/manifest.json")));
    }

    @Test void hopperFacingAndInventoryMatchRecipe() throws Exception {
        StructureWriter.generate(recipe("container-stone"), temporary.resolve("a"));
        CompoundTag root = (CompoundTag) NBTUtil.read(temporary.resolve("a/cell.nbt").toFile()).getTag();
        assertEquals(4671, root.getInt("DataVersion"));
        assertEquals(8, root.getListTag("blocks").size());
        assertEquals("east", root.getListTag("palette").asCompoundTagList().get(0).getCompoundTag("Properties").getString("facing"));
        CompoundTag item = root.getListTag("blocks").asCompoundTagList().get(0).getCompoundTag("nbt").getListTag("Items").asCompoundTagList().get(0);
        assertEquals("minecraft:stone", item.getString("id"));
        assertEquals(16, item.getInt("count"));
    }

    @Test void negativeChunkBoundaryCoordinatesPreserved() throws Exception {
        StructureWriter.generate(recipe("container-stone"), temporary.resolve("a"));
        var manifest = JsonParser.parseString(Files.readString(temporary.resolve("a/manifest.json"))).getAsJsonObject();
        assertEquals(95920261024L, manifest.get("seed").getAsLong());
        var first = manifest.getAsJsonArray("placements").get(0).getAsJsonObject();
        assertEquals(-17, first.get("x").getAsInt());
        assertEquals(-17, first.get("z").getAsInt());
        assertEquals(16, manifest.getAsJsonArray("placements").size());
    }

    @Test void nestedItemMetadataPreserved() throws Exception {
        StructureWriter.generate(recipe("container-shulker-books"), temporary.resolve("a"));
        CompoundTag root = (CompoundTag) NBTUtil.read(temporary.resolve("a/cell.nbt").toFile()).getTag();
        CompoundTag item = root.getListTag("blocks").asCompoundTagList().get(0).getCompoundTag("nbt").getListTag("Items").asCompoundTagList().get(0);
        var books = item.getCompoundTag("components").getListTag("minecraft:container").asCompoundTagList();
        assertEquals(27, books.size());
        assertEquals(1, books.get(0).getCompoundTag("item").getCompoundTag("components").getCompoundTag("minecraft:stored_enchantments").getInt("minecraft:mending"));
    }

    @Test void rejectsLiveOrExistingOutput() throws Exception {
        Files.createDirectory(temporary.resolve("existing"));
        assertThrows(IllegalArgumentException.class, () -> StructureWriter.generate(recipe("container-stone"), temporary.resolve("existing")));
        Files.createSymbolicLink(temporary.resolve("alias"), temporary);
        assertThrows(IllegalArgumentException.class, () -> StructureWriter.generate(recipe("container-stone"), temporary.resolve("alias/new")));
    }
}
