use azalea::protocol::packets::game::{
    ClientboundGamePacket as Packet, ClientboundMapItemData, ClientboundSetActionBarText,
    c_map_item_data::{DecorationType, MapDecoration, MapPatch, OptionalMapPatch},
};
use sw_performance_bots::effects::Effects;

fn map(id: u32, patch: Option<MapPatch>) -> Packet {
    Packet::MapItemData(ClientboundMapItemData {
        map_id: id,
        scale: 2,
        locked: false,
        decorations: Some(vec![MapDecoration {
            decoration_type: DecorationType::TargetX,
            x: -96,
            y: 96,
            rot: 0,
            name: Some("Target A".into()),
        }]),
        color_patch: OptionalMapPatch(patch),
    })
}

#[test]
fn actionbar_history_is_bounded_but_update_sequence_is_not_reset() {
    let mut state = Effects::default();
    for index in 0..100 {
        state
            .observe(&Packet::SetActionBarText(ClientboundSetActionBarText {
                text: format!("Target {index}").into(),
            }))
            .unwrap();
    }
    let value = state.snapshot();
    assert_eq!(value["actionbarUpdates"], 100);
    assert_eq!(value["actionbar"].as_array().unwrap().len(), 64);
    assert_eq!(value["actionbar"][0]["sequence"], 37);
    assert_eq!(value["actionbar"][63]["text"], "Target 99");
    assert_eq!(value["droppedHistory"], 36);
}

#[test]
fn map_patch_coordinates_and_content_are_observed_independently_of_session() {
    let mut state = Effects::default();
    state
        .observe(&map(
            3,
            Some(MapPatch {
                width: 2,
                height: 2,
                start_x: 126,
                start_y: 126,
                map_colors: vec![4, 8, 0, 12],
            }),
        ))
        .unwrap();
    let value = state.snapshot();
    let observed = &value["maps"][0];
    assert_eq!(observed["id"], 3);
    assert_eq!(observed["knownPixels"], 4);
    assert_eq!(observed["nonzeroPixels"], 3);
    assert_eq!(observed["decorations"][0]["x"], -96);
    assert_eq!(observed["decorations"][0]["z"], 96);
    assert_eq!(observed["decorations"][0]["caption"], "Target A");
    let hash = observed["colorsCrc32"].clone();
    state.observe(&map(3, None)).unwrap();
    assert_eq!(state.snapshot()["maps"][0]["colorsCrc32"], hash);
}

#[test]
fn malformed_map_patch_does_not_partially_mutate_observed_state() {
    let mut state = Effects::default();
    state.observe(&map(3, None)).unwrap();
    let before = state.snapshot();
    for patch in [
        MapPatch {
            width: 2,
            height: 2,
            start_x: 127,
            start_y: 0,
            map_colors: vec![1; 4],
        },
        MapPatch {
            width: 2,
            height: 2,
            start_x: 0,
            start_y: 0,
            map_colors: vec![1; 3],
        },
        MapPatch {
            width: 0,
            height: 1,
            start_x: 0,
            start_y: 0,
            map_colors: vec![],
        },
    ] {
        assert!(state.observe(&map(3, Some(patch))).is_err());
        assert_eq!(state.snapshot(), before);
    }
}

#[test]
fn map_retention_eviction_is_explicit() {
    let mut state = Effects::default();
    for id in 0..10 {
        state.observe(&map(id, None)).unwrap();
    }
    let value = state.snapshot();
    assert_eq!(value["maps"].as_array().unwrap().len(), 8);
    assert_eq!(value["evictedMaps"], 2);
    assert_eq!(value["maps"][0]["id"], 2);
}

#[test]
fn bossbar_and_network_only_text_display_updates_and_removals_are_observed() {
    use azalea::entity::{EntityDataItem, EntityDataValue, EntityMetadataItems};
    use azalea::protocol::packets::game::{
        ClientboundAddEntity, ClientboundBossEvent, ClientboundRemoveEntities,
        ClientboundSetEntityData,
        c_boss_event::{AddOperation, BossBarColor, BossBarOverlay, Operation, Properties, Style},
    };
    use azalea::registry::builtin::EntityKind;
    use azalea::world::MinecraftEntityId;
    let id = "00000000-0000-0000-0000-000000000001".parse().unwrap();
    let mut state = Effects::default();
    state
        .observe(&Packet::BossEvent(ClientboundBossEvent {
            id,
            operation: Operation::Add(AddOperation {
                name: "Target A".into(),
                progress: 1.0,
                style: Style {
                    color: BossBarColor::White,
                    overlay: BossBarOverlay::Progress,
                },
                properties: Properties {
                    darken_screen: false,
                    play_music: false,
                    create_world_fog: false,
                },
            }),
        }))
        .unwrap();
    state
        .observe(&Packet::BossEvent(ClientboundBossEvent {
            id,
            operation: Operation::UpdateName("Target B".into()),
        }))
        .unwrap();
    state
        .observe(&Packet::AddEntity(ClientboundAddEntity {
            id: MinecraftEntityId(42),
            uuid: id,
            entity_type: EntityKind::TextDisplay,
            position: Default::default(),
            movement: Default::default(),
            x_rot: 0,
            y_rot: 0,
            y_head_rot: 0,
            data: 0,
        }))
        .unwrap();
    state
        .observe(&Packet::SetEntityData(ClientboundSetEntityData {
            id: MinecraftEntityId(42),
            packed_items: EntityMetadataItems(vec![
                EntityDataItem {
                    index: 2,
                    value: EntityDataValue::OptionalFormattedText(Some(Box::new(
                        "Unrelated custom name".into(),
                    ))),
                },
                EntityDataItem {
                    index: 23,
                    value: EntityDataValue::FormattedText(Box::new("Target B 32m".into())),
                },
            ]),
        }))
        .unwrap();
    let value = state.snapshot();
    assert_eq!(value["bossbars"][id.to_string()]["text"], "Target B");
    assert_eq!(value["textDisplays"]["42"]["text"], "Target B 32m");
    state
        .observe(&Packet::BossEvent(ClientboundBossEvent {
            id,
            operation: Operation::Remove,
        }))
        .unwrap();
    state
        .observe(&Packet::RemoveEntities(ClientboundRemoveEntities {
            entity_ids: vec![MinecraftEntityId(42)],
        }))
        .unwrap();
    let value = state.snapshot();
    assert!(value["bossbars"].as_object().unwrap().is_empty());
    assert!(value["textDisplays"].as_object().unwrap().is_empty());
}

#[test]
fn same_dimension_respawn_preserves_level_entities_but_world_change_discards_them() {
    use azalea::core::game_type::{GameMode, OptionalGameType};
    use azalea::protocol::packets::{
        common::CommonPlayerSpawnInfo,
        game::{ClientboundAddEntity, ClientboundLogin, ClientboundRespawn},
    };
    use azalea::registry::{DataRegistry, builtin::EntityKind, data::DimensionKind};
    use azalea::world::MinecraftEntityId;
    let spawn = |dimension: &str| CommonPlayerSpawnInfo {
        dimension_type: DimensionKind::new_raw(0),
        dimension: dimension.parse().unwrap(),
        seed: 0,
        game_type: GameMode::Survival,
        previous_game_type: OptionalGameType(None),
        is_debug: false,
        is_flat: true,
        last_death_location: None,
        portal_cooldown: 0,
        sea_level: 63,
    };
    let mut state = Effects::default();
    state
        .observe(&Packet::Login(ClientboundLogin {
            player_id: MinecraftEntityId(1),
            hardcore: false,
            levels: vec![],
            max_players: 20,
            chunk_radius: 2,
            simulation_distance: 2,
            reduced_debug_info: false,
            show_death_screen: true,
            do_limited_crafting: false,
            common: spawn("minecraft:overworld"),
            enforces_secure_chat: false,
        }))
        .unwrap();
    state
        .observe(&Packet::AddEntity(ClientboundAddEntity {
            id: MinecraftEntityId(42),
            uuid: "00000000-0000-0000-0000-000000000001".parse().unwrap(),
            entity_type: EntityKind::TextDisplay,
            position: Default::default(),
            movement: Default::default(),
            x_rot: 0,
            y_rot: 0,
            y_head_rot: 0,
            data: 0,
        }))
        .unwrap();
    state
        .observe(&Packet::Respawn(ClientboundRespawn {
            common: spawn("minecraft:overworld"),
            data_to_keep: 0,
        }))
        .unwrap();
    assert!(
        state.snapshot()["textDisplays"].get("42").is_some(),
        "vanilla keeps this ClientLevel on same-world respawn"
    );
    state
        .observe(&Packet::Respawn(ClientboundRespawn {
            common: spawn("minecraft:the_nether"),
            data_to_keep: 3,
        }))
        .unwrap();
    assert!(
        state.snapshot()["textDisplays"]
            .as_object()
            .unwrap()
            .is_empty(),
        "vanilla replaces ClientLevel on world change"
    );
}
