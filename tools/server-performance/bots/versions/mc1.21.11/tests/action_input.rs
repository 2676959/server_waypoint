use azalea::protocol::packets::game::s_player_action::Action;
use serde_json::json;
use sw_performance_bots::action_input::{inventory_slots, player_action};

#[test]
fn inventory_accepts_actual_player_menu_bounds_without_silent_fallbacks() {
    assert_eq!(
        inventory_slots(&json!({"from":38,"to":5})).unwrap(),
        (38, 5)
    );
    assert_eq!(inventory_slots(&json!({"from":9,"to":0})).unwrap(), (9, 0));
    assert_eq!(
        inventory_slots(&json!({"from":44,"to":45})).unwrap(),
        (44, 45)
    );
    for value in [
        json!({"from":-1,"to":5}),
        json!({"from":46,"to":5}),
        json!({"from":5,"to":5}),
        json!({"to":5}),
    ] {
        assert!(inventory_slots(&value).is_err());
    }
}

#[test]
fn physical_player_action_requires_known_operation_and_boolean_drop_mode() {
    assert_eq!(
        player_action(&json!({"operation":"drop"})).unwrap(),
        Action::DropItem
    );
    assert_eq!(
        player_action(&json!({"operation":"drop","all":true})).unwrap(),
        Action::DropAllItems
    );
    assert_eq!(
        player_action(&json!({"operation":"swap"})).unwrap(),
        Action::SwapItemWithOffhand
    );
    assert!(player_action(&json!({"operation":"drop","all":1})).is_err());
    assert!(player_action(&json!({"operation":"other"})).is_err());
}

#[test]
fn container_position_requires_explicit_block_coordinates() {
    use sw_performance_bots::action_input::container_position;
    assert_eq!(
        container_position(&json!({"x": 300, "y": -61, "z": 297})).unwrap(),
        (300, -61, 297)
    );
    for value in [
        json!({"x": 300, "y": -61}),
        json!({"x": 30_000_001, "y": -61, "z": 0}),
        json!({"x": 0, "y": 320, "z": 0}),
        json!({"x": 0.5, "y": -61, "z": 0}),
    ] {
        assert!(container_position(&value).is_err());
    }
}

#[test]
fn inventory_drag_requires_explicit_source_slots_and_kind() {
    use sw_performance_bots::action_input::inventory_drag;
    assert_eq!(
        inventory_drag(&json!({"from":36,"slots":[9],"kind":"left"})).unwrap(),
        (36, vec![9], true)
    );
    assert_eq!(
        inventory_drag(&json!({"from":54,"slots":[0,27],"kind":"right"})).unwrap(),
        (54, vec![0, 27], false)
    );
    for value in [
        json!({"slots":[9],"kind":"left"}),
        json!({"from":36,"kind":"left"}),
        json!({"from":36,"slots":[],"kind":"left"}),
        json!({"from":36,"slots":[9,9],"kind":"left"}),
        json!({"from":36,"slots":[63],"kind":"left"}),
        json!({"from":36,"slots":[9]}),
        json!({"from":36,"slots":[9],"kind":"middle"}),
        json!({"from":36,"slots":[9],"kind":"left","x":300,"y":-61}),
    ] {
        assert!(inventory_drag(&value).is_err());
    }
}
