package _959.server_waypoint.common.util;

import _959.server_waypoint.text.FormattedTextHelper;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
//? if < 1.20.3
/*import net.kyori.adventure.text.serializer.json.JSONOptions;*/
import net.minecraft.network.chat.Component;
//? if >= 1.20.3
import net.minecraft.network.chat.ComponentSerialization;

import static _959.server_waypoint.util.VanillaDimensionNames.*;

import net.minecraft.ChatFormatting;

import java.util.Objects;

public class TextHelper {
    /**
     * Adventure's JSON as this Minecraft version reads it. Before 1.20.3 a hover entity's id has to be a
     * string, where the default writes the int array that later versions accept as well.
     */
    //? if >= 1.20.3 {
    public static final GsonComponentSerializer JSON = GsonComponentSerializer.gson();
    //?} else {
    /*public static final GsonComponentSerializer JSON = GsonComponentSerializer.builder()
            .editOptions(options -> options.value(JSONOptions.EMIT_HOVER_SHOW_ENTITY_ID_AS_INT_ARRAY, false))
            .build();
    *///?}

    public static Component parseFormattedText(String rawText) {
        net.kyori.adventure.text.Component adventureText = FormattedTextHelper.parse(rawText);
        try {
            return toMinecraft(adventureText);
        } catch (IllegalArgumentException ignored) {
            return Component.literal(rawText);
        }
    }

    public static Component toMinecraft(net.kyori.adventure.text.Component adventureText) {
        Objects.requireNonNull(adventureText, "adventureText");
        //? if >= 1.20.3 {
        return ComponentSerialization.CODEC
                .decode(JsonOps.INSTANCE, JSON.serializeToTree(adventureText))
                .result()
                .map(result -> result.getFirst())
                .orElseThrow(() -> new IllegalArgumentException("Could not convert Adventure text to Minecraft text"));
        //?} else {
        /*Component converted = Component.Serializer.fromJson(
                JSON.serializeToTree(adventureText)
        );
        if (converted == null) {
            throw new IllegalArgumentException("Could not convert Adventure text to Minecraft text");
        }
        return converted;
        *///?}
    }

    /**
     * Minecraft text as Adventure text, keeping its colours, decorations, hover, click and insertion.
     * It goes through the JSON both sides read, the way {@link #toMinecraft} goes the other way.
     */
    public static net.kyori.adventure.text.Component toAdventure(Component minecraftText) {
        Objects.requireNonNull(minecraftText, "minecraftText");
        //? if >= 1.20.3 {
        JsonElement json = ComponentSerialization.CODEC
                .encodeStart(JsonOps.INSTANCE, minecraftText)
                .result()
                .orElseThrow(() -> new IllegalArgumentException("Could not convert Minecraft text to Adventure text"));
        //?} else {
        /*JsonElement json = Component.Serializer.toJsonTree(minecraftText);
        *///?}
        return JSON.deserializeFromTree(json);
    }

    public static ChatFormatting getDimensionColor(String dimString) {
        return switch (dimString) {
            case MINECRAFT_OVERWORLD -> ChatFormatting.GREEN;
            case MINECRAFT_THE_NETHER -> ChatFormatting.RED;
            case MINECRAFT_THE_END -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.YELLOW;
        };
    }
}
