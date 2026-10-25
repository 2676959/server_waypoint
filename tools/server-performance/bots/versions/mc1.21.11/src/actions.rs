use azalea::{Client, WalkDirection, container::ContainerClientExt};
use serde_json::{Value, json};
use std::time::{Duration, Instant};

pub async fn perform(bot: &Client, data: &Value) -> anyhow::Result<Value> {
    let started = Instant::now();
    let before = bot.position();
    match data["operation"].as_str().unwrap_or("") {
        "command" => {
            let command = data["command"]
                .as_str()
                .ok_or_else(|| anyhow::anyhow!("command missing"))?;
            anyhow::ensure!(
                command.len() <= 256 && command.starts_with('/'),
                "invalid command"
            );
            bot.chat(command);
            tokio::time::sleep(Duration::from_millis(50)).await;
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
        "clock": "client-monotonic", "completionScope": "client-issued",
        "before": [before.x, before.y, before.z], "after": [after.x, after.y, after.z]}),
    )
}
