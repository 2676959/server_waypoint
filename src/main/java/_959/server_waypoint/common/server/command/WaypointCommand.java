package _959.server_waypoint.common.server.command;

import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.common.network.payload.s2c.DimensionWaypointS2CPayload;
import _959.server_waypoint.common.network.payload.s2c.WorldWaypointS2CPayload;
import _959.server_waypoint.common.permission.PermissionKey;
import _959.server_waypoint.common.network.payload.s2c.WaypointListS2CPayload;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.common.util.TextButton;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

//? if fabric {
 import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import static _959.server_waypoint.common.util.DimensionFileHelper.*;
import static _959.server_waypoint.common.util.WaypointPosHelper.fromBlockPos;
import static _959.server_waypoint.fabric.permission.FabricPermissionManager.hasPermission;
        //?} else {
/*import net.neoforged.neoforge.network.PacketDistributor;
import static _959.server_waypoint.neoforge.permission.NeoForgePermissionManager.hasPermission;
*///?}

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static _959.server_waypoint.common.server.command.suggestion.SuggestionProviders.*;
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

import static _959.server_waypoint.common.server.WaypointServerMod.CONFIG;
import static _959.server_waypoint.common.util.TextHelper.*;
import static _959.server_waypoint.common.util.TextHelper.DimensionColorHelper.getDimensionColor;
import static _959.server_waypoint.common.util.SimpleWaypointHelper.DEFAULT_STYLE;
import static _959.server_waypoint.common.util.SimpleWaypointHelper.simpleWaypointToFormattedText;
import static _959.server_waypoint.util.CommandGenerator.tpCmd;

public class WaypointCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("wp")
                        .then(literal("add")
                                .requires(source -> hasPermission(source, PermissionKey.COMMAND_ADD, CONFIG.CommandPermission().add()))
                                .then(argument("dimension", dimension())
                                        .then(argument("list", string())
                                                .suggests(WAYPOINT_LIST)
                                                .executes(
                                                        context -> {
                                                            executeAddList(context.getSource(),
                                                                    getDimensionArgument(context, "dimension").getRegistryKey(),
                                                                    getString(context, "list"));
                                                            return 1;
                                                        }
                                                )
                                                .then(argument("pos", blockPos())
                                                        .then(argument("name", string())
                                                                .suggests(WAYPOINT_NAMES)
                                                                .then(argument("initials", string())
                                                                        .suggests(NAME_INITIALS)
                                                                        .then(argument("color", color())
                                                                                .then(argument("yaw", integer())
                                                                                        .suggests(PLAYER_YAW)
                                                                                        .then(argument("global", bool())
                                                                                                .executes(
                                                                                                        context -> {
                                                                                                            executeAdd(context.getSource(),
                                                                                                                    getDimensionArgument(context, "dimension").getRegistryKey(),
                                                                                                                    getString(context, "list"),
                                                                                                                    getBlockPos(context, "pos"),
                                                                                                                    getString(context, "name"),
                                                                                                                    getString(context, "initials"),
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
                                        .then(argument("list", string())
                                                .suggests(WAYPOINT_LIST)
                                                .then(argument("name", string())
                                                        .suggests(WAYPOINT_NAMES)
                                                        .then(argument("initials", string())
                                                                .suggests(NAME_INITIALS)
                                                                .then(argument("color", color())
                                                                        .then(argument("yaw", integer())
                                                                                .suggests(PLAYER_YAW)
                                                                                .then(argument("global", bool())
                                                                                        .executes(
                                                                                                context -> {
                                                                                                    ServerCommandSource source = context.getSource();
                                                                                                    executeAdd(source,
                                                                                                            source.getWorld().getRegistryKey(),
                                                                                                            getString(context, "list"),
                                                                                                            getBlockPos(context, "pos"),
                                                                                                            getString(context, "name"),
                                                                                                            getString(context, "initials"),
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
                                .requires(source -> hasPermission(source, PermissionKey.COMMAND_EDIT, CONFIG.CommandPermission().edit()))
                                .then(argument("dimension", dimension())
                                        .then(argument("list", string())
                                                .suggests(WAYPOINT_LIST)
                                                .then(argument("name", string())
                                                        .suggests(WAYPOINT_NAMES)
                                                        .then(argument("initials", string())
                                                                .suggests(NAME_INITIALS)
                                                                .then(argument("pos", blockPos())
                                                                        .then(argument("color", color())
                                                                                .then(argument("yaw", integer())
                                                                                        .suggests(PLAYER_YAW)
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
                        .then(literal("remove")
                                .requires(source -> hasPermission(source, PermissionKey.COMMAND_REMOVE, CONFIG.CommandPermission().remove()))
                                .then(argument("dimension", dimension())
                                        .then(argument("list", string())
                                                .suggests(WAYPOINT_LIST)
                                                .executes(
                                                        context -> {
                                                            executeRemoveList(context.getSource(),
                                                                    getDimensionArgument(context, "dimension").getRegistryKey(),
                                                                    getString(context, "list"));
                                                            return 1;
                                                        }
                                                )
                                                .then(argument("name", string())
                                                        .suggests(WAYPOINT_NAMES)
                                                        .executes(
                                                                context -> {
                                                                    executeRemove(context.getSource(),
                                                                            getDimensionArgument(context, "dimension").getRegistryKey(),
                                                                            getString(context, "list"),
                                                                            getString(context, "name")
                                                                    );
                                                                    return 1;
                                                                }
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
                                                .suggests(WAYPOINT_LIST)
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

    private static void executeAddList(ServerCommandSource source, RegistryKey<World> dimKey, String listName) {
        String dimString = getDimensionString(dimKey);
        WaypointFileManager waypointFileManager = WaypointServerMod.INSTANCE.getWaypointFileManager(dimString);
        if (waypointFileManager == null) {
            source.sendError(text("Dimension: %s does not exist.".formatted(dimString)));
        } else {
            if (waypointFileManager.getWaypointListByName(listName) != null) {
                source.sendError(text("List: %s already exists.".formatted(listName)));
                return;
            }
            waypointFileManager.addWaypointList(WaypointList.build(listName));
            source.sendFeedback(() -> {
                MutableText feedback = text("Add waypoint list %s under dimension: ".formatted(listName));
                feedback.append(text(dimString).setStyle(Style.EMPTY.withColor(getDimensionColor(dimKey))));
                return feedback;
            }, true);
            saveChanges(source, waypointFileManager);
        }
    }

    private static void executeAdd(ServerCommandSource source, RegistryKey<World> dimKey, String listName, BlockPos pos, String name, String initials, Formatting color, int yaw, boolean global) {
        int colorIdx = formattingToColorIndex(color);
        String dimString = getDimensionString(dimKey);
        WaypointFileManager dimensionManger = WaypointServerMod.INSTANCE.getWaypointFileManager(dimString);
        if (dimensionManger == null) {
            dimensionManger = WaypointServerMod.INSTANCE.addWaypointFileManager(dimString);
        }
        WaypointList waypointList = dimensionManger.getWaypointListByName(listName);
        WaypointPos waypointPos = fromBlockPos(pos);
        SimpleWaypoint newWaypoint = new SimpleWaypoint(name, initials, waypointPos, colorIdx, yaw, global);
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
                    feedback.append(simpleWaypointToFormattedText(waypointFound, tpCmd(dimString, waypointFound.pos(), waypointFound.yaw()), waypointInfoText(dimString, waypointFound)));
                    feedback.append(text(" already exists. ").setStyle(DEFAULT_STYLE));
                    feedback.append(TextButton.replaceButton(dimString, listName, newWaypoint));
                    return feedback;
                    }
                , false);
                return;
            }
        }
        saveChanges(source, dimensionManger);
        WaypointServerMod.INSTANCE.broadcastWaypointModification(dimString, listName, newWaypoint, WaypointModificationType.ADD, source.getPlayer());
        source.sendFeedback(() -> {
            MutableText feedback = text("Waypoint ");
            feedback.append(simpleWaypointToFormattedText(newWaypoint, tpCmd(dimString, waypointPos, yaw), waypointInfoText(dimString, newWaypoint)));
            feedback.append(text(" has been added to list: %s.".formatted(listName)).setStyle(DEFAULT_STYLE));
            return feedback;
        }, true);
    }

    private static void saveChanges(ServerCommandSource source, WaypointFileManager dimensionManger) {
        WaypointServerMod.EDITION++;
        source.getServer().execute(
                () -> {
                    try {
                        dimensionManger.saveDimension();
                    } catch (IOException e) {
                        source.sendError(text("IO Exception: Failed to write to %s.".formatted(dimensionManger.getDimensionFile())));
                        throw new RuntimeException(e);
                    }
                    try {
                        WaypointServerMod.INSTANCE.saveEdition();
                    } catch (IOException e) {
                        source.sendError(text("Failed to save edition file, sync may not work properly."));
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    // edit existing waypoint
    private static void executeEdit(ServerCommandSource source, RegistryKey<World> dimKey, String listName, String waypointName, String initials, BlockPos pos, Formatting color, int yaw, boolean global) {
        WaypointServerMod waypointServerMod = WaypointServerMod.INSTANCE;
        String dimString = getDimensionString(dimKey);
        WaypointFileManager waypointFileManager = waypointServerMod.getWaypointFileManager(dimString);
        if (waypointFileManager == null) {
            source.sendError(text("Dimension \"%s\" does not exist.".formatted(dimString)));
        } else {
            WaypointList waypointList = waypointFileManager.getWaypointListByName(listName);
            if (waypointList == null) {
                source.sendError(text("Waypoint list \"%s\" does not exist.".formatted(listName)));
            } else {
                SimpleWaypoint waypoint = waypointList.getWaypointByName(waypointName);
                if (waypoint == null) {
                    source.sendError(text("Waypoint \"%s\" does not exist.".formatted(waypointName)));
                } else {
                    int colorIdx = formattingToColorIndex(color);
                    WaypointPos waypointPos = fromBlockPos(pos);
                    if (waypoint.compareProperties(initials, waypointPos, colorIdx, yaw, global)) {
                        source.sendFeedback(() -> text("Identical properties, no changes made."), false);
                        return;
                    } else {
                        waypoint.setInitials(initials);
                        waypoint.setPos(waypointPos);
                        waypoint.setColorIdx(colorIdx);
                        waypoint.setYaw(yaw);
                        waypoint.setGlobal(global);
                    }
                    saveChanges(source, waypointFileManager);
                    WaypointServerMod.INSTANCE.broadcastWaypointModification(dimString, listName, waypoint, WaypointModificationType.UPDATE, source.getPlayer());
                    source.sendFeedback(() -> {
                        MutableText feedback = text("Waypoint ");
                        feedback.append(simpleWaypointToFormattedText(waypoint, tpCmd(dimString, waypointPos, yaw), waypointInfoText(dimString, waypoint)));
                        feedback.append(text(" has been updated.").setStyle(DEFAULT_STYLE));
                        return feedback;
                    }, true);
                }
            }
        }
    }

    private static void executeRemove(ServerCommandSource source, RegistryKey<World> dimKey, String listName, String waypointName) {
        WaypointServerMod waypointServerMod = WaypointServerMod.INSTANCE;
        String dimString = getDimensionString(dimKey);
        WaypointFileManager waypointFileManager = waypointServerMod.getWaypointFileManager(dimString);
        if (waypointFileManager == null) {
            source.sendError(text("Dimension \"%s\" does not exist.".formatted(dimString)));
        } else {
            WaypointList waypointList = waypointFileManager.getWaypointListByName(listName);
            if (waypointList == null) {
                source.sendError(text("Waypoint list \"%s\" does not exist.".formatted(listName)));
            } else {
                List<SimpleWaypoint> removedWaypoints = waypointList.removeByName(waypointName);
                if (removedWaypoints.isEmpty()) {
                    source.sendError(text("Waypoint \"%s\" does not exist.".formatted(waypointName)));
                    return;
                }
                MutableText waypointNameList = text("");
                int lastIdx = removedWaypoints.size() - 1;
                int idx = 0;
                for (SimpleWaypoint removedWaypoint : removedWaypoints) {
                    waypointNameList.append(simpleWaypointToFormattedText(removedWaypoint, tpCmd(dimString, removedWaypoint.pos(), removedWaypoint.yaw()), waypointInfoText(dimString, removedWaypoint)));
                    waypointNameList.append(" ");
                    waypointNameList.append(TextButton.restoreButton(dimString, listName, removedWaypoint));
                    if (idx != lastIdx) {
                        waypointNameList.append(END_LINE);
                    }
                    idx++;
                }
                saveChanges(source, waypointFileManager);
                for (SimpleWaypoint removedWaypoint : removedWaypoints) {
                    WaypointServerMod.INSTANCE.broadcastWaypointModification(dimString, listName, removedWaypoint, WaypointModificationType.REMOVE, source.getPlayer());
                }
                source.sendFeedback(() -> {
                    MutableText feedback = text("Removed waypoint:").append(END_LINE);
                    feedback.append(waypointNameList);
                    return feedback;
                }, true);
            }
        }
    }

    private static void executeRemoveList(ServerCommandSource source, RegistryKey<World> dimKey, String listName) {
        String dimString = getDimensionString(dimKey);
        WaypointFileManager waypointFileManager = WaypointServerMod.INSTANCE.getWaypointFileManager(dimString);
        if (waypointFileManager == null) {
            source.sendError(text("Dimension: %s does not exist.".formatted(dimString)));
        } else {
            WaypointList waypointList = waypointFileManager.getWaypointListByName(listName);
            if (waypointList == null) {
                source.sendError(text("Waypoint list: %s does not exist.".formatted(dimString)));
            } else if (waypointList.simpleWaypoints().isEmpty()) {
                waypointFileManager.removeWaypointListByName(listName);
                source.sendFeedback(() -> {
                    MutableText feedback = text("Removed waypoint list %s under dimension: ".formatted(listName));
                    feedback.append(text(dimString).setStyle(Style.EMPTY.withColor(getDimensionColor(dimKey))));
                    return feedback;
                }, true);
            } else {
                source.sendError(text("Cannot remove non-empty waypoint list: %s".formatted(listName)));
            }
            saveChanges(source, waypointFileManager);
        }
    }

    private static void executeDownload(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            WorldWaypointS2CPayload payload = WaypointServerMod.INSTANCE.toWorldWaypointPayload();
            if (payload != null) {
                //? if fabric {
                ServerPlayNetworking.send(player, payload);
                //?} else {
                /*PacketDistributor.sendToPlayer(player, payload);
                *///?}
            } else {
                source.sendError(Text.of("This server does not have any waypoints."));
            }
        }
    }

    private static void executeDownload(ServerCommandSource source, RegistryKey<World> dimKey) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            String dimString = getDimensionString(dimKey);
            WaypointFileManager waypointFileManager = WaypointServerMod.INSTANCE.getWaypointFileManager(dimString);
            if (waypointFileManager != null) {
                DimensionWaypoint dimWaypoint = waypointFileManager.toDimensionWaypoint();
                DimensionWaypointS2CPayload payload = new DimensionWaypointS2CPayload(dimWaypoint);
                //? if fabric {
                ServerPlayNetworking.send(player, payload);
                //?} else {
                /*PacketDistributor.sendToPlayer(player, payload);
                *///?}
                return;
            }
            source.sendError(Text.of("Dimension \"%s\" does not have any waypoints.".formatted(dimString)));
        }
    }

    private static void executeDownload(ServerCommandSource source, RegistryKey<World> dimKey, String name) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            String dimString = getDimensionString(dimKey);
            WaypointFileManager waypointFileManager = WaypointServerMod.INSTANCE.getWaypointFileManager(dimString);
            if (waypointFileManager != null) {
                WaypointList waypointList = waypointFileManager.getWaypointListByName(name);
                if (waypointList != null) {
                    WaypointListS2CPayload payload = new WaypointListS2CPayload(new WaypointListBuffer(dimString, waypointList));
                    //? if fabric {
                    ServerPlayNetworking.send(player, payload);
                    //?} else {
                    /*PacketDistributor.sendToPlayer(player, payload);
                    *///?}
                } else {
                    source.sendError(Text.of("No waypoint list named \"%s\".".formatted(name)));
                }
            } else {
                source.sendError(Text.of("Dimension \"%s\" does not have any waypoints!".formatted(dimString)));
            }
        }
    }

    private static void executeList(ServerCommandSource source) {
        WaypointServerMod waypointServerMod = WaypointServerMod.INSTANCE;
        Map<String, WaypointFileManager> dimensionManagerMap = waypointServerMod.getFileManagerMap();
        MutableText listMsg = text("");
        listMsg.append(END_LINE);
        boolean empty = true;
        for (String dimString : dimensionManagerMap.keySet()) {
            // Dimension header
            WaypointFileManager waypointFileManager = dimensionManagerMap.get(dimString);
            if (waypointFileManager == null) {
                continue;
            }
            if (waypointFileManager.getWaypointListMap().isEmpty()) {
                continue;
            }
            listMsg.append(Text.literal(dimString).formatted(getDimensionColor(dimString)));
            listMsg.append(END_LINE);

            Map<String, WaypointList> lists = waypointFileManager.getWaypointListMap();
            int listCount = lists.size();
            int currentList = 0;
            for (Map.Entry<String, WaypointList> listEntry : lists.entrySet()) {
                currentList++;
                boolean isLastList = currentList == listCount;
//                    "╸┣┗ ━━"
                String listPrefix = isLastList ? "  ┗━━╸" : "  ┣━━╸";
                String listName = listEntry.getKey();
                MutableText listNameText = Text.literal(listPrefix).setStyle(DEFAULT_STYLE)
                    .append(Text.literal(listName).setStyle(Style.EMPTY.withBold(true)));
                listMsg.append(listNameText);
                listMsg.append(END_LINE);
                // Waypoints
                WaypointList list = listEntry.getValue();
                for (SimpleWaypoint waypoint : list.simpleWaypoints()) {
                    String currentWaypointPrefix = (isLastList ? "        " : "  ┃     ");
                    Text waypointText = Text.literal(currentWaypointPrefix).setStyle(DEFAULT_STYLE)
                            .append(simpleWaypointToFormattedText(waypoint, tpCmd(dimString, waypoint.pos(), waypoint.yaw()), waypointInfoText(dimString, waypoint)))
                            .append(" ")
                            .append(TextButton.editButton(dimString, listName, waypoint))
                            .append(TextButton.removeButton(dimString, listName, waypoint));
                    listMsg.append(waypointText);
                    listMsg.append(END_LINE);
                }
            }
            empty = false;
        }
        if (empty) {
            source.sendMessage(text("No waypoints to list."));
        } else {
            source.sendMessage(listMsg);
        }
    }
}
