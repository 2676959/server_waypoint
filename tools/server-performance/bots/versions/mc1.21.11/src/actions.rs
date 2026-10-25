use azalea::{Client, WalkDirection, container::ContainerClientExt};
use serde_json::{Value, json};
use std::time::{Duration, Instant};

pub async fn perform(
    bot: &Client,
    data: &Value,
    acknowledgements: &std::sync::Arc<
        std::sync::Mutex<sw_performance_bots::command_ack::PendingAcks>,
    >,
    markers: &std::sync::Arc<std::sync::Mutex<sw_performance_bots::command_ack::PendingAcks>>,
) -> anyhow::Result<Value> {
    let mut started = Instant::now();
    let mut marker_elapsed = 0_u64;
    let before = bot.position();
    let mut native_ack = Value::Null;
    match data["operation"].as_str().unwrap_or("") {
        "command" => {
            let command = data["command"]
                .as_str()
                .ok_or_else(|| anyhow::anyhow!("command missing"))?;
            anyhow::ensure!(
                command.len() <= 256
                    && command.starts_with('/')
                    && command.chars().all(|c| !c.is_control() && c != '§'),
                "invalid command"
            );
            if data["acknowledged"] == true {
                let id = data["actionId"]
                    .as_str()
                    .ok_or_else(|| anyhow::anyhow!("acknowledged action ID required"))?;
                let receiver = acknowledgements
                    .lock()
                    .unwrap()
                    .register(&bot.profile().name, id, command)
                    .map_err(anyhow::Error::msg)?;
                let marker_receiver = markers
                    .lock()
                    .unwrap()
                    .register(&bot.profile().name, id, command)
                    .map_err(anyhow::Error::msg)?;
                let marker = serde_json::to_vec(
                    &json!({"actionId":id,"command":command.trim_start_matches('/')}),
                )?;
                bot.write_packet(azalea::protocol::packets::game::ServerboundCustomPayload {
                    identifier: "swperf:command_request".parse().unwrap(),
                    data: azalea::buf::UnsizedByteArray::from(marker),
                });
                let ready = tokio::time::timeout(Duration::from_secs(10), marker_receiver).await;
                markers.lock().unwrap().cancel(&bot.profile().name, id);
                if !matches!(ready, Ok(Ok(_))) {
                    acknowledgements
                        .lock()
                        .unwrap()
                        .cancel(&bot.profile().name, id);
                    anyhow::bail!("native command marker registration deadline");
                }
                marker_elapsed = started.elapsed().as_nanos() as u64;
                started = Instant::now();
                // Queue both packets through the same Azalea command queue.
                // Client::chat uses a different ECS message phase and can overtake
                // the preceding marker when the swarm is busy.
                bot.write_packet(azalea::protocol::packets::game::ServerboundChatCommand {
                    command: command[1..].to_owned(),
                });
                let reply = tokio::time::timeout(Duration::from_secs(10), receiver).await;
                acknowledgements
                    .lock()
                    .unwrap()
                    .cancel(&bot.profile().name, id);
                native_ack = reply
                    .map_err(|_| anyhow::anyhow!("native command acknowledgement deadline"))??;
            } else {
                bot.chat(command);
                tokio::time::sleep(Duration::from_millis(50)).await;
            }
        }
        "edit" => {
            use azalea::protocol::packets::game::ServerboundCustomPayload;
            use sw_performance_bots::waypoint_protocol::{encode_edit, frame_message};
            let body = encode_edit(&data["edit"]).map_err(anyhow::Error::msg)?;
            let mut transfer = *bot.profile().uuid.as_bytes();
            let request = data["edit"]["requestId"]
                .as_i64()
                .ok_or_else(|| anyhow::anyhow!("edit request ID missing"))?
                .to_be_bytes();
            for (target, value) in transfer[8..].iter_mut().zip(request) {
                *target ^= value;
            }
            for frame in frame_message(2, &body, transfer, true).map_err(anyhow::Error::msg)? {
                bot.write_packet(ServerboundCustomPayload {
                    identifier: "server_waypoint:message_chunk".parse().unwrap(),
                    data: azalea::buf::UnsizedByteArray::from(frame),
                });
            }
            tokio::time::sleep(Duration::from_millis(150)).await;
        }
        "walk" => {
            let millis = data["milliseconds"].as_u64().unwrap_or(500);
            anyhow::ensure!((50..=5000).contains(&millis), "walk duration out of bounds");
            bot.walk(WalkDirection::Forward);
            tokio::time::sleep(Duration::from_millis(millis)).await;
            bot.walk(WalkDirection::None);
        }
        "inventory" => {
            let from = data["from"].as_u64().unwrap_or(36);
            let to = data["to"].as_u64().unwrap_or(37);
            anyhow::ensure!(
                (9..=44).contains(&from) && (9..=44).contains(&to) && from != to,
                "invalid inventory slots"
            );
            let inventory = bot
                .open_inventory()
                .ok_or_else(|| anyhow::anyhow!("another container open"))?;
            inventory.left_click(from as usize);
            tokio::time::sleep(Duration::from_millis(150)).await;
            inventory.left_click(to as usize);
            tokio::time::sleep(Duration::from_millis(150)).await;
            inventory.close();
        }
        "disconnect" => bot.disconnect(),
        _ => anyhow::bail!("unsupported action"),
    }
    let after = bot.position();
    Ok(
        json!({"clientElapsedNs": started.elapsed().as_nanos() as u64,
        "markerRegistrationNs": marker_elapsed, "clock": "client-monotonic", "completionScope": if native_ack.is_null() {"client-issued"} else {"client-roundtrip"}, "nativeAck":native_ack,
        "before": [before.x, before.y, before.z], "after": [after.x, after.y, after.z]}),
    )
}
