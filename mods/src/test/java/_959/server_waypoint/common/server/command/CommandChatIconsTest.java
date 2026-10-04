package _959.server_waypoint.common.server.command;

import _959.server_waypoint.common.util.TextHelper;
import _959.server_waypoint.util.NamespacedId;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommandChatIconsTest {
    @Test
    void onlySupportedBuildsEmitObjectsAndChooseTheItemsAtlasAfterTheSplit() {
        Component diamond = CommandChatIcons.INSTANCE.item(NamespacedId.parse("minecraft:diamond"));
        Component head = CommandChatIcons.INSTANCE.playerHead(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        //? if >=1.21.9 {
        assertNotNull(diamond);
        assertNotNull(head);
        var sprite = TextHelper.JSON.serializeToTree(diamond).getAsJsonObject();
        assertEquals("minecraft:item/diamond", sprite.get("sprite").getAsString());
        //? if >=1.21.11 {
        assertEquals("minecraft:items", sprite.get("atlas").getAsString());
        //?} else {
        /*assertEquals("minecraft:blocks", ((net.kyori.adventure.text.object.SpriteObjectContents)
                ((net.kyori.adventure.text.ObjectComponent) TextHelper.JSON.deserializeFromTree(sprite)).contents()).atlas().asString());
        *///?}
        assertTrue(TextHelper.JSON.serializeToTree(head).getAsJsonObject().has("player"));
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                ((net.kyori.adventure.text.object.PlayerHeadObjectContents)
                        ((net.kyori.adventure.text.ObjectComponent) head).contents()).id());
        assertEquals(diamond, TextHelper.JSON.deserializeFromTree(sprite));
        //?} else {
        /*assertNull(diamond);
        assertNull(head);
        *///?}
        assertNull(CommandChatIcons.INSTANCE.item(NamespacedId.parse("voxelmap:waypoint")));
        assertNull(CommandChatIcons.INSTANCE.item(NamespacedId.parse("example:unknown")));
    }

    @Test
    void plainTextReadersGetTheViewWithoutObjectsOrTheSpacesAfterThem() {
        Component name = Component.text("[MH] Main Home");
        //? if >=1.21.9 {
        Component sprite = java.util.Objects.requireNonNull(CommandChatIcons.INSTANCE.item(NamespacedId.parse("minecraft:diamond")));
        Component view = Component.translatable("wp.result.added", Component.empty().append(sprite.appendSpace()).append(name));
        assertEquals(Component.translatable("wp.result.added", Component.empty().children(java.util.List.of(Component.empty(), name))),
                CommandChatIcons.INSTANCE.withoutIcons(view));
        //?} else {
        /*assertSame(name, CommandChatIcons.INSTANCE.withoutIcons(name));
        *///?}
    }

    @Test
    void objectsSurviveTheMinecraftFeedbackConversion() {
        //? if >=1.21.9 {
        //? if fabric {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        //?} else {
        /*try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable unavailableRuntime) {
            // Forge-family JUnit runtimes cannot bootstrap without their loader (see ModMessageSenderTest).
            org.junit.jupiter.api.Assumptions.abort("Minecraft bootstrap unavailable: " + unavailableRuntime);
        }
        *///?}
        Component message = Component.empty()
                .append(CommandChatIcons.INSTANCE.playerHead(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")).appendSpace())
                .append(Component.text("Alex: "))
                .append(CommandChatIcons.INSTANCE.item(NamespacedId.parse("minecraft:diamond")));
        assertEquals(message, TextHelper.toAdventure(TextHelper.toMinecraft(message)));
        //?}
    }
}
