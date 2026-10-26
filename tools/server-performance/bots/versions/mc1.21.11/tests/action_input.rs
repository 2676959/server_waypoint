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
