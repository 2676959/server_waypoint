mod actions;
use azalea::{prelude::*, swarm::SwarmBuilder};
use serde_json::{Value, json};
use std::{collections::BTreeMap, io::Write, sync::{Arc, Mutex}, time::{Duration, Instant}};
use sw_performance_bots::driver::ActionLedger;
use tokio::io::{AsyncBufReadExt, BufReader};

#[derive(Clone, Default, Component)]
struct State {
    bots: Arc<Mutex<BTreeMap<String, Client>>>,
    failures: Arc<Mutex<Vec<String>>>,
}

async fn handle(bot: Client, event: Event, state: State) -> anyhow::Result<()> {
    match event {
        Event::Spawn => { state.bots.lock().unwrap().insert(bot.profile().name, bot); }
        Event::Disconnect(reason) => {
            let name = bot.profile().name;
            state.bots.lock().unwrap().remove(&name);
            state.failures.lock().unwrap().push(format!("{name}: disconnected: {reason:?}"));
        }
        _ => {}
    }
    Ok(())
}

struct Output { run: String, attempt: String, sequence: u64 }
impl Output {
    fn emit(&mut self, kind: &str, data: Value) {
        self.sequence += 1;
        println!("{}", json!({"schemaVersion": 1, "runId": self.run, "attemptId": self.attempt,
            "sequence": self.sequence, "type": kind, "data": data}));
        std::io::stdout().flush().unwrap();
    }
}

#[tokio::main(flavor = "current_thread")]
async fn main() -> anyhow::Result<()> {
    tokio::task::LocalSet::new().run_until(run()).await
}

async fn run() -> anyhow::Result<()> {
    let (tx, mut rx) = tokio::sync::mpsc::channel::<String>(64);
    tokio::spawn(async move {
        let mut lines = BufReader::new(tokio::io::stdin()).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            if line.len() > 65536 || tx.send(line).await.is_err() { break; }
        }
    });
    let first: Value = serde_json::from_str(&rx.recv().await.ok_or_else(|| anyhow::anyhow!("start required"))?)?;
    anyhow::ensure!(first["schemaVersion"] == 1 && first["type"] == "start", "start/schema required");
    let run = first["runId"].as_str().ok_or_else(|| anyhow::anyhow!("runId required"))?.to_owned();
    let attempt = first["attemptId"].as_str().ok_or_else(|| anyhow::anyhow!("attemptId required"))?.to_owned();
    let mut sequence = first["sequence"].as_u64().ok_or_else(|| anyhow::anyhow!("sequence required"))?;
    let mut output = Output { run, attempt, sequence: 0 };
    let data = &first["data"];
    let count = data["count"].as_u64().unwrap_or(1);
    anyhow::ensure!((1..=50).contains(&count), "count out of bounds");
    anyhow::ensure!(data["mode"] == "vanilla", "wire mode not implemented");
    let address = data["address"].as_str().ok_or_else(|| anyhow::anyhow!("address missing"))?.to_owned();
    let state = State::default();
    let mut builder = SwarmBuilder::new().set_handler(handle).reconnect_after(None).join_delay(Duration::from_millis(200));
    for index in 0..count {
        builder = builder.add_account_with_state(Account::offline(&format!("swp_reader_{index:02}")), state.clone());
    }
    let swarm = tokio::task::spawn_local(builder.start(address));
    let deadline = Instant::now() + Duration::from_secs(120);
    loop {
        if state.bots.lock().unwrap().len() == count as usize { break; }
        anyhow::ensure!(Instant::now() < deadline && !swarm.is_finished(), "swarm readiness deadline");
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
    let bots: Vec<Value> = state.bots.lock().unwrap().values().map(|bot| {
        let profile = bot.profile();
        json!({"name": profile.name, "uuid": profile.uuid.to_string(), "mode": "vanilla", "connected": true})
    }).collect();
    output.emit("ready", json!({"bots": bots, "autoReconnect": false}));
    let started = Instant::now();
    let mut ledger = ActionLedger::new(256);
    let mut completed = 0_u64;
    let mut offered = 0_u64;
    let mut max_lag = 0_f64;
    while let Some(line) = rx.recv().await {
        let request: Value = serde_json::from_str(&line)?;
        let next = request["sequence"].as_u64().unwrap_or(0);
        anyhow::ensure!(request["schemaVersion"] == 1 && request["runId"] == output.run && request["attemptId"] == output.attempt && next > sequence, "stale identity/sequence");
        sequence = next;
        for failure in state.failures.lock().unwrap().drain(..) {
            output.emit("error", json!({"reason": failure}));
        }
        let data = &request["data"];
        match request["type"].as_str().unwrap_or("") {
            "action" => {
                let id = data["actionId"].as_str().ok_or_else(|| anyhow::anyhow!("actionId missing"))?;
                ledger.offer(id).map_err(anyhow::Error::msg)?;
                offered += 1;
                let name = data["bot"].as_str().ok_or_else(|| anyhow::anyhow!("bot missing"))?;
                let bot = state.bots.lock().unwrap().get(name).cloned().ok_or_else(|| anyhow::anyhow!("bot not connected"))?;
                let action_start = Instant::now();
                match actions::perform(&bot, data).await {
                    Ok(mut evidence) => {
                        ledger.ack(id).map_err(anyhow::Error::msg)?;
                        completed += 1;
                        evidence["actionId"] = json!(id);
                        evidence["bot"] = json!(name);
                        output.emit("action_result", evidence);
                    }
                    Err(error) => output.emit("error", json!({"reason": error.to_string(), "actionId": id})),
                }
                max_lag = max_lag.max(action_start.elapsed().as_secs_f64());
            }
            "barrier" | "quiesce" => {
                output.emit("rate_sample", json!({"offered": offered, "completed": completed,
                    "pending": offered - completed, "elapsedSeconds": started.elapsed().as_secs_f64(),
                    "maxLagSeconds": max_lag, "barrierAcknowledged": ledger.quiescent(), "completionScope": "client-issued"}));
            }
            "stop" => {
                for bot in state.bots.lock().unwrap().values() { bot.disconnect(); }
                tokio::time::sleep(Duration::from_millis(250)).await;
                output.emit("stopped", json!({"completed": completed, "pending": offered - completed}));
                swarm.abort();
                return Ok(());
            }
            _ => anyhow::bail!("unsupported command"),
        }
    }
    swarm.abort();
    anyhow::bail!("controller stdin closed before stop")
}
