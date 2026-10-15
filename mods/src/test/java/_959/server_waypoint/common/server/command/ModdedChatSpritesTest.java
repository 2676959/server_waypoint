package _959.server_waypoint.common.server.command;

import _959.server_waypoint.util.NamespacedId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModdedChatSpritesTest {
    private final Map<String, String> assets = new HashMap<>();
    private final ModdedChatSprites sprites = new ModdedChatSprites(path -> {
        String data = assets.get(path);
        return data == null ? null : new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    });

    @Test
    void followsBlockModelParentsAndChildTextureOverrides() {
        asset("items/marble.json", """
                {"model":{"type":"minecraft:model","model":"example:item/marble"}}
                """);
        asset("models/item/marble.json", """
                {"parent":"example:block/marble","textures":{"material":"example:block/polished/marble"}}
                """);
        asset("models/block/marble.json", """
                {"textures":{"all":"#material","material":"example:block/raw_marble"}}
                """);
        asset("textures/block/polished/marble.png", "texture");
        assertEquals("example:block/polished/marble", sprite("marble"));
    }

    @Test
    void readsMaterialObjectsUsedByNewerItemModels() {
        asset("items/tool.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"example:item/tool\"}}");
        asset("models/item/tool.json", """
                {"textures":{"layer0":{"sprite":"example:item/materials/tool","force_translucent":true}}}
                """);
        asset("textures/item/materials/tool.png", "texture");
        assertEquals("example:item/materials/tool", sprite("tool"));
    }

    @Test
    void selectsGuiModelInsteadOfTheDisplayContextFallback() {
        asset("items/tool.json", """
                {"model":{"type":"minecraft:select","property":"minecraft:display_context",
                "cases":[{"when":["gui","fixed"],"model":{"type":"minecraft:model","model":"example:item/gui"}}],
                "fallback":{"type":"minecraft:model","model":"example:item/held"}}}
                """);
        asset("models/item/gui.json", "{\"textures\":{\"layer0\":\"example:item/gui\"}}");
        asset("textures/item/gui.png", "texture");
        assertEquals("example:item/gui", sprite("tool"));
    }

    @Test
    void recognizesUnqualifiedVanillaSelectorIds() {
        asset("items/tool.json", """
                {"model":{"type":"select","property":"display_context",
                "cases":[{"when":"gui","model":{"type":"model","model":"example:item/gui"}}],
                "fallback":{"type":"empty"}}}
                """);
        asset("models/item/gui.json", "{\"textures\":{\"layer0\":\"example:item/gui\"}}");
        asset("textures/item/gui.png", "texture");
        assertEquals("example:item/gui", sprite("tool"));
    }

    @Test
    void followsConditionalAndRangeDefaults() {
        asset("items/tool.json", """
                {"model":{"type":"minecraft:condition","on_false":{
                "type":"minecraft:range_dispatch","fallback":{"type":"minecraft:model","model":"example:item/tool"}}}}
                """);
        asset("models/item/tool.json", "{\"textures\":{\"layer0\":\"example:item/tool_idle\"}}");
        asset("textures/item/tool_idle.png", "texture");
        assertEquals("example:item/tool_idle", sprite("tool"));
    }

    @Test
    void triesAnotherCompositeLayerWhenTheFirstHasNoTexture() {
        asset("items/tool.json", """
                {"model":{"type":"minecraft:composite","models":[
                {"type":"minecraft:model","model":"example:item/missing"},
                {"type":"minecraft:model","model":"example:item/tool"}]}}
                """);
        asset("models/item/tool.json", "{\"textures\":{\"layer0\":\"example:item/tool\"}}");
        asset("textures/item/tool.png", "texture");
        assertEquals("example:item/tool", sprite("tool"));
    }

    @Test
    void verifiesConventionalItemAndBlockTexturePathsWhenDefinitionsAreAbsent() {
        asset("textures/item/tool.png", "texture");
        asset("textures/block/marble.png", "texture");
        assertEquals("example:item/tool", sprite("tool"));
        assertEquals("example:block/marble", sprite("marble"));
        assertNull(sprite("missing"));
    }

    @Test
    void followsConventionalModelWhenTheItemDefinitionIsAbsent() {
        asset("models/item/tool.json", "{\"textures\":{\"layer0\":\"example:item/different_name\"}}");
        asset("textures/item/different_name.png", "texture");
        assertEquals("example:item/different_name", sprite("tool"));
    }

    @Test
    void doesNotEmitMissingTexturesOrTexturesOutsideTheChatAtlases() {
        asset("items/missing.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"example:item/missing\"}}");
        asset("models/item/missing.json", "{\"textures\":{\"layer0\":\"example:item/absent\"}}");
        asset("items/custom.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"example:item/custom\"}}");
        asset("models/item/custom.json", "{\"textures\":{\"particle\":\"example:entity/custom\"}}");
        asset("textures/entity/custom.png", "texture");
        assertNull(sprite("missing"));
        assertNull(sprite("custom"));
    }

    @Test
    void malformedAssetsAndCustomRenderersLeaveOutTheIcon() {
        asset("items/broken.json", "not json");
        asset("items/invalid.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":[]}}");
        asset("items/custom.json", "{\"model\":{\"type\":\"example:renderer\"}}");
        assertNull(sprite("broken"));
        assertNull(sprite("invalid"));
        assertNull(sprite("custom"));
    }

    @Test
    void cyclicTextureAliasesTerminateWithoutAnIcon() {
        asset("models/item/loop.json", "{\"textures\":{\"layer0\":\"#other\",\"other\":\"#layer0\"}}");
        assertNull(sprite("loop"));
    }

    @Test
    void cyclicParentsDoNotEmitAnOtherwiseAvailableTexture() {
        asset("models/item/loop.json", "{\"parent\":\"example:item/loop\",\"textures\":{\"layer0\":\"example:item/loop\"}}");
        asset("textures/item/loop.png", "texture");
        assertNull(sprite("loop"));
    }

    @Test
    void doesNotGuessSpritesForExcludedVanillaItems() {
        assets.put("assets/minecraft/textures/item/potion.png", "texture");
        assertNull(sprites.sprite(NamespacedId.parse("minecraft:potion")));
    }

    private void asset(String path, String data) {
        assets.put("assets/example/" + path, data);
    }

    private String sprite(String path) {
        return sprites.sprite(NamespacedId.parse("example:" + path));
    }
}
