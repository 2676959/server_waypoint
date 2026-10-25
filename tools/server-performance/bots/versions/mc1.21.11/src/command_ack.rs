//! Test-probe acknowledgements; a marker alone never completes a command.
use serde_json::Value;
use std::collections::BTreeMap;
use tokio::sync::oneshot;

pub struct PendingAcks {
    stage: &'static str,
    entries: BTreeMap<(String, String), (String, oneshot::Sender<Value>)>,
}
impl Default for PendingAcks {
    fn default() -> Self {
        Self {
            stage: "command-completed",
            entries: BTreeMap::new(),
        }
    }
}
impl PendingAcks {
    pub fn markers() -> Self {
        Self {
            stage: "marker-registered",
            entries: BTreeMap::new(),
        }
    }
    pub fn register(
        &mut self,
        bot: &str,
        id: &str,
        command: &str,
    ) -> Result<oneshot::Receiver<Value>, String> {
        if id.is_empty() || id.len() > 128 || self.entries.len() >= 256 {
            return Err("invalid/bounded command acknowledgement request".into());
        }
        let key = (bot.to_owned(), id.to_owned());
        if self.entries.contains_key(&key) {
            return Err("duplicate pending command acknowledgement".into());
        }
        let (sender, receiver) = oneshot::channel();
        self.entries
            .insert(key, (command.trim_start_matches('/').to_owned(), sender));
        Ok(receiver)
    }
    pub fn receive(&mut self, bot: &str, value: &Value) -> Result<(), String> {
        let id = value["actionId"]
            .as_str()
            .ok_or("acknowledgement ID missing")?;
        let key = (bot.to_owned(), id.to_owned());
        let (command, _) = self
            .entries
            .get(&key)
            .ok_or("unknown actor/action acknowledgement")?;
        if value["serverObserved"] != true
            || value["command"] != *command
            || value["stage"] != self.stage
        {
            return Err("acknowledgement lacks matching native command callback".into());
        }
        let (_, sender) = self.entries.remove(&key).unwrap();
        sender
            .send(value.clone())
            .map_err(|_| "command acknowledgement receiver expired".into())
    }
    pub fn cancel(&mut self, bot: &str, id: &str) {
        self.entries.remove(&(bot.to_owned(), id.to_owned()));
    }
    pub fn len(&self) -> usize {
        self.entries.len()
    }
    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }
}
