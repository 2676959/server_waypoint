//! Test-side indexed command graph installation (upstream Azalea ignores this packet).
use azalea::{buf::{AzaleaWrite, AzaleaWriteVar}, protocol::packets::game::{ClientboundCommands, c_commands::NodeType}};
use serde_json::{Value, json};
use std::time::Instant;
use tokio::sync::oneshot;

pub fn install(packet: &ClientboundCommands) -> Result<Value, String> {
    let n = packet.entries.len();
    if n == 0 || n > 100_000 || packet.root_index as usize >= n
        || !matches!(packet.entries[packet.root_index as usize].node_type, NodeType::Root) {
        return Err("invalid command tree root/size".into());
    }
    for node in &packet.entries {
        if node.children.iter().chain(node.redirect_node.iter()).any(|i| *i as usize >= n) {
            return Err("invalid command tree edge/redirect".into());
        }
    }
    let mut colors = vec![0_u8; n];
    // Child cycles are invalid. Redirects may lead back to root and are kept as indexed edges.
    for root in 0..n {
        let mut stack = vec![(root, false)];
        while let Some((index, done)) = stack.pop() {
            if done { colors[index] = 2; continue; }
            if colors[index] == 1 { return Err("cyclic command tree children".into()); }
            if colors[index] == 2 { continue; }
            colors[index] = 1;
            stack.push((index, true));
            for child in &packet.entries[index].children { stack.push((*child as usize, false)); }
        }
    }
    let mut body = Vec::new();
    (n as u32).azalea_write_var(&mut body).map_err(|e| e.to_string())?;
    let mut nodes = Vec::new();
    for node in &packet.entries {
        let start = body.len();
        node.azalea_write(&mut body).map_err(|e| e.to_string())?;
        // Azalea 0.15.1 reads but omits FLAG_RESTRICTED in its writer.
        if node.is_restricted { body[start] |= 0x20; }
        nodes.push(json!({"name":node.name(), "children":node.children, "redirect":node.redirect_node,
            "executable":node.is_executable, "restricted":node.is_restricted}));
    }
    packet.root_index.azalea_write_var(&mut body).map_err(|e| e.to_string())?;
    let root = &packet.entries[packet.root_index as usize];
    let wp = root.children.iter().map(|i| &packet.entries[*i as usize]).find(|node| node.name() == Some("wp"));
    let branches: Vec<_> = wp.map(|node| node.children.iter().filter_map(|i| packet.entries[*i as usize].name()).collect()).unwrap_or_default();
    Ok(json!({"installed":true, "rootIndex":packet.root_index, "nodes":nodes, "branches":branches,
        "nodeCount":n, "packetBodyBytes":body.len(), "packetCrc32":crc32fast::hash(&body)}))
}

pub struct Capture {
    connection: String,
    role: String,
    connected: Instant,
    pending: Option<(Instant, oneshot::Sender<Value>)>,
    pub samples: Vec<Value>,
    // Retain the installed, validated graph for subsequent test-side lookups.
    installed: Option<ClientboundCommands>,
}

impl Capture {
    pub fn new(connection: String, role: String) -> Self {
        Self { connection, role, connected: Instant::now(), pending: None, samples: Vec::new(), installed: None }
    }
    pub fn refresh(&mut self) -> Result<oneshot::Receiver<Value>, String> {
        if self.samples.len() != 1 || self.pending.is_some() { return Err("overlapping/unmatched tree refresh".into()); }
        let (tx, rx) = oneshot::channel();
        self.pending = Some((Instant::now(), tx));
        Ok(rx)
    }
    pub fn request(&self) -> Value { json!({"connectionId":self.connection,"sequence":2}) }
    pub fn receive(&mut self, packet: &ClientboundCommands, uuid: String) -> Result<(), String> {
        let sequence = self.samples.len() + 1;
        if sequence > 2 || sequence == 2 && self.pending.is_none() { return Err("unsolicited command tree".into()); }
        let mut sample = install(packet)?;
        self.installed = Some(packet.clone());
        let started = self.pending.as_ref().map(|p| p.0).unwrap_or(self.connected);
        sample["clientElapsedNs"] = json!(started.elapsed().as_nanos() as u64);
        sample["clock"] = json!("client-monotonic");
        sample["connectionId"] = json!(self.connection);
        sample["playerUuid"] = json!(uuid);
        sample["role"] = json!(self.role);
        sample["sequence"] = json!(sequence);
        sample["trigger"] = json!(if sequence == 1 {"login"} else {"refresh"});
        sample["timingScope"] = json!(if sequence == 1 {"connect-to-installed-tree"} else {"request-to-installed-tree"});
        self.samples.push(sample.clone());
        if let Some((_, sender)) = self.pending.take() { let _ = sender.send(sample); }
        Ok(())
    }
}
