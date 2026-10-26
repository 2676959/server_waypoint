use azalea::protocol::packets::game::s_player_action::Action;
use serde_json::Value;

pub fn inventory_slots(data: &Value) -> Result<(usize, usize), &'static str> {
    let from = data["from"]
        .as_u64()
        .ok_or("inventory source slot required")?;
    let to = data["to"]
        .as_u64()
        .ok_or("inventory destination slot required")?;
    if from > 45 || to > 45 || from == to {
        return Err("invalid player menu slots");
    }
    Ok((from as usize, to as usize))
}

pub fn player_action(data: &Value) -> Result<Action, &'static str> {
    match data["operation"].as_str() {
        Some("drop") => {
            let all = match data.get("all") {
                Some(value) => value.as_bool().ok_or("drop mode must be boolean")?,
                None => false,
            };
            Ok(if all {
                Action::DropAllItems
            } else {
                Action::DropItem
            })
        }
        Some("swap") => Ok(Action::SwapItemWithOffhand),
        _ => Err("unsupported physical player action"),
    }
}
