package _959.server_waypoint.common.server;

import _959.server_waypoint.common.network.waypoint.DimensionWaypoint;
import _959.server_waypoint.common.network.waypoint.WorldWaypoint;
import _959.server_waypoint.common.network.payload.s2c.WaypointModificationS2CPayload;
import _959.server_waypoint.common.server.waypoint.DimensionManager;
import _959.server_waypoint.common.server.waypoint.SimpleWaypoint;
import _959.server_waypoint.config.Config;
import com.google.gson.GsonBuilder;

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

//? if fabric {
 import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?} else {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}

import static _959.server_waypoint.common.ServerWaypointMod.MOD_ID;
import static _959.server_waypoint.common.ServerWaypointMod.LOGGER;
import static _959.server_waypoint.common.util.DimensionFileHelper.getDimensionKey;
import static _959.server_waypoint.common.util.SimpleWaypointHelper.DEFAULT_STYLE;
import static _959.server_waypoint.common.util.TextHelper.text;
import static _959.server_waypoint.common.util.SimpleWaypointHelper.simpleWaypointToFormattedText;
import static _959.server_waypoint.common.util.CommandGenerator.tpCmd;
import static _959.server_waypoint.common.util.TextHelper.waypointInfoText;

import com.google.gson.Gson;

public class WaypointServer {
    public static int EDITION = 0;
    public static Config CONFIG = new Config();
    public static WaypointServer INSTANCE;
    public MinecraftServer MINECRAFT_SERVER;
    private Path waypointsDir;
    private Path editionFile;
    private final Path configDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final byte[] DEFAULT_CONFIG = gson.toJson(CONFIG).getBytes();
    private LinkedHashMap<RegistryKey<World>, DimensionManager> dimensionManagerMap;

    public WaypointServer(Path configDir) {
        this.configDir = configDir.resolve(MOD_ID);
    }

    public void loadConfig(FileReader reader) {
        CONFIG = gson.fromJson(reader, Config.class);
    }

    public void initServer() throws IOException {
        this.dimensionManagerMap = new LinkedHashMap<>();
        this.dimensionManagerMap.put(World.OVERWORLD, null);
        this.dimensionManagerMap.put(World.NETHER, null);
        this.dimensionManagerMap.put(World.END, null);

        WaypointServer.INSTANCE = this;
        Path waypointsFolder = this.configDir.resolve("waypoints");
        this.editionFile = this.configDir.resolve("EDITION");
        Path configFile = this.configDir.resolve("config.json");
        
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
            if (!Files.exists(configFile) || !Files.isRegularFile(configFile)) {
                Files.createFile(configFile);
                Files.write(configFile, DEFAULT_CONFIG);
                LOGGER.info("Created config file at: {}", configFile);
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
            LOGGER.warn("Duplicate dimension key: {}", dimensionKey.getValue().toString());
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
            //? if fabric {
            ServerPlayNetworking.send(player, payload);
            //?} else {
            /*PacketDistributor.sendToPlayer(player, payload);
            *///?}
        });
    }

    public void saveEdition() throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(this.editionFile.toFile()))) {
            out.writeInt(EDITION);
            LOGGER.info("Saved edition: {}", EDITION);
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
}
