use serde_json::json;
use sw_performance_bots::command_ack::PendingAcks;

#[test]
fn acknowledgement_requires_exact_actor_action_and_command() {
    let mut pending = PendingAcks::default();
    let mut reply = pending.register("alice", "action-1", "/wp help").unwrap();
    let value = json!({"actionId":"action-1", "command":"wp help", "serverObserved":true, "returned":1, "stage":"command-completed"});
    assert!(pending.receive("bob", &value).is_err());
    assert!(reply.try_recv().is_err());
    let mut wrong = value.clone();
    wrong["command"] = json!("wp reload");
    assert!(pending.receive("alice", &wrong).is_err());
    assert!(reply.try_recv().is_err());
    pending.receive("alice", &value).unwrap();
    assert_eq!(reply.try_recv().unwrap()["returned"], 1);
    assert!(pending.receive("alice", &value).is_err());
    assert_eq!(pending.len(), 0);
}

#[test]
fn unobserved_ack_and_duplicate_pending_action_are_rejected() {
    let mut pending = PendingAcks::default();
    let mut reply = pending.register("alice", "action-1", "/wp help").unwrap();
    assert!(pending.register("alice", "action-1", "/wp help").is_err());
    assert!(
        pending
            .receive(
                "alice",
                &json!({"actionId":"action-1", "command":"wp help", "serverObserved":false})
            )
            .is_err()
    );
    assert!(reply.try_recv().is_err());
    pending.cancel("alice", "action-1");
    assert_eq!(pending.len(), 0);
    assert!(reply.try_recv().is_err());
}

#[test]
fn marker_registration_is_separate_from_native_callback_completion() {
    let mut markers = PendingAcks::markers();
    let mut commands = PendingAcks::default();
    let mut marker = markers.register("alice", "id", "/wp help").unwrap();
    let mut command = commands.register("alice", "id", "/wp help").unwrap();
    let ready = json!({"actionId":"id","command":"wp help","serverObserved":true,"stage":"marker-registered"});
    assert!(commands.receive("alice", &ready).is_err());
    markers.receive("alice", &ready).unwrap();
    assert!(marker.try_recv().is_ok());
    assert!(command.try_recv().is_err());
    commands.receive("alice", &json!({"actionId":"id","command":"wp help","serverObserved":true,"stage":"command-completed"})).unwrap();
    assert!(command.try_recv().is_ok());
}
