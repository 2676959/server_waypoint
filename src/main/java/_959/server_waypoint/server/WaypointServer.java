package _959.server_waypoint.server;

import _959.server_waypoint.ServerWaypointFabric;
import _959.server_waypoint.network.waypoint.DimensionWaypoint;
import _959.server_waypoint.network.waypoint.WorldWaypoint;
import _959.server_waypoint.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.server.waypoint.DimensionManager;
import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import _959.server_waypoint.config.Config;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static _959.server_waypoint.ServerWaypointFabric.LOGGER;
import static _959.server_waypoint.util.DimensionFileHelper.getDimensionKey;
import static _959.server_waypoint.util.SimpleWaypointHelper.DEFAULT_STYLE;
import static _959.server_waypoint.util.TextHelper.text;
import static _959.server_waypoint.util.SimpleWaypointHelper.simpleWaypointToFormattedText;
import static _959.server_waypoint.util.CommandGenerator.tpCmd;
import static _959.server_waypoint.util.TextHelper.waypointInfoText;

import com.google.gson.Gson;

public class WaypointServer {
    public static int EDITION = 0;
    public static Config CONFIG = new Config();
    public static WaypointServer INSTANCE;
    public MinecraftServer MINECRAFT_SERVER;
    public Path waypointsDir;
    public Path editionFile;
    public Path configFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final byte[] DEFAULT_CONFIG = gson.toJson(CONFIG).getBytes();
    private LinkedHashMap<RegistryKey<World>, DimensionManager> dimensionManagerMap;

    public void loadConfig(FileReader reader) {
        CONFIG = gson.fromJson(reader, Config.class);
    }
    
    public void initServer() throws IOException {
        this.dimensionManagerMap = new LinkedHashMap<>();
        this.dimensionManagerMap.put(World.OVERWORLD, null);
        this.dimensionManagerMap.put(World.NETHER, null);
        this.dimensionManagerMap.put(World.END, null);

        WaypointServer.INSTANCE = this;
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("server_waypoint");
        Path waypointsFolder = configDir.resolve("waypoints");
        this.editionFile = configDir.resolve("EDITION");
        this.configFile = configDir.resolve("config.json");
        
        try {
            if (!Files.exists(waypointsFolder) || !Files.isDirectory(waypointsFolder)) {
                Files.createDirectories(waypointsFolder);
                LOGGER.info("Created server_waypoints/waypoints directory at: {}", waypointsFolder);
            }

            // Read or create VERSION file
            if (!Files.exists(this.editionFile) || !Files.isRegularFile(this.editionFile)) {
                try (DataOutputStream out = new DataOutputStream(new FileOutputStream(this.editionFile.toFile()))) {
                    out.writeInt(EDITION);
                    LOGGER.info("Created EDITION file with edition: {}", EDITION);
                }
            } else {
                try (DataInputStream in = new DataInputStream(new FileInputStream(this.editionFile.toFile()))) {
                    EDITION = in.readInt();
                    LOGGER.info("Read EDITION from file: {}", EDITION);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize server_waypoints directory or EDITION file", e);
            throw e;
        }

        try {
            if (!Files.exists(this.configFile) || !Files.isRegularFile(this.configFile)) {
                Files.createFile(this.configFile);
                Files.write(this.configFile, DEFAULT_CONFIG);
                LOGGER.info("Created config file at: {}", this.configFile);
            } else {
                this.loadConfig(new FileReader(configFile.toFile()));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read config file, use default config instead", e);
        }

        this.waypointsDir = waypointsFolder;
        for (Path path : Files.newDirectoryStream(waypointsFolder)) {
            String fileName = path.getFileName().toString().replace(".txt", "");
            if (fileName.startsWith("dim%")) {
                RegistryKey<World> dimensionKey = getDimensionKey(fileName);
                if (dimensionKey == null) {
                    LOGGER.error("Invalid dimension file name {}, skip", fileName);
                    continue;
                }
                LOGGER.info("Found dimension file: {}, id: {}", fileName, dimensionKey.getValue());
                DimensionManager dimLists = new DimensionManager(dimensionKey, this.waypointsDir);
                this.dimensionManagerMap.put(dimensionKey, dimLists);
                if (Files.exists(dimLists.dimensionFilePath)) {
                    LOGGER.info("Loading waypointList from: {}", dimLists.dimensionFilePath);
                    try {
                        dimLists.readDimension();
                    } catch (IOException e) {
                        LOGGER.error("Failed to load dimension file", e);
                        throw e;
                    }
                }
            }
        }
    }

    @Nullable
    public WorldWaypoint toWorldWaypoint() {
        List<DimensionWaypoint> dimensionWaypoints = new ArrayList<>();
        for (DimensionManager dimensionManager : this.dimensionManagerMap.values()) {
            if (dimensionManager != null) {
                dimensionWaypoints.add(dimensionManager.toDimensionWaypoint());
            }
        }
        if (dimensionWaypoints.isEmpty()) {
            return null;
        }
        return new WorldWaypoint(dimensionWaypoints);
    }

    public Map<RegistryKey<World>, DimensionManager> getDimensionManagerMap() {
        return this.dimensionManagerMap;
    }

    @Nullable
    public DimensionManager getDimensionManager(RegistryKey<World> dimensionKey) {
        return this.dimensionManagerMap.get(dimensionKey);
    }

    public DimensionManager addDimensionManager(RegistryKey<World> dimensionKey) {
        DimensionManager dimensionManager = this.dimensionManagerMap.get(dimensionKey);
        if (dimensionManager != null) {
            ServerWaypointFabric.LOGGER.warn("Duplicate dimension key: {}", dimensionKey.getValue().toString());
            return dimensionManager;
        } else {
            DimensionManager newDimManager = new DimensionManager(dimensionKey, this.waypointsDir);
            this.dimensionManagerMap.put(dimensionKey, newDimManager);
            return newDimManager;
        }
    }

    public void broadcastWaypointModification(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint, WaypointModificationS2CPayload.ModificationType type, @Nullable PlayerEntity source) {
        WaypointModificationS2CPayload payload = new WaypointModificationS2CPayload(dimKey, listName, waypoint, type, EDITION);
        this.MINECRAFT_SERVER.getPlayerManager().getPlayerList().forEach(player -> {
            player.sendMessage(
                text((source != null ? source.getName().getString() : "Server") + " " + type.toString() + " waypoint: ")
                .append(simpleWaypointToFormattedText(waypoint, tpCmd(dimKey, waypoint.pos(), waypoint.yaw()), waypointInfoText(dimKey, waypoint))
                .append(text(" on server.").setStyle(DEFAULT_STYLE)))
                );
            ServerPlayNetworking.send(player, payload);
        }
        );
    }

    public void saveEdition() throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(this.editionFile.toFile()))) {
            out.writeInt(EDITION);
        } catch (IOException e) {
            LOGGER.error("Failed to save edition to file, sync may not work properly.", e);
            throw e;
        }
    }

    public void setMinecraftServer(MinecraftServer server) {
        if (this.MINECRAFT_SERVER != null) {
            return;
        }
        this.MINECRAFT_SERVER = server;
    }

    private String getDefaultConfig() {
        return """
                {
                  "CommandPermission": {
                    "add": 0,
                    "edit": 0,
                    "remove": 0
                  }
                }
                """;
    }
}
