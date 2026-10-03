package _959.server_waypoint.common.util;

import _959.server_waypoint.common.MinecraftTestRuntime;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextHelperTest {
    private record Piece(String text, TextColor color, String insertion) {
    }

    @BeforeAll
    static void bootstrapMinecraft() {
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        } catch (Throwable bootstrapFailure) {
            // Forge-family runtimes cannot bootstrap Minecraft from a plain JUnit run; see ModMessageSenderTest.
            Assumptions.abort("Minecraft could not be bootstrapped in this test runtime: " + bootstrapFailure);
        }
    }

    @Test
    void aPlayersDisplayNameKeepsItsColourHoverClickAndInsertionOnTheWayThroughMinecraftText() {
        MinecraftTestRuntime.assumeEntityTypesAreRegistered();
        UUID id = UUID.fromString("c3b0f8d2-5a1e-4c7e-9f3a-2b1d4e6f8a90");
        Component displayName = Component.text("Alex", NamedTextColor.GOLD, TextDecoration.BOLD)
                .clickEvent(ClickEvent.suggestCommand("/tell Alex "))
                .hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"), id, Component.text("Alex")))
                .insertion("Alex");

        assertEquals(displayName, TextHelper.toAdventure(TextHelper.toMinecraft(displayName)));
    }

    @Test
    void aTeamFormattedNameKeepsItsPrefixSuffixAndTeamColour() {
        net.minecraft.network.chat.Component teamName = net.minecraft.network.chat.Component.empty()
                .append(net.minecraft.network.chat.Component.literal("[Admin] "))
                .append(net.minecraft.network.chat.Component.literal("Alex"))
                .append(net.minecraft.network.chat.Component.literal(" (he)"))
                .withStyle(ChatFormatting.RED)
                .withStyle(style -> style.withInsertion("Alex"));

        List<Piece> pieces = new ArrayList<>();
        collect(TextHelper.toAdventure(teamName), null, null, pieces);

        assertEquals(List.of(
                new Piece("[Admin] ", NamedTextColor.RED, "Alex"),
                new Piece("Alex", NamedTextColor.RED, "Alex"),
                new Piece(" (he)", NamedTextColor.RED, "Alex")), pieces);
    }

    /** The text of every non-empty piece with the colour and insertion it ends up with after inheritance. */
    private static void collect(Component component, TextColor inheritedColor, String inheritedInsertion,
                                List<Piece> pieces) {
        TextColor color = component.color() != null ? component.color() : inheritedColor;
        String insertion = component.insertion() != null ? component.insertion() : inheritedInsertion;
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            pieces.add(new Piece(text.content(), color, insertion));
        }
        for (Component child : component.children()) {
            collect(child, color, insertion, pieces);
        }
    }
}
