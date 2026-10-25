use sw_performance_bots::driver::ActionLedger;

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
