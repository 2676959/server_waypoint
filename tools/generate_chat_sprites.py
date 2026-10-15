"""Extract representative flat sprites from vanilla client jars (no textures are copied).

Usage: python3 tools/generate_chat_sprites.py oldest-client.jar [newer-client.jar ...] output.json
The first jar's item definitions choose each texture, and every jar must contain it. Tinted
textures, dynamic special models and unknown/custom items deliberately retain text-only feedback.
Static special models use their base model's representative texture, not a rendered inventory icon.
"""
import json
import sys
import zipfile


def generate(jar, shared_names):
    def resource(kind, identifier):
        namespace, path = identifier.split(":", 1) if ":" in identifier else ("minecraft", identifier)
        return f"assets/{namespace}/{kind}/{path}.json"

    def textures(model, seen=()):
        """The model's textures and elements, inheriting both from its parents."""
        if model in seen:
            return {}, []
        try:
            data = json.loads(jar.read(resource("models", model)))
        except KeyError:
            return {}, []
        result, elements = textures(data["parent"], (*seen, model)) if "parent" in data else ({}, [])
        result.update(data.get("textures", {}))
        return result, data.get("elements", elements)

    def model(node):
        kind = node.get("type", "").removeprefix("minecraft:")
        if kind == "model":
            return node
        if kind == "special" and node.get("model", {}).get("type", "").removeprefix("minecraft:") in (
                "bed", "chest", "shulker_box", "conduit", "shield", "copper_golem_statue"):
            # These renderers have a static base texture. Heads and banners have dynamic skin/
            # colour data; their template particles are unrelated to their visible appearance.
            return {"model": node["base"]}
        if kind == "condition":
            return model(node["on_false"])
        if kind == "select" and node.get("property", "").removeprefix("minecraft:") == "display_context":
            for case in node.get("cases", []):
                if "gui" in (case["when"] if isinstance(case["when"], list) else [case["when"]]):
                    return model(case["model"])
        if kind in ("select", "range_dispatch"):
            if "fallback" in node:
                return model(node["fallback"])
            entries = node.get("entries", node.get("cases", []))
            return model(entries[0]["model"]) if entries else None
        return None

    def resolve(values, reference):
        sprite, seen = reference, set()
        while sprite.startswith("#") and sprite not in seen:
            seen.add(sprite)
            sprite = values.get(sprite[1:], "")
        if not sprite or sprite.startswith("#"):
            return None
        return sprite if ":" in sprite else "minecraft:" + sprite

    def tinted(values, elements, tints):
        """Sprites the item's tints recolour: layerN by tint N, an element face by its tintindex."""
        def recoloured(index):
            if not 0 <= index < len(tints):
                return False
            tint = tints[index]
            return tint.get("type", "").removeprefix("minecraft:") != "constant" or tint.get("value") != -1

        references = ["#" + key for key in values
                      if key.startswith("layer") and key[5:].isdigit() and recoloured(int(key[5:]))]
        references += [face["texture"] for element in elements for face in element.get("faces", {}).values()
                       if recoloured(face.get("tintindex", -1))]
        return {resolve(values, reference) for reference in references}

    result = {}
    for name in sorted(jar.namelist()):
        if not name.startswith("assets/minecraft/items/") or not name.endswith(".json"):
            continue
        selected = model(json.loads(jar.read(name))["model"])
        if selected is None:
            continue
        values, elements = textures(selected["model"])
        recoloured = tinted(values, elements, selected.get("tints", []))
        for key in ("layer0", "side", "front", "all", "top", "particle"):
            sprite = resolve(values, "#" + key)
            if sprite is None or sprite in recoloured:
                continue
            namespace, path = sprite.split(":", 1)
            if path.startswith(("item/", "block/")) and f"assets/{namespace}/textures/{path}.png" in shared_names:
                item = "minecraft:" + name.removeprefix("assets/minecraft/items/").removesuffix(".json")
                result[item] = sprite
                break
    return result


if __name__ == "__main__":
    jars = [zipfile.ZipFile(path) for path in sys.argv[1:-1]]
    result = generate(jars[0], set.intersection(*(set(jar.namelist()) for jar in jars)))
    with open(sys.argv[-1], "w") as output:
        json.dump(result, output, indent=4, sort_keys=True)
        output.write("\n")
    print(f"Wrote {len(result)} sprite mappings")
