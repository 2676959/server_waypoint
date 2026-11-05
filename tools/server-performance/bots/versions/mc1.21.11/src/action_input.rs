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

pub fn container_position(data: &Value) -> Result<(i32, i32, i32), &'static str> {
    let coordinate = |key| {
        data[key]
            .as_i64()
            .ok_or("container block coordinate required")
    };
    let x = coordinate("x")?;
    let y = coordinate("y")?;
    let z = coordinate("z")?;
    if !(-30_000_000..=30_000_000).contains(&x) || !(-30_000_000..=30_000_000).contains(&z) {
        return Err("container block is outside the world");
    }
    if !(-64..=319).contains(&y) {
        return Err("container block is outside world height");
    }
    Ok((x as i32, y as i32, z as i32))
}

pub fn inventory_drag(data: &Value) -> Result<(usize, Vec<usize>, bool), &'static str> {
    let from = data["from"]
        .as_u64()
        .ok_or("inventory drag source slot required")?;
    if from > 62 {
        return Err("invalid inventory drag source slot");
    }
    let (slots, left) = inventory_drag_slots(data)?;
    if data.get("x").is_some() || data.get("y").is_some() || data.get("z").is_some() {
        container_position(data)?;
    }
    Ok((from as usize, slots, left))
}

pub fn inventory_drag_slots(data: &Value) -> Result<(Vec<usize>, bool), &'static str> {
    let values = data["slots"]
        .as_array()
        .ok_or("inventory drag destination slots required")?;
    if values.is_empty() {
        return Err("inventory drag destination slots required");
    }
    let mut slots = Vec::new();
    for value in values {
        let slot = value
            .as_u64()
            .ok_or("invalid inventory drag destination slot")?;
        if slot > 62 {
            return Err("invalid inventory drag destination slot");
        }
        let slot = slot as usize;
        if slots.contains(&slot) {
            return Err("duplicate inventory drag destination slot");
        }
        slots.push(slot);
    }
    let left = match data["kind"].as_str() {
        Some("left") => true,
        Some("right") => false,
        _ => Err("inventory drag kind must be left or right")?,
    };
    Ok((slots, left))
}

#[derive(Debug, PartialEq, Eq)]
pub struct WaypointAction {
    pub operation: &'static str,
    pub forced: bool,
    pub compression: bool,
    pub waypoints: u32,
    pub text: String,
}

pub fn waypoint_action(data: &Value) -> Result<WaypointAction, &'static str> {
    match data["operation"].as_str() {
        Some("refresh") => {
            let forced = data["forced"].as_bool().ok_or("forced refresh flag required")?;
            if !forced {
                return Err("refresh must be forced");
            }
            Ok(WaypointAction {
                operation: "refresh",
                forced,
                compression: false,
                waypoints: 0,
                text: String::new(),
            })
        }
        Some("chat") => {
            let text = data["text"].as_str().ok_or("chat text required")?;
            if text.is_empty() || text.len() > 256 || text.starts_with('/') {
                return Err("chat text out of bounds");
            }
            Ok(WaypointAction {
                operation: "chat",
                forced: false,
                compression: false,
                waypoints: 0,
                text: text.to_owned(),
            })
        }
        Some("upload") => {
            let compression = data["compression"]
                .as_bool()
                .ok_or("compression flag required")?;
            let waypoints = data["waypoints"].as_u64().ok_or("waypoint count required")?;
            if !(1..=4096).contains(&waypoints) {
                return Err("upload exceeds the per-request limit");
            }
            Ok(WaypointAction {
                operation: "upload",
                forced: false,
                compression,
                waypoints: waypoints as u32,
                text: String::new(),
            })
        }
        _ => Err("unsupported waypoint action"),
    }
}
