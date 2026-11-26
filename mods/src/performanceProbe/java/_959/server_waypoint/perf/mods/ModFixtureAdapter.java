package _959.server_waypoint.perf.mods;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

final class ModFixtureAdapter {
    static final String MARKER = "server_waypoint:probe";
    private static final BlockPos POSITION = new BlockPos(8, 80, 8);
    private Path directory;
    private BlockState previous;
    private boolean placed;
    private boolean activated;

    static String hash(Path file) throws Exception {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Probe identity is not a jar: " + file);
        }
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    void load(Path path) throws Exception {
        directory = path.toRealPath();
        if (!Files.isDirectory(directory) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Fixture directory is not a real directory");
        }
    }

    void place(MinecraftServer server, Path path) throws Exception {
        load(path);
        Path marker = directory.resolve("swperf-placed.json");
        if (Files.exists(marker)) {
            throw new IllegalStateException("Refusing to overwrite a placed fixture");
        }
        ServerLevel level = server.overworld();
        level.getChunk(POSITION);
        previous = level.getBlockState(POSITION);
        level.setBlockAndUpdate(POSITION, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity chest = chest(level);
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag tag = new CompoundTag();
        tag.putString(MARKER, "placed");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        chest.setItem(0, stack);
        placed = true;
        Files.writeString(marker, "{\"marker\":\"placed\",\"dataVersion\":" + SharedRuntime.dataVersion() + "}");
    }

    void activate() {
        if (!placed) {
            throw new IllegalStateException("Fixture is not placed");
        }
        activated = true;
    }

    void restore(MinecraftServer server) {
        if (!placed) {
            return;
        }
        ServerLevel level = server.overworld();
        level.getChunk(POSITION);
        level.setBlockAndUpdate(POSITION, previous);
        placed = false;
        activated = false;
        if (directory != null) {
            try {
                Files.deleteIfExists(directory.resolve("swperf-placed.json"));
            } catch (Exception exception) {
                throw new IllegalStateException("Fixture cleanup failed", exception);
            }
        }
    }

    JsonObject snapshot(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getChunk(POSITION);
        boolean chestPresent = level.getBlockEntity(POSITION) instanceof ChestBlockEntity;
        JsonObject result = new JsonObject();
        result.addProperty("dataVersion", SharedRuntime.dataVersion());
        result.addProperty("chest", chestPresent);
        result.addProperty("marker", chestPresent ? marker(chest(level).getItem(0)) : "");
        result.addProperty("activated", activated);
        result.addProperty("placed", placed);
        return result;
    }

    private static ChestBlockEntity chest(ServerLevel level) {
        if (!(level.getBlockEntity(POSITION) instanceof ChestBlockEntity chest)) {
            throw new IllegalStateException("Native chest is absent");
        }
        return chest;
    }

    private static String marker(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return "";
        }
        return data.copyTag().getString(MARKER).orElse("");
    }
}
