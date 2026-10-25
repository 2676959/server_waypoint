mod actions;
use azalea::buf::UnsizedByteArray;
use azalea::protocol::packets::game::{ClientboundGamePacket, ServerboundCustomPayload};
use azalea::{prelude::*, swarm::SwarmBuilder};
use serde_json::{Value, json};
use std::{
    collections::BTreeMap,
    io::Write,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};
use sw_performance_bots::driver::ActionLedger;
use sw_performance_bots::waypoint_protocol::{
    TransferReceiver, client_handshake, decode_snapshot, request_snapshot, server_handshake,
};

fn payload(bot: &Client, channel: &str, data: Vec<u8>) {
    bot.write_packet(ServerboundCustomPayload {
        identifier: channel.parse().unwrap(),
        data: UnsizedByteArray::from(data),
    });
}

#[derive(Default)]
struct WireState {
    reply: bool,
    receiver: TransferReceiver,
    packets: Vec<Value>,
}
use tokio::io::{AsyncBufReadExt, BufReader};

#[derive(Clone, Default, Component)]
struct State {
    bots: Arc<Mutex<BTreeMap<String, Client>>>,
    mode: String,
    wire: Arc<Mutex<BTreeMap<String, WireState>>>,
    failures: Arc<Mutex<Vec<String>>>,
    notifications: Option<tokio::sync::mpsc::Sender<String>>,
}

impl State {
    fn failure(&self, reason: String) {
        let mut failures = self.failures.lock().unwrap();
        if failures.len() < 32 {
            failures.push(reason.clone());
        }
        if let Some(sender) = &self.notifications {
            let _ = sender.try_send(reason);
        }
    }
}

async fn handle(bot: Client, event: Event, state: State) -> anyhow::Result<()> {
    let name = bot.profile().name;
    if let Err(error) = handle_event(bot.clone(), event, state.clone()).await {
        if let Some(peer) = state.wire.lock().unwrap().get_mut(&name) {
            peer.receiver.clear();
        }
        state.failure(format!("{name}: protocol/action handler failed: {error}"));
        bot.disconnect();
    }
    Ok(())
}

async fn handle_event(bot: Client, event: Event, state: State) -> anyhow::Result<()> {
    match event {
        Event::Spawn => {
            state
                .bots
                .lock()
                .unwrap()
                .insert(bot.profile().name, bot.clone());
            if state.mode != "vanilla" {
                payload(&bot, "minecraft:register", b"server_waypoint:server_handshake\0server_waypoint:message_chunk\0server_waypoint:upload_request".to_vec());
                payload(
                    &bot,
                    "server_waypoint:client_handshake",
                    client_handshake(if state.mode == "compatible" { 2 } else { 999 }),
                );
            }
        }
        Event::Packet(packet) => {
            if let ClientboundGamePacket::CustomPayload(packet) = &*packet {
                let channel = packet.identifier.to_string();
                let data: &[u8] = &packet.data;
                if channel == "server_waypoint:server_handshake" {
                    let (version, server_id, compression) =
                        server_handshake(data).map_err(anyhow::Error::msg)?;
                    anyhow::ensure!(version == 2, "unexpected server protocol");
                    let mut wire = state.wire.lock().unwrap();
                    let peer = wire.entry(bot.profile().name).or_default();
                    peer.reply = true;
                    peer.packets.push(json!({"channel": channel, "version": version, "serverId": server_id, "compression": compression}));
                    drop(wire);
                    if state.mode != "vanilla" {
                        payload(
                            &bot,
                            "server_waypoint:message_chunk",
                            request_snapshot(*bot.profile().uuid.as_bytes()),
                        );
                    }
                } else if channel == "server_waypoint:message_chunk" {
                    let mut wire = state.wire.lock().unwrap();
                    let retained: usize = wire
                        .values()
                        .map(|peer| peer.receiver.retained_bytes())
                        .sum();
                    anyhow::ensure!(
                        retained + data.len() <= 256 * 1024 * 1024,
                        "global receive byte limit"
                    );
                    let peer = wire.entry(bot.profile().name).or_default();
                    if let Some((kind, bytes)) =
                        peer.receiver.receive(data).map_err(anyhow::Error::msg)?
                    {
                        let snapshot = if kind == 0 {
                            decode_snapshot(&bytes).map_err(anyhow::Error::msg)?
                        } else {
                            Value::Null
                        };
                        peer.packets.push(json!({"channel": channel, "messageTypeId": kind, "waypointLists": snapshot,
                            "bodyHex": bytes.iter().map(|b| format!("{b:02x}")).collect::<String>(), "checksumVerified": true}));
                    }
                }
            }
        }
        Event::Tick => {
            if let Some(peer) = state.wire.lock().unwrap().get_mut(&bot.profile().name) {
                anyhow::ensure!(!peer.receiver.expire(), "incoming transfer expired");
            }
        }
        Event::Disconnect(reason) => {
            let name = bot.profile().name;
            state.bots.lock().unwrap().remove(&name);
            state.failure(format!("{name}: disconnected: {reason:?}"));
        }
        _ => {}
    }
    Ok(())
}

struct Output {
    run: String,
    attempt: String,
    sequence: u64,
}
impl Output {
    fn emit(&mut self, kind: &str, data: Value) {
        self.sequence += 1;
        println!(
            "{}",
            json!({"schemaVersion": 1, "runId": self.run, "attemptId": self.attempt,
            "sequence": self.sequence, "type": kind, "data": data})
        );
        std::io::stdout().flush().unwrap();
    }
}

#[tokio::main(flavor = "current_thread")]
async fn main() -> anyhow::Result<()> {
    tokio::task::LocalSet::new().run_until(run()).await
}

async fn run() -> anyhow::Result<()> {
    let (tx, mut rx) = tokio::sync::mpsc::channel::<(String, Instant)>(64);
    tokio::spawn(async move {
        let mut source = BufReader::new(tokio::io::stdin());
        let mut line = Vec::new();
        loop {
            let Ok(available) = source.fill_buf().await else {
                break;
            };
            if available.is_empty() {
                break;
            }
            let count = available
                .iter()
                .position(|byte| *byte == b'\n')
                .map(|index| index + 1)
                .unwrap_or(available.len());
            if line.len() + count > 65536 {
                break;
            }
            line.extend_from_slice(&available[..count]);
            source.consume(count);
            if line.last() == Some(&b'\n') {
                let Ok(text) = String::from_utf8(std::mem::take(&mut line)) else {
                    break;
                };
                if tx.send((text, Instant::now())).await.is_err() {
                    break;
                }
            }
        }
    });
    let first: Value = serde_json::from_str(
        &rx.recv()
            .await
            .ok_or_else(|| anyhow::anyhow!("start required"))?
            .0,
    )?;
    anyhow::ensure!(
        first["schemaVersion"] == 1 && first["type"] == "start",
        "start/schema required"
    );
    let run = first["runId"]
        .as_str()
        .ok_or_else(|| anyhow::anyhow!("runId required"))?
        .to_owned();
    let attempt = first["attemptId"]
        .as_str()
        .ok_or_else(|| anyhow::anyhow!("attemptId required"))?
        .to_owned();
    let mut sequence = first["sequence"]
        .as_u64()
        .ok_or_else(|| anyhow::anyhow!("sequence required"))?;
    let mut output = Output {
        run,
        attempt,
        sequence: 0,
    };
    let data = &first["data"];
    let count = data["count"].as_u64().unwrap_or(1);
    anyhow::ensure!((1..=50).contains(&count), "count out of bounds");
    let mode = data["mode"].as_str().unwrap_or("vanilla").to_owned();
    anyhow::ensure!(
        ["vanilla", "compatible", "incompatible"].contains(&mode.as_str()),
        "unknown wire mode"
    );
    let address = data["address"]
        .as_str()
        .ok_or_else(|| anyhow::anyhow!("address missing"))?
        .to_owned();
    let (failure_tx, mut failure_rx) = tokio::sync::mpsc::channel(32);
    let state = State {
        mode: mode.clone(),
        notifications: Some(failure_tx),
        ..State::default()
    };
    let mut builder = SwarmBuilder::new()
        .set_handler(handle)
        .reconnect_after(None)
        .join_delay(Duration::from_millis(200));
    for index in 0..count {
        builder = builder.add_account_with_state(
            Account::offline(&format!("swp_reader_{index:02}")),
            state.clone(),
        );
    }
    let swarm = tokio::task::spawn_local(builder.start(address));
    let deadline = Instant::now() + Duration::from_secs(120);
    loop {
        if state.bots.lock().unwrap().len() == count as usize
            && (mode == "vanilla"
                || state
                    .wire
                    .lock()
                    .unwrap()
                    .values()
                    .filter(|peer| peer.reply)
                    .count()
                    == count as usize)
        {
            break;
        }
        let failures = state.failures.lock().unwrap().clone();
        if !failures.is_empty() {
            output.emit("error", json!({"reason": failures.join("; ")}));
            swarm.abort();
            anyhow::bail!("native handler failed before readiness");
        }
        anyhow::ensure!(
            Instant::now() < deadline && !swarm.is_finished(),
            "swarm readiness deadline"
        );
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
    let bots: Vec<Value> = state.bots.lock().unwrap().values().map(|bot| {
        let profile = bot.profile();
        json!({"name": profile.name, "uuid": profile.uuid.to_string(), "mode": mode, "connected": true, "protocolAccepted": mode == "compatible"})
    }).collect();
    output.emit("ready", json!({"bots": bots, "autoReconnect": false}));
    let started = Instant::now();
    let mut ledger = ActionLedger::new(256);
    type Job = (String, Value, Instant);
    type Outcome = (String, String, Result<Value, String>, Instant);
    let (outcome_tx, mut outcome_rx) = tokio::sync::mpsc::channel::<Outcome>(256);
    let mut senders = BTreeMap::new();
    let mut workers = Vec::new();
    let clients: Vec<_> = state
        .bots
        .lock()
        .unwrap()
        .iter()
        .map(|(name, bot)| (name.clone(), bot.clone()))
        .collect();
    for (name, bot) in clients {
        let (sender, mut requests) = tokio::sync::mpsc::channel::<Job>(256);
        senders.insert(name.clone(), sender);
        let outcomes = outcome_tx.clone();
        workers.push(tokio::task::spawn_local(async move {
            while let Some((id, data, enqueued)) = requests.recv().await {
                let result = actions::perform(&bot, &data)
                    .await
                    .map_err(|error| error.to_string());
                if outcomes
                    .send((id, name.clone(), result, enqueued))
                    .await
                    .is_err()
                {
                    break;
                }
            }
        }));
    }
    let mut completed = 0_u64;
    let mut offered = 0_u64;
    let mut failed = 0_u64;
    let mut max_lag = 0_f64;
    let mut barrier = false;
    loop {
        if barrier && ledger.quiescent() {
            let peers: Vec<Value> = state.wire.lock().unwrap().iter().map(|(name, peer)| json!({"name": name, "packets": peer.packets, "pendingTransfers": peer.receiver.pending()})).collect();
            output.emit("packet_summary", json!({"peers": peers}));
            output.emit("rate_sample", json!({"offered": offered, "completed": completed, "failed": failed,
                "pending": offered - completed - failed, "elapsedSeconds": started.elapsed().as_secs_f64(),
                "maxLagSeconds": max_lag, "barrierAcknowledged": true, "completionScope": "client-issued"}));
            max_lag = 0.0;
            barrier = false;
        }
        let input = tokio::select! {
            biased;
            reason = failure_rx.recv() => {
                if let Some(reason) = reason { output.emit("error", json!({"reason": reason})); }
                continue;
            },
            outcome = outcome_rx.recv(), if offered > completed + failed => {
                let (id, name, result, enqueued) = outcome.ok_or_else(|| anyhow::anyhow!("action worker exited"))?;
                max_lag = max_lag.max(enqueued.elapsed().as_secs_f64());
                match result {
                    Ok(mut evidence) => {
                        ledger.ack(&id).map_err(anyhow::Error::msg)?;
                        completed += 1;
                        evidence["actionId"] = json!(id);
                        evidence["bot"] = json!(name);
                        output.emit("action_result", evidence);
                    }
                    Err(reason) => {
                        ledger.reject(&id).map_err(anyhow::Error::msg)?;
                        failed += 1;
                        output.emit("error", json!({"reason": reason, "actionId": id, "bot": name}));
                    }
                }
                continue;
            },
            input = rx.recv(), if !barrier && offered - completed - failed < 256 => input,
        };
        let Some((line, enqueued)) = input else {
            break;
        };
        let request: Value = serde_json::from_str(&line)?;
        let next = request["sequence"].as_u64().unwrap_or(0);
        anyhow::ensure!(
            request["schemaVersion"] == 1
                && request["runId"] == output.run
                && request["attemptId"] == output.attempt
                && next > sequence,
            "stale identity/sequence"
        );
        sequence = next;
        let failures = state.failures.lock().unwrap().clone();
        if !failures.is_empty() && request["type"] != "stop" {
            output.emit("error", json!({"reason": failures.join("; ")}));
            continue;
        }
        let data = &request["data"];
        match request["type"].as_str().unwrap_or("") {
            "action" => {
                let id = data["actionId"]
                    .as_str()
                    .ok_or_else(|| anyhow::anyhow!("actionId missing"))?
                    .to_owned();
                let name = data["bot"]
                    .as_str()
                    .ok_or_else(|| anyhow::anyhow!("bot missing"))?;
                let sender = senders
                    .get(name)
                    .ok_or_else(|| anyhow::anyhow!("bot not connected"))?;
                ledger.offer(&id).map_err(anyhow::Error::msg)?;
                sender
                    .try_send((id, data.clone(), enqueued))
                    .map_err(|error| {
                        anyhow::anyhow!("bounded actor queue rejected action: {error}")
                    })?;
                offered += 1;
            }
            "barrier" | "quiesce" => barrier = true,
            "stop" => {
                for worker in &workers {
                    worker.abort();
                }
                for bot in state.bots.lock().unwrap().values() {
                    bot.disconnect();
                }
                tokio::time::sleep(Duration::from_millis(250)).await;
                output.emit("stopped", json!({"completed": completed, "failed": failed, "pending": offered - completed - failed}));
                swarm.abort();
                return Ok(());
            }
            _ => anyhow::bail!("unsupported command"),
        }
    }
    for worker in &workers {
        worker.abort();
    }
    swarm.abort();
    anyhow::bail!("controller stdin closed before stop")
}
