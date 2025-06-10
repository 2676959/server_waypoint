package _959.server_waypoint.server.command;

import _959.server_waypoint.ServerWaypoint;
import _959.server_waypoint.network.payload.DimensionWaypointS2CPayload;
import _959.server_waypoint.network.payload.WorldWaypointS2CPayload;
import _959.server_waypoint.network.waypoint.DimensionWaypoint;
import _959.server_waypoint.network.waypoint.WorldWaypoint;
import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import _959.server_waypoint.server.waypoint.WaypointList;
import _959.server_waypoint.network.payload.WaypointListS2CPayload;
import _959.server_waypoint.server.WaypointServer;
import _959.server_waypoint.server.waypoint.DimensionManager;
import _959.server_waypoint.util.SimpleWaypointHelper;
import _959.server_waypoint.util.TeleportCommandGenerator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.command.argument.BlockPosArgumentType.blockPos;
import static net.minecraft.command.argument.BlockPosArgumentType.getBlockPos;
import static net.minecraft.command.argument.ColorArgumentType.color;
import static net.minecraft.command.argument.ColorArgumentType.getColor;
import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.minecraft.command.argument.DimensionArgumentType.dimension;
import static net.minecraft.command.argument.DimensionArgumentType.getDimensionArgument;

import static _959.server_waypoint.util.DimensionColorHelper.getDimensionColor;
import static _959.server_waypoint.util.TextHelper.text;
import static _959.server_waypoint.util.TextHelper.END_LINE;

public class WaypointCommand {
    public static final DynamicCommandExceptionType IO_EXCEPTION = new DynamicCommandExceptionType(file -> Text.of("IO Exception: Failed to write to %s.".formatted(file)));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("wp")
                        .then(literal("add").requires(source -> source.hasPermissionLevel(0))
                                .then(argument("dimension", dimension())
                                        .then(argument("pos", blockPos())
                                                .then(argument("name", string())
                                                        .then(argument("initial", string())
                                                                .then(argument("set", string())
                                                                        .then(argument("color", color())
                                                                                .then(argument("yaw", integer())
                                                                                        .then(argument("global", bool())
                                                                                                .executes(
                                                                                                        context -> {
                                                                                                            executeAdd(context.getSource(),
                                                                                                                    getDimensionArgument(context, "dimension").getRegistryKey(),
                                                                                                                    getBlockPos(context, "pos"),
                                                                                                                    getString(context, "name"),
                                                                                                                    getString(context, "initial"),
                                                                                                                    getString(context, "set"),
                                                                                                                    getColor(context, "color"),
                                                                                                                    getInteger(context, "yaw"),
                                                                                                                    getBool(context, "global")
                                                                                                                    );
                                                                                                            return 1;
                                                                                                        }
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(argument("pos", blockPos())
                                        .then(argument("name", string())
                                                .then(argument("initial", string())
                                                        .then(argument("set", string())
                                                                .then(argument("color", color())
                                                                        .then(argument("yaw", integer())
                                                                                .then(argument("global", bool())
                                                                                        .executes(
                                                                                                context -> {
                                                                                                    ServerCommandSource source = context.getSource();
                                                                                                executeAdd(source,
                                                                                                        source.getWorld().getRegistryKey(),
                                                                                                        getBlockPos(context, "pos"),
                                                                                                        getString(context, "name"),
                                                                                                        getString(context, "initial"),
                                                                                                        getString(context, "set"),
                                                                                                        getColor(context, "color"),
                                                                                                        getInteger(context, "yaw"),
                                                                                                        getBool(context, "global")
                                                                                                        );
                                                                                                return 1;
                                                                                                }
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(literal("list")
                                .executes(
                                        context -> {
                                            executeList(context.getSource());
                                            return 1;
                                        }
                                )
                        )
//                        .then(literal("test")
//                                .executes(
//                                        context -> {
//                                            ServerCommandSource source = context.getSource();
//                                            executeTest(source);
//                                            return 1;
//                                        }
//                                )
//                        )
                        .then(literal("download")
                                .then(argument("dimension", dimension())
                                        .executes(
                                                context -> {
                                                    executeDownload(context.getSource(),
                                                        getDimensionArgument(context, "dimension").getRegistryKey()
                                                    );
                                                    return 1;
                                                }
                                        )
                                        .then(argument("set", string())
                                                .executes(
                                                        context -> {
                                                            executeDownload(context.getSource(),
                                                                    getDimensionArgument(context, "dimension").getRegistryKey(),
                                                                    getString(context, "set")
                                                            );
                                                            return 1;
                                                        }
                                                )
                                        )
                                )
                                .executes(
                                        context -> {
                                            executeDownload(context.getSource());
                                            return 1;
                                        }
                                )
                        )
        );
    }

//    private static void executeTest(ServerCommandSource source) {
//        ServerPlayerEntity player;
//        if ((player = source.getPlayer()) != null) {
//            SimpleWaypoint testWp_1 = new SimpleWaypoint("test_1", "T1", new BlockPos(0,0,0), 0, 0, true);
//            SimpleWaypoint testWp_2 = new SimpleWaypoint("test_2", "T2", new BlockPos(0,2,0), 0, 0, true);
//            WaypointListS2CPayload testPayload = new WaypointListS2CPayload(World.OVERWORLD, WaypointList.build("test").add(testWp_1).add(testWp_2));
//            ServerPlayNetworking.send(player, testPayload);
//        }
//    }

    private static void executeAdd(ServerCommandSource source, RegistryKey<World> dimKey, BlockPos pos, String name, String initials, String listName, Formatting color, int yaw, boolean global) throws CommandSyntaxException {
        int colorIdx = color.getColorIndex();
        colorIdx = (colorIdx > 0) ? colorIdx : 15;
        ServerWaypoint.LOGGER.info("colorIdx: {}", colorIdx);
        WaypointServer waypointServer = WaypointServer.INSTANCE;
        DimensionManager dimensionManger = waypointServer.getDimensionManager(dimKey);
        if (dimensionManger == null) {
            dimensionManger = waypointServer.addDimensionManager(dimKey);
        }
        WaypointList waypointList = dimensionManger.getWaypointListByName(listName);
        SimpleWaypoint simpleWaypoint = new SimpleWaypoint(name, initials, pos, colorIdx, yaw, global);
        if (waypointList == null) {
            waypointList = WaypointList.build(listName);
            waypointList.add(simpleWaypoint);
            dimensionManger.addWaypointList(waypointList);
        } else {
            waypointList.add(simpleWaypoint);
        }
        try {
            dimensionManger.saveDimension();
            source.sendFeedback(() -> {
                MutableText feedback = text("Waypoint ");
                feedback.append(SimpleWaypointHelper.simpleWaypointToFormattedText(simpleWaypoint, TeleportCommandGenerator.tpCmd(dimKey, pos, yaw)));
                feedback.append(text(" has been added to set: %s".formatted(listName)).setStyle(SimpleWaypointHelper.DEFAULT_STYLE));
                return feedback;
            }, true);
        } catch (IOException e) {
            throw IO_EXCEPTION.create(dimensionManger.dimensionFilePath);
        }
    }

    private static void executeDownload(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            WaypointServer waypointServer = WaypointServer.INSTANCE;
            WorldWaypoint worldWaypoint = waypointServer.toWorldWaypoint();
            if (worldWaypoint != null) {
                WorldWaypointS2CPayload payload = new WorldWaypointS2CPayload(worldWaypoint);
                ServerPlayNetworking.send(player, payload);
            } else {
                source.sendError(Text.of("This server does not have any waypoints."));
            }
        }
    }

    private static void executeDownload(ServerCommandSource source, RegistryKey<World> dimKey) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            DimensionManager dimensionManager = WaypointServer.INSTANCE.getDimensionManager(dimKey);
            if (dimensionManager != null) {
                DimensionWaypoint dimWaypoint = dimensionManager.toDimensionWaypoint();
                ServerPlayNetworking.send(player, new DimensionWaypointS2CPayload(dimWaypoint));
                return;
            }
            source.sendError(Text.of("Dimension \"%s\" does not have any waypoints.".formatted(dimKey.getValue().toString())));
        }
    }

    private static void executeDownload(ServerCommandSource source, RegistryKey<World> dimKey, String name) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            DimensionManager dimensionManager = WaypointServer.INSTANCE.getDimensionManager(dimKey);
            if (dimensionManager != null) {
                WaypointList waypointList = dimensionManager.getWaypointListByName(name);
                if (waypointList != null) {
                    ServerPlayNetworking.send(player, new WaypointListS2CPayload(dimKey, waypointList));
                } else {
                    source.sendError(Text.of("No waypoint set named \"%s\".".formatted(name)));
                }
            } else {
                source.sendError(Text.of("Dimension \"%s\" does not have any waypoints!".formatted(dimKey.getValue().toString())));
            }
        }
    }

    private static void executeList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            WaypointServer waypointServer = WaypointServer.INSTANCE;
            Map<RegistryKey<World>, DimensionManager> dimensionManagerMap = waypointServer.getDimensionManagerMap();
            MutableText listMsg = text("");
            listMsg.append(END_LINE);
//            player.sendMessage(Text.of("----------------------"));
            for (RegistryKey<World> dimKey : dimensionManagerMap.keySet()) {
                // Dimension header

//                player.sendMessage(
//                    Text.literal(dimKey.getValue().toString())
//                        .formatted(getDimensionColor(dimKey))
//                );

                listMsg.append(Text.literal(dimKey.getValue().toString()).formatted(getDimensionColor(dimKey)));
                listMsg.append(END_LINE);

                DimensionManager dimensionManager = dimensionManagerMap.get(dimKey);
                if (dimensionManager == null) {
                    continue;
                }
                Map<String, WaypointList> lists = dimensionManager.getWaypointListMap();
                int listCount = lists.size();
                int currentList = 0;
                
                for (Map.Entry<String, WaypointList> listEntry : lists.entrySet()) {
                    currentList++;
                    boolean isLastList = currentList == listCount;
//                    "╸┣┗ ━━"
                    String listPrefix = isLastList ? "  ┗━━╸" : "  ┣━━╸";
                    
                    // Set header
                    MutableText listNameText = Text.literal(listPrefix).setStyle(SimpleWaypointHelper.DEFAULT_STYLE)
                        .append(Text.literal(listEntry.getKey()).setStyle(Style.EMPTY.withBold(true)));
//                    player.sendMessage(listNameText);
                    listMsg.append(listNameText);
                    listMsg.append(END_LINE);

                    // Waypoints
                    WaypointList list = listEntry.getValue();
                    for (SimpleWaypoint waypoint : list.simpleWaypoints()) {
                        String currentWaypointPrefix = (isLastList ? "        " : "  ┃     ");
                        Text waypointText = Text.literal(currentWaypointPrefix).setStyle(SimpleWaypointHelper.DEFAULT_STYLE)
                                .append(SimpleWaypointHelper.simpleWaypointToFormattedText(waypoint, TeleportCommandGenerator.tpCmd(dimKey, waypoint.pos(), waypoint.yaw())));
                        listMsg.append(waypointText);
                        listMsg.append(END_LINE);
//                        player.sendMessage(waypointText, false);
                    }
                }
//                player.sendMessage(Text.of("----------------------"));
            }
            player.sendMessage(listMsg);
        }
    }
}
