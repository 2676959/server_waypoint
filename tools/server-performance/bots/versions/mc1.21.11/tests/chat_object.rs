//! System chat must survive the player-head object used by teleport confirmations.
use std::io::Cursor;

use azalea_buf::AzaleaRead;
use azalea_chat::FormattedText;
use serde::Deserialize;
use simdnbt::owned::{BaseNbt, NbtCompound, NbtList, NbtTag};

fn player_head(profile: NbtCompound) -> NbtCompound {
    let mut head = NbtCompound::new();
    head.insert("object", "player");
    head.insert("player", profile);
    head
}

fn uuid_profile() -> NbtCompound {
    let mut profile = NbtCompound::new();
    profile.insert("id", NbtTag::IntArray(vec![1, 2, 3, 4]));
    profile
}

fn text_component(text: &str) -> NbtCompound {
    let mut component = NbtCompound::new();
    component.insert("text", text);
    component
}

fn confirmation(include_head: bool) -> NbtCompound {
    let mut message = text_component("✔ Teleported ");
    let mut siblings = Vec::new();
    if include_head {
        let mut head = player_head(uuid_profile());
        head.insert("extra", NbtTag::List(NbtList::from(vec![text_component(" ")])));
        siblings.push(head);
    }
    siblings.push(text_component("Alex to [BA] Base"));
    message.insert("extra", NbtTag::List(NbtList::from(siblings)));
    message
}

fn read_component(compound: NbtCompound) -> Result<FormattedText, azalea_buf::BufReadError> {
    let mut bytes = Vec::new();
    BaseNbt::from(compound).write_unnamed(&mut bytes);
    FormattedText::azalea_read(&mut Cursor::new(bytes.as_slice()))
}

#[test]
fn plain_confirmation_decodes() {
    let text = read_component(confirmation(false)).unwrap();
    assert_eq!(text.to_string(), "✔ Teleported Alex to [BA] Base");
}

#[test]
fn player_head_confirmation_keeps_surrounding_text() {
    let text = read_component(confirmation(true)).unwrap();
    assert_eq!(
        text.to_string(),
        "✔ Teleported [unknown player head] Alex to [BA] Base"
    );
}

#[test]
fn named_player_head_uses_vanilla_description() {
    let mut profile = NbtCompound::new();
    profile.insert("name", "Alex");
    let mut message = text_component("");
    message.insert(
        "extra",
        NbtTag::List(NbtList::from(vec![player_head(profile)])),
    );
    let text = read_component(message).unwrap();
    assert_eq!(text.to_string(), "[Alex head]");
}

#[test]
fn atlas_sprite_uses_vanilla_description() {
    let mut sprite = NbtCompound::new();
    sprite.insert("object", "atlas");
    sprite.insert("atlas", "minecraft:items");
    sprite.insert("sprite", "minecraft:item/diamond");
    let mut message = text_component("icon ");
    message.insert("extra", NbtTag::List(NbtList::from(vec![sprite])));
    let text = read_component(message).unwrap();
    assert_eq!(text.to_string(), "icon [item/diamond@items]");
}

#[test]
fn wire_confirmation_omits_type_tags_and_text_on_the_root() {
    let mut profile = NbtCompound::new();
    profile.insert("id", NbtTag::IntArray(vec![1, 2, 3, 4]));
    let mut head = NbtCompound::new();
    head.insert("color", "white");
    head.insert("player", profile);
    head.insert("extra", vec![" ".to_owned()]);
    let mut teleported = NbtCompound::new();
    teleported.insert("", "Teleported ");
    let mut name = NbtCompound::new();
    name.insert("", "Alex");
    let mut body = NbtCompound::new();
    body.insert(
        "extra",
        NbtTag::List(NbtList::from(vec![teleported, head, name])),
    );
    let mut check = NbtCompound::new();
    check.insert("", "✔ ");
    let mut message = NbtCompound::new();
    message.insert("color", "green");
    message.insert("extra", NbtTag::List(NbtList::from(vec![check, body])));

    let text = read_component(message).unwrap();
    assert_eq!(text.to_string(), "✔ Teleported [unknown player head] Alex");
}

#[test]
fn json_player_head_keeps_surrounding_text() {
    let component = FormattedText::deserialize(&serde_json::json!({
        "text": "✔ Teleported ",
        "extra": [
            {"object": "player", "player": {"id": [1, 2, 3, 4]}, "extra": [{"text": " "}]},
            {"text": "Alex to [BA] Base"}
        ]
    }))
    .unwrap();
    assert_eq!(
        component.to_string(),
        "✔ Teleported [unknown player head] Alex to [BA] Base"
    );
}
