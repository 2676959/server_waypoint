//! Bounded observations of normal clientbound packets, independent of probe state.
use azalea::entity::EntityDataValue;
use azalea::protocol::packets::game::{ClientboundGamePacket as Packet, c_boss_event::Operation};
use azalea::registry::builtin::EntityKind;
use serde_json::{Value, json};
use std::collections::{BTreeMap, VecDeque};

#[derive(Default)]
pub struct Effects {
    dimension: Option<String>,
    sequence: u64,
    actionbar_updates: u64,
    actionbar: VecDeque<Value>,
    chat: VecDeque<Value>,
    bossbars: BTreeMap<String, Value>,
    displays: BTreeMap<i32, Value>,
    maps: VecDeque<MapState>,
    dropped_history: u64,
    evicted_maps: u64,
    clipped_text: u64,
}

struct MapState {
    id: u32,
    scale: u8,
    colors: Box<[u8; 16384]>,
    known: Box<[bool; 16384]>,
    decorations: Vec<Value>,
    patches: u64,
    sequence: u64,
}

fn bounded_push(queue: &mut VecDeque<Value>, value: Value, dropped: &mut u64) {
    if queue.len() == 64 {
        queue.pop_front();
        *dropped += 1;
    }
    queue.push_back(value);
}

impl Effects {
    fn text(&mut self, value: String) -> String {
        if value.chars().count() > 2048 {
            self.clipped_text += 1;
            value.chars().take(2048).collect()
        } else {
            value
        }
    }

    pub fn observe(&mut self, packet: &Packet) -> Result<(), String> {
        // Validate the entire patch before changing even the sequence counter.
        if let Packet::MapItemData(map) = packet {
            if let Some(patch) = &map.color_patch.0 {
                let width = usize::from(patch.width);
                let height = usize::from(patch.height);
                if width == 0
                    || height == 0
                    || usize::from(patch.start_x) + width > 128
                    || usize::from(patch.start_y) + height > 128
                    || patch.map_colors.len() != width * height
                {
                    return Err("invalid native map rectangle/content length".into());
                }
            }
            if map.scale > 4
                || map
                    .decorations
                    .as_ref()
                    .is_some_and(|values| values.len() > 256)
            {
                return Err("invalid native map scale/decoration limit".into());
            }
        }
        match packet {
            Packet::SetActionBarText(value) => {
                self.sequence += 1;
                self.actionbar_updates += 1;
                let text = self.text(value.text.to_string());
                bounded_push(
                    &mut self.actionbar,
                    json!({"sequence":self.sequence,"text":text}),
                    &mut self.dropped_history,
                );
            }
            Packet::SystemChat(value) => {
                self.sequence += 1;
                let text = self.text(value.content.to_string());
                let event = json!({"sequence":self.sequence,"text":text});
                if value.overlay {
                    self.actionbar_updates += 1;
                    bounded_push(&mut self.actionbar, event, &mut self.dropped_history);
                } else {
                    bounded_push(&mut self.chat, event, &mut self.dropped_history);
                }
            }
            Packet::BossEvent(value) => {
                let id = value.id.to_string();
                match &value.operation {
                    Operation::Add(add) => {
                        if self.bossbars.len() >= 16 && !self.bossbars.contains_key(&id) {
                            return Err("active bossbar observation limit".into());
                        }
                        self.sequence += 1;
                        let text = self.text(add.name.to_string());
                        self.bossbars
                            .insert(id, json!({"text":text,"sequence":self.sequence}));
                    }
                    Operation::UpdateName(name) => {
                        if self.bossbars.contains_key(&id) {
                            self.sequence += 1;
                            let text = self.text(name.to_string());
                            self.bossbars
                                .insert(id, json!({"text":text,"sequence":self.sequence}));
                        }
                    }
                    Operation::Remove => {
                        self.bossbars.remove(&id);
                    }
                    _ => {}
                }
            }
            Packet::AddEntity(value) if value.entity_type == EntityKind::TextDisplay => {
                if self.displays.len() >= 16 && !self.displays.contains_key(&value.id.0) {
                    return Err("active text display observation limit".into());
                }
                self.sequence += 1;
                self.displays
                    .insert(value.id.0, json!({"text":null,"sequence":self.sequence}));
            }
            Packet::SetEntityData(value) if self.displays.contains_key(&value.id.0) => {
                for item in &value.packed_items.0 {
                    // TextDisplay's text is a direct Component metadata value;
                    // the optional custom entity name is a different type.
                    if let EntityDataValue::FormattedText(text) = &item.value {
                        self.sequence += 1;
                        let text = self.text(text.to_string());
                        self.displays
                            .insert(value.id.0, json!({"text":text,"sequence":self.sequence}));
                    }
                }
            }
            Packet::RemoveEntities(value) => {
                for id in &value.entity_ids {
                    self.displays.remove(&id.0);
                }
            }
            Packet::Login(value) => {
                self.dimension = Some(value.common.dimension.to_string());
                self.displays.clear();
            }
            // Vanilla replaces ClientLevel only when the dimension changes.
            // Same-world death/respawn keeps other level entities, including
            // packet-only text displays. Bossbars have independent removals.
            Packet::Respawn(value) => {
                let dimension = value.common.dimension.to_string();
                if self.dimension.as_ref() != Some(&dimension) {
                    self.displays.clear();
                }
                self.dimension = Some(dimension);
            }
            Packet::MapItemData(value) => {
                self.sequence += 1;
                let mut map =
                    if let Some(index) = self.maps.iter().position(|map| map.id == value.map_id) {
                        self.maps.remove(index).unwrap()
                    } else {
                        if self.maps.len() == 8 {
                            self.maps.pop_front();
                            self.evicted_maps += 1;
                        }
                        MapState {
                            id: value.map_id,
                            scale: value.scale,
                            colors: Box::new([0; 16384]),
                            known: Box::new([false; 16384]),
                            decorations: Vec::new(),
                            patches: 0,
                            sequence: 0,
                        }
                    };
                map.scale = value.scale;
                map.sequence = self.sequence;
                if let Some(decorations) = &value.decorations {
                    map.decorations = decorations.iter().map(|decoration| {
                        let text = decoration.name.as_ref().map(|text| self.text(text.to_string()));
                        json!({"type":format!("{:?}",decoration.decoration_type),"x":decoration.x,"z":decoration.y,"caption":text})
                    }).collect();
                }
                if let Some(patch) = &value.color_patch.0 {
                    for row in 0..usize::from(patch.height) {
                        for column in 0..usize::from(patch.width) {
                            let offset = (usize::from(patch.start_y) + row) * 128
                                + usize::from(patch.start_x)
                                + column;
                            map.colors[offset] =
                                patch.map_colors[row * usize::from(patch.width) + column];
                            map.known[offset] = true;
                        }
                    }
                    map.patches += 1;
                }
                self.maps.push_back(map);
            }
            _ => {}
        }
        Ok(())
    }

    pub fn snapshot(&self) -> Value {
        let maps: Vec<Value> = self.maps.iter().map(|map| json!({
            "id":map.id,"scale":map.scale,"sequence":map.sequence,"patches":map.patches,
            "knownPixels":map.known.iter().filter(|known| **known).count(),
            "nonzeroPixels":map.colors.iter().zip(map.known.iter()).filter(|(color,known)| **known && **color != 0).count(),
            "colorsCrc32":crc32fast::hash(&map.colors[..]),"decorations":map.decorations,
        })).collect();
        json!({"dimension":self.dimension,"sequence":self.sequence,"actionbarUpdates":self.actionbar_updates,"actionbar":self.actionbar,"chat":self.chat,
            "bossbars":self.bossbars,"textDisplays":self.displays,"maps":maps,"droppedHistory":self.dropped_history,
            "evictedMaps":self.evicted_maps,"clippedText":self.clipped_text})
    }
}
