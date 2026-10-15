"""Regression coverage for representative chat sprites from item model definitions."""
import io
import json
import unittest
import zipfile

from generate_chat_sprites import generate


class ChatSpritesTest(unittest.TestCase):
    def generate(self, items, models, textures, missing=()):
        archive = io.BytesIO()
        with zipfile.ZipFile(archive, "w") as jar:
            for kind, definitions in (("items", items), ("models", models)):
                for name, definition in definitions.items():
                    jar.writestr(f"assets/minecraft/{kind}/{name}.json", json.dumps(definition))
            for texture in textures:
                jar.writestr(f"assets/minecraft/textures/{texture}.png", b"")
        archive.seek(0)
        with zipfile.ZipFile(archive) as jar:
            shared = set(jar.namelist()) - {f"assets/minecraft/textures/{name}.png" for name in missing}
            return generate(jar, shared)

    def test_beds_use_their_inherited_colour_texture(self):
        result = self.generate(
            {"red_bed": {"model": {"type": "minecraft:special", "base": "minecraft:item/red_bed",
                                   "model": {"type": "minecraft:bed", "texture": "minecraft:red"}}}},
            {"item/red_bed": {"parent": "minecraft:item/template_bed", "textures": {"colour": "block/red_wool"}},
             "item/template_bed": {"textures": {"particle": "#colour"}}},
            ["block/red_wool"])
        self.assertEqual({"minecraft:red_bed": "minecraft:block/red_wool"}, result)

    def test_chests_nested_in_selects_use_the_default_base_texture(self):
        result = self.generate(
            {"chest": {"model": {"type": "minecraft:select", "property": "minecraft:local_time",
                                 "cases": [], "fallback": {"type": "minecraft:special", "base": "minecraft:item/chest",
                                                           "model": {"type": "minecraft:chest", "texture": "minecraft:normal"}}}}},
            {"item/chest": {"textures": {"particle": "block/oak_planks"}}},
            ["block/oak_planks"])
        self.assertEqual({"minecraft:chest": "minecraft:block/oak_planks"}, result)

    def test_a_texture_missing_from_a_newer_client_is_excluded(self):
        result = self.generate(
            {"red_bed": {"model": {"type": "minecraft:special", "base": "minecraft:item/red_bed",
                                   "model": {"type": "minecraft:bed", "texture": "minecraft:red"}}}},
            {"item/red_bed": {"textures": {"particle": "block/red_wool"}}},
            ["block/red_wool"], missing=["block/red_wool"])
        self.assertEqual({}, result)

    def test_dynamic_and_unknown_special_models_do_not_use_unrelated_particles(self):
        for renderer in ("banner", "head", "unknown"):
            with self.subTest(renderer=renderer):
                result = self.generate(
                    {"dynamic": {"model": {"type": "minecraft:special", "base": "minecraft:item/template",
                                            "model": {"type": "minecraft:" + renderer}}}},
                    {"item/template": {"textures": {"particle": "block/oak_planks"}}},
                    ["block/oak_planks"])
                self.assertEqual({}, result)


if __name__ == "__main__":
    unittest.main()
