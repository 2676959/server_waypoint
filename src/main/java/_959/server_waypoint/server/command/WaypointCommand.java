package _959.server_waypoint.server.command;

import _959.server_waypoint.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.network.waypoint.DimensionWaypoint;
import _959.server_waypoint.network.waypoint.WorldWaypoint;
import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import _959.server_waypoint.server.waypoint.WaypointList;
import _959.server_waypoint.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.server.WaypointServer;
import _959.server_waypoint.server.waypoint.DimensionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
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

import static _959.server_waypoint.util.TextHelper.*;
import static _959.server_waypoint.util.DimensionColorHelper.getDimensionColor;
import static _959.server_waypoint.network.payload.s2c.WaypointModificationS2CPayload.ModificationType;
import static _959.server_waypoint.util.SimpleWaypointHelper.DEFAULT_STYLE;
import static _959.server_waypoint.util.SimpleWaypointHelper.simpleWaypointToFormattedText;
import static _959.server_waypoint.util.CommandGenerator.tpCmd;
import static _959.server_waypoint.util.CommandGenerator.editCmd;

public class WaypointCommand {
    public static final DynamicCommandExceptionType IO_EXCEPTION = new DynamicCommandExceptionType(file -> Text.of("IO Exception: Failed to write to %s.".formatted(file)));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("wp")
                        .then(literal("add").requires(source -> source.hasPermissionLevel(0))
                                .then(argument("dimension", dimension())
                                        .then(argument("pos", blockPos())
                                                .then(argument("name", string())
                                                        .then(argument("initials", string())
                                                                .then(argument("list", string())
                                                                        .then(argument("color", color())
                                                                                .then(argument("yaw", integer())
                                                                                        .then(argument("global", bool())
                                                                                                .executes(
                                                                                                        context -> {
                                                                                                            executeAdd(context.getSource(),
                                                                                                                    getDimensionArgument(context, "dimension").getRegistryKey(),
                                                                                                                    getBlockPos(context, "pos"),
                                                                                                                    getString(context, "name"),
                                                                                                                    getString(context, "initials"),
                                                                                                                    getString(context, "list"),
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
                                                .then(argument("initials", string())
                                                        .then(argument("list", string())
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
                                                                                                        getString(context, "initials"),
                                                                                                        getString(context, "list"),
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
                        .then(literal("edit")
                                .then(argument("dimension", dimension())
                                        .then(argument("list", string())
                                                .then(argument("name", string())
                                                        .then(argument("initials", string())
                                                                .then(argument("pos", blockPos())
                                                                        .then(argument("color", color())
                                                                                .then(argument("yaw", integer())
                                                                                        .then(argument("global", bool())
                                                                                                .executes(
                                                                                                        context -> {
                                                                                                            executeEdit(context.getSource(),
                                                                                                                    getDimensionArgument(context, "dimension").getRegistryKey(),
                                                                                                                    getString(context, "list"),
                                                                                                                    getString(context, "name"),
                                                                                                                    getString(context, "initials"),
                                                                                                                    getBlockPos(context, "pos"),
                                                                                                                    getColor(context, "color"),
                                                                                                                    getInteger(context, "yaw"),
                                                                                                                    getBool(context, "global"));
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

                        )
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
                                        .then(argument("list", string())
                                                .executes(
                                                        context -> {
                                                            executeDownload(context.getSource(),
                                                                    getDimensionArgument(context, "dimension").getRegistryKey(),
                                                                    getString(context, "list")
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

    private static void executeAdd(ServerCommandSource source, RegistryKey<World> dimKey, BlockPos pos, String name, String initials, String listName, Formatting color, int yaw, boolean global) throws CommandSyntaxException {
        int colorIdx = formattingToColorIndex(color);
        WaypointServer waypointServer = WaypointServer.INSTANCE;
        DimensionManager dimensionManger = waypointServer.getDimensionManager(dimKey);
        if (dimensionManger == null) {
            dimensionManger = waypointServer.addDimensionManager(dimKey);
        }
        WaypointList waypointList = dimensionManger.getWaypointListByName(listName);
        SimpleWaypoint newWaypoint = new SimpleWaypoint(name, initials, pos, colorIdx, yaw, global);
        if (waypointList == null) {
            waypointList = WaypointList.build(listName);
            waypointList.add(newWaypoint);
            dimensionManger.addWaypointList(waypointList);
        } else {
            SimpleWaypoint waypointFound = waypointList.getWaypointByName(name);
            if (waypointFound == null) {
                waypointList.add(newWaypoint);
            } else {
                source.sendFeedback(() -> {
                    MutableText feedback = text("Waypoint ");
                    feedback.append(simpleWaypointToFormattedText(waypointFound, tpCmd(dimKey, waypointFound.pos(), waypointFound.yaw())));
                    feedback.append(text(" already exists. ").setStyle(DEFAULT_STYLE));
                    feedback.append(text("[REPLACE]").setStyle(Style.EMPTY
                            .withColor(Formatting.AQUA)
                            .withClickEvent(new ClickEvent.SuggestCommand(editCmd(dimKey, listName, newWaypoint)))));
                    return feedback;
                    }
                , false);
                return;
            }
        }
        try {
            dimensionManger.saveDimension();
            source.sendFeedback(() -> {
                MutableText feedback = text("Waypoint ");
                feedback.append(simpleWaypointToFormattedText(newWaypoint, tpCmd(dimKey, pos, yaw)));
                feedback.append(text(" has been added to list: %s.".formatted(listName)).setStyle(DEFAULT_STYLE));
                return feedback;
            }, true);
        } catch (IOException e) {
            throw IO_EXCEPTION.create(dimensionManger.dimensionFilePath);
        }
        WaypointServer.EDITION++;
        WaypointServer.INSTANCE.broadcastWaypointModification(dimKey, listName, newWaypoint, ModificationType.ADD, source.getPlayer());
        try {
            WaypointServer.INSTANCE.saveEdition();
        } catch (IOException e) {
            source.sendError(text("Failed to save edition file, sync may not work properly."));
        }
    }

    // edit existing waypoint
    private static void executeEdit(ServerCommandSource source, RegistryKey<World> dimKey, String listName, String waypointName, String initials, BlockPos pos, Formatting color, int yaw, boolean global) throws CommandSyntaxException {
        WaypointServer waypointServer = WaypointServer.INSTANCE;
        DimensionManager dimensionManager = waypointServer.getDimensionManager(dimKey);
        if (dimensionManager == null) {
            source.sendError(text("Dimension \"%s\" does not exist.".formatted(dimKey.getValue().toString())));
        } else {
            WaypointList waypointList = dimensionManager.getWaypointListByName(listName);
            if (waypointList == null) {
                source.sendError(text("Waypoint list \"%s\" does not exist.".formatted(listName)));
            } else {
                SimpleWaypoint waypoint = waypointList.getWaypointByName(waypointName);
                if (waypoint == null) {
                    source.sendError(text("Waypoint \"%s\" does not exist.".formatted(waypointName)));
                } else {
                    int colorIdx = formattingToColorIndex(color);
                    if (waypoint.compareValues(initials, pos, colorIdx, yaw, global)) {
                        source.sendFeedback(() -> text("Identical properties, no changes made."), false);
                        return;
                    } else {
                        waypoint.setInitials(initials);
                        waypoint.setPos(pos);
                        waypoint.setColorIdx(colorIdx);
                        waypoint.setYaw(yaw);
                        waypoint.setGlobal(global);
                    }
                    try {
                        dimensionManager.saveDimension();
                    } catch (IOException e) {
                        throw IO_EXCEPTION.create(dimensionManager.dimensionFilePath);
                    }
                    WaypointServer.EDITION++;
                    try {
                        WaypointServer.INSTANCE.saveEdition();
                    } catch (IOException e) {
                        source.sendError(text("Failed to save edition file, sync may not work properly."));
                    }
                    WaypointServer.INSTANCE.broadcastWaypointModification(dimKey, listName, waypoint, ModificationType.UPDATE, source.getPlayer());
                    source.sendFeedback(() -> {
                        MutableText feedback = text("Waypoint ");
                        feedback.append(simpleWaypointToFormattedText(waypoint, tpCmd(dimKey, pos, yaw)));
                        feedback.append(text(" has been updated.").setStyle(DEFAULT_STYLE));
                        return feedback;
                    }, true);
                }
            }
        }
    }

    private static void executeDownload(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            WaypointServer waypointServer = WaypointServer.INSTANCE;
            WorldWaypoint worldWaypoint = waypointServer.toWorldWaypoint();
            if (worldWaypoint != null) {
                WorldWaypointS2CPayload payload = new WorldWaypointS2CPayload(worldWaypoint, WaypointServer.EDITION);
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
                    source.sendError(Text.of("No waypoint list named \"%s\".".formatted(name)));
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
                    
                    // List header
                    MutableText listNameText = Text.literal(listPrefix).setStyle(DEFAULT_STYLE)
                        .append(Text.literal(listEntry.getKey()).setStyle(Style.EMPTY.withBold(true)));
//                    player.sendMessage(listNameText);
                    listMsg.append(listNameText);
                    listMsg.append(END_LINE);

                    // Waypoints
                    WaypointList list = listEntry.getValue();
                    for (SimpleWaypoint waypoint : list.simpleWaypoints()) {
                        String currentWaypointPrefix = (isLastList ? "        " : "  ┃     ");
                        Text waypointText = Text.literal(currentWaypointPrefix).setStyle(DEFAULT_STYLE)
                                .append(simpleWaypointToFormattedText(waypoint, tpCmd(dimKey, waypoint.pos(), waypoint.yaw())));
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
