use sw_performance_bots::driver::ActionLedger;

#[test]
fn effects_snapshot_keeps_client_effects_without_copying_wire_bodies() {
    use serde_json::json;
    use sw_performance_bots::driver::snapshot_report;
    let effects = json!({"alice":{"chat":[{"sequence":1,"text":"delivered"}]}});
    let wire = json!([{"name":"alice","packets":[{"bodyHex":"ab".repeat(1_000_000)}]}]);
    let full = snapshot_report(true, || wire.clone(), effects.clone());
    assert_eq!(full["scope"], "full");
    assert_eq!(full["peers"], wire);
    let summary = snapshot_report(false, || panic!("effects scope must not materialize wire history"), effects.clone());
    assert_eq!(summary["scope"], "effects");
    assert_eq!(summary["peers"], json!([]));
    assert_eq!(summary["effects"], effects);
    assert!(summary.to_string().len() < 1024);
}

#[test]
fn rejects_stale_action_ids() {
    let mut ledger = ActionLedger::new(2);
    assert!(ledger.offer("a").is_ok());
    assert!(ledger.offer("a").is_err());
    assert!(ledger.ack("missing").is_err());
    assert!(ledger.ack("a").is_ok());
    assert!(ledger.offer("a").is_err());
}

#[test]
fn barrier_waits_for_acknowledged_actions() {
    let mut ledger = ActionLedger::new(1);
    ledger.offer("a").unwrap();
    assert!(!ledger.quiescent());
    assert!(ledger.offer("b").is_err());
    ledger.ack("a").unwrap();
    assert!(ledger.quiescent());
}

#[test]
fn failed_action_is_resolved_without_allowing_id_reuse() {
    let mut ledger = ActionLedger::new(1);
    ledger.offer("a").unwrap();
    ledger.reject("a").unwrap();
    assert!(ledger.quiescent());
    assert!(ledger.offer("a").is_err());
    assert!(ledger.reject("a").is_err());
}
