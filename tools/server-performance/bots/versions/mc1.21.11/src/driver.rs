use std::collections::HashSet;

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

    pub fn quiescent(&self) -> bool {
        self.pending.is_empty()
    }
}
