package _959.server_waypoint.navigation;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/** Stores explored navigation terrain separately from the player's active map region. */
public final class NavigationMapTerrainStore {
    private static final int PIXEL_COUNT = 128 * 128;
    private static final Gson GSON = new Gson();
    private final Path directory;

    public NavigationMapTerrainStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /** Saves the outgoing region and restores the destination without exploring it again. */
    public byte[] switchRegion(int mapId, Region current, byte[] colors, Region target) {
        if (colors.length != PIXEL_COUNT) {
            throw new IllegalArgumentException("Navigation map terrain must contain 16384 pixels");
        }
        if (current.equals(target)) {
            return colors;
        }
        this.save(mapId, current, colors);
        return this.restore(mapId, target);
    }

    /** Returns blank pixels only when this map has never saved the requested region. */
    public byte[] restore(int mapId, Region region) {
        try {
            String encoded;
            try {
                encoded = Files.readString(this.file(mapId, region), StandardCharsets.UTF_8);
            } catch (NoSuchFileException missing) {
                return new byte[PIXEL_COUNT];
            }
            Snapshot snapshot = GSON.fromJson(encoded, Snapshot.class);
            if (snapshot == null || !region.equals(snapshot.region()) || snapshot.colors() == null) {
                throw new IllegalStateException("Invalid navigation map terrain snapshot");
            }
            byte[] colors = Base64.getDecoder().decode(snapshot.colors());
            if (colors.length != PIXEL_COUNT) {
                throw new IllegalStateException("Invalid navigation map terrain pixel count");
            }
            return colors;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not restore navigation map terrain", exception);
        }
    }

    private void save(int mapId, Region region, byte[] colors) {
        Path file = this.file(mapId, region);
        Snapshot snapshot = new Snapshot(region, Base64.getEncoder().encodeToString(colors));
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "terrain-", ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(snapshot), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not save navigation map terrain", exception);
        }
    }

    private Path file(int mapId, Region region) {
        if (mapId < 0) {
            throw new IllegalArgumentException("Navigation map ID must not be negative");
        }
        String key = UUID.nameUUIDFromBytes(GSON.toJson(region).getBytes(StandardCharsets.UTF_8)).toString();
        return this.directory.resolve(Integer.toString(mapId)).resolve(key + ".json");
    }

    public record Region(String dimension, int centerX, int centerZ, int scale) {
        public Region {
            Objects.requireNonNull(dimension, "dimension");
            if (dimension.isBlank() || scale < 0 || scale > 4) {
                throw new IllegalArgumentException("Invalid navigation map region");
            }
        }
    }

    private record Snapshot(Region region, String colors) {
    }
}
