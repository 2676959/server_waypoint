package _959.server_waypoint.common.util;

import _959.server_waypoint.text.FormattedTextHelper;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.network.chat.Component;
//? if >= 1.20.3
import net.minecraft.network.chat.ComponentSerialization;

import static _959.server_waypoint.util.VanillaDimensionNames.*;

import net.minecraft.ChatFormatting;

import java.util.Objects;

public class TextHelper {
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
                .decode(JsonOps.INSTANCE, GsonComponentSerializer.gson().serializeToTree(adventureText))
                .result()
                .map(result -> result.getFirst())
                .orElseThrow(() -> new IllegalArgumentException("Could not convert Adventure text to Minecraft text"));
        //?} else {
        /*Component converted = Component.Serializer.fromJson(
                GsonComponentSerializer.gson().serializeToTree(adventureText)
        );
        if (converted == null) {
            throw new IllegalArgumentException("Could not convert Adventure text to Minecraft text");
        }
        return converted;
        *///?}
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
