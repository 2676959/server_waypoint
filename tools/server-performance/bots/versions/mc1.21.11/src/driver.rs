use std::collections::HashSet;
use serde_json::{Value, json};

pub fn snapshot_report(include_wire: bool, wire: impl FnOnce() -> Value, effects: Value) -> Value {
    let peers = if include_wire { wire() } else { json!([]) };
    json!({"scope":if include_wire {"full"} else {"effects"},"peers":peers,"effects":effects})
}

pub struct ActionLedger {
    limit: usize,
    seen: HashSet<String>,
    pending: HashSet<String>,
}

impl ActionLedger {
    pub fn new(limit: usize) -> Self {
        Self {
            limit,
            seen: HashSet::new(),
            pending: HashSet::new(),
        }
    }

    pub fn offer(&mut self, id: &str) -> Result<(), &'static str> {
        if id.is_empty() || self.seen.contains(id) || self.pending.len() >= self.limit {
            return Err("stale action ID or queue full");
        }
        self.seen.insert(id.to_owned());
        self.pending.insert(id.to_owned());
        Ok(())
    }

    pub fn ack(&mut self, id: &str) -> Result<(), &'static str> {
        if !self.pending.remove(id) {
            return Err("unoffered action ID");
        }
        Ok(())
    }

    pub fn reject(&mut self, id: &str) -> Result<(), &'static str> {
        if !self.pending.remove(id) {
            return Err("unoffered failed action ID");
        }
        Ok(())
    }

    pub fn quiescent(&self) -> bool {
        self.pending.is_empty()
    }
}
