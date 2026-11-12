use azalea::{
    Client, WalkDirection,
    bot::BotClientExt,
    container::{ContainerClientExt, ContainerHandle},
    core::position::BlockPos,
    inventory::operations::{ClickOperation, QuickCraftClick, QuickCraftKind, QuickCraftStatus},
};
use serde_json::{Value, json};
use std::time::{Duration, Instant};

async fn open_other_container(bot: &Client, position: BlockPos) -> anyhow::Result<ContainerHandle> {
    // Azalea can wait forever after a rapid open/close misses the server menu.
    match tokio::time::timeout(Duration::from_secs(2), bot.open_container_at(position)).await {
        Ok(Some(handle)) => Ok(handle),
        Ok(None) | Err(_) => anyhow::bail!("other container did not open"),
    }
}

async fn wait_game_ticks(bot: &Client, count: usize) {
    let mut ticks = bot.get_tick_broadcaster();
    for _ in 0..count {
        let _ = ticks.recv().await;
    }
}

pub async fn perform(
    bot: &Client,
    data: &Value,
    acknowledgements: &std::sync::Arc<
        std::sync::Mutex<sw_performance_bots::command_ack::PendingAcks>,
    >,
    markers: &std::sync::Arc<std::sync::Mutex<sw_performance_bots::command_ack::PendingAcks>>,
    open_container: &mut Option<ContainerHandle>,
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
                let reply = tokio::time::timeout(Duration::from_secs(90), receiver).await;
                acknowledgements
                    .lock()
                    .unwrap()
                    .cancel(&bot.profile().name, id);
                match reply {
                    Ok(Ok(value)) => native_ack = value,
                    Ok(Err(error)) => anyhow::bail!(error.to_string()),
                    Err(_) => {
                        let after = bot.position();
                        return Ok(json!({
                            "error": "native command acknowledgement deadline",
                            "success": false,
                            "clientElapsedNs": started.elapsed().as_nanos() as u64,
                            "markerRegistrationNs": marker_elapsed,
                            "clock": "client-monotonic",
                            "completionScope": "client-issued",
                            "nativeAck": Value::Null,
                            "before": [before.x, before.y, before.z],
                            "after": [after.x, after.y, after.z]
                        }));
                    }
                }
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
        "entity-interact" => {
            let id = sw_performance_bots::action_input::entity_interact(data)
                .map_err(anyhow::Error::msg)?;
            let entity = bot
                .ecs_entity_by_minecraft_entity(azalea::world::MinecraftEntityId(id))
                .ok_or_else(|| anyhow::anyhow!("entity is not loaded"))?;
            bot.entity_interact(entity);
            wait_game_ticks(bot, 5).await;
        }
        "select" => {
            let slot = data["slot"]
                .as_u64()
                .ok_or_else(|| anyhow::anyhow!("hotbar slot missing"))?;
            anyhow::ensure!(slot <= 8, "invalid hotbar slot");
            bot.set_selected_hotbar_slot(slot as u8);
            tokio::time::sleep(Duration::from_millis(100)).await;
        }
        "inventory" => {
            let (from, to) = sw_performance_bots::action_input::inventory_slots(data)
                .map_err(anyhow::Error::msg)?;
            let inventory = bot
                .open_inventory()
                .ok_or_else(|| anyhow::anyhow!("another container open"))?;
            inventory.left_click(from);
            tokio::time::sleep(Duration::from_millis(150)).await;
            inventory.left_click(to);
            tokio::time::sleep(Duration::from_millis(150)).await;
            inventory.close();
        }
        "drop" | "swap" => {
            let action = sw_performance_bots::action_input::player_action(data)
                .map_err(anyhow::Error::msg)?;
            bot.write_packet(azalea::protocol::packets::game::ServerboundPlayerAction {
                action,
                pos: azalea::core::position::BlockPos::new(0, 0, 0),
                direction: azalea::core::direction::Direction::Down,
                seq: 0,
            });
            tokio::time::sleep(Duration::from_millis(150)).await;
        }
        "open-container" => {
            let (x, y, z) = sw_performance_bots::action_input::container_position(data)
                .map_err(anyhow::Error::msg)?;
            anyhow::ensure!(open_container.is_none(), "another container already open");
            let handle = open_other_container(bot, BlockPos::new(x, y, z)).await?;
            if data["hold"] == true {
                *open_container = Some(handle);
            } else {
                handle.close();
            }
            tokio::time::sleep(Duration::from_millis(150)).await;
        }
        "close-container" => {
            let handle = open_container
                .take()
                .ok_or_else(|| anyhow::anyhow!("no held other container"))?;
            handle.close();
            tokio::time::sleep(Duration::from_millis(150)).await;
        }
        "pickup" => {
            let from = data["from"]
                .as_u64()
                .ok_or_else(|| anyhow::anyhow!("inventory pickup source slot required"))?;
            anyhow::ensure!(from <= 62, "invalid inventory pickup source slot");
            if open_container.is_none() {
                if data.get("x").is_some() {
                    let (x, y, z) = sw_performance_bots::action_input::container_position(data)
                        .map_err(anyhow::Error::msg)?;
                    *open_container = Some(open_other_container(bot, BlockPos::new(x, y, z)).await?);
                } else {
                    *open_container = Some(
                        bot.open_inventory()
                            .ok_or_else(|| anyhow::anyhow!("another container open"))?,
                    );
                }
            }
            open_container
                .as_ref()
                .ok_or_else(|| anyhow::anyhow!("no open inventory"))?
                .left_click(from as usize);
            wait_game_ticks(bot, 2).await;
        }
        "drag" => {
            let (slots, left) = if data.get("from").is_some() {
                let (from, slots, left) = sw_performance_bots::action_input::inventory_drag(data)
                    .map_err(anyhow::Error::msg)?;
                if open_container.is_none() {
                    if data.get("x").is_some() {
                        let (x, y, z) = sw_performance_bots::action_input::container_position(data)
                            .map_err(anyhow::Error::msg)?;
                        *open_container = Some(open_other_container(bot, BlockPos::new(x, y, z)).await?);
                    } else {
                        *open_container = Some(
                            bot.open_inventory()
                                .ok_or_else(|| anyhow::anyhow!("another container open"))?,
                        );
                    }
                }
                open_container
                    .as_ref()
                    .ok_or_else(|| anyhow::anyhow!("no open inventory"))?
                    .left_click(from);
                tokio::time::sleep(Duration::from_millis(150)).await;
                (slots, left)
            } else {
                if open_container.is_none() {
                    if data.get("x").is_some() {
                        let (x, y, z) = sw_performance_bots::action_input::container_position(data)
                            .map_err(anyhow::Error::msg)?;
                        *open_container = Some(open_other_container(bot, BlockPos::new(x, y, z)).await?);
                    } else {
                        *open_container = Some(
                            bot.open_inventory()
                                .ok_or_else(|| anyhow::anyhow!("another container open"))?,
                        );
                    }
                }
                sw_performance_bots::action_input::inventory_drag_slots(data)
                    .map_err(anyhow::Error::msg)?
            };
            let handle = open_container
                .as_ref()
                .ok_or_else(|| anyhow::anyhow!("no open inventory"))?;
            let kind = if left {
                QuickCraftKind::Left
            } else {
                QuickCraftKind::Right
            };
            handle.click(ClickOperation::QuickCraft(QuickCraftClick {
                kind: kind.clone(),
                status: QuickCraftStatus::Start,
            }));
            wait_game_ticks(bot, 2).await;
            for slot in slots {
                handle.click(ClickOperation::QuickCraft(QuickCraftClick {
                    kind: kind.clone(),
                    status: QuickCraftStatus::Add { slot: slot as u16 },
                }));
                wait_game_ticks(bot, 2).await;
            }
            handle.click(ClickOperation::QuickCraft(QuickCraftClick {
                kind,
                status: QuickCraftStatus::End,
            }));
            wait_game_ticks(bot, 2).await;
            if data["hold"] != true {
                open_container
                    .take()
                    .ok_or_else(|| anyhow::anyhow!("no open inventory"))?
                    .close();
                tokio::time::sleep(Duration::from_millis(150)).await;
            }
        }
        "menu-click" => {
            let (slot, button) = sw_performance_bots::action_input::menu_click(data)
                .map_err(anyhow::Error::msg)?;
            let handle = open_container
                .as_ref()
                .ok_or_else(|| anyhow::anyhow!("no open inventory"))?;
            match button {
                sw_performance_bots::action_input::MenuButton::Left => handle.left_click(slot),
                sw_performance_bots::action_input::MenuButton::Right => handle.right_click(slot),
                sw_performance_bots::action_input::MenuButton::Shift => handle.shift_click(slot),
            }
            wait_game_ticks(bot, 2).await;
        }
        "disconnect" => bot.disconnect(),
        "refresh" => {
            use azalea::protocol::packets::game::ServerboundCustomPayload;
            use sw_performance_bots::waypoint_protocol::request_snapshot;
            let action = sw_performance_bots::action_input::waypoint_action(data)
                .map_err(anyhow::Error::msg)?;
            anyhow::ensure!(action.operation == "refresh" && action.forced, "forced refresh required");
            let frame = request_snapshot(*bot.profile().uuid.as_bytes());
            bot.write_packet(ServerboundCustomPayload {
                identifier: "server_waypoint:message_chunk".parse().unwrap(),
                data: azalea::buf::UnsizedByteArray::from(frame),
            });
            tokio::time::sleep(Duration::from_millis(150)).await;
        }
        "chat" => {
            let action = sw_performance_bots::action_input::waypoint_action(data)
                .map_err(anyhow::Error::msg)?;
            anyhow::ensure!(action.operation == "chat", "chat action required");
            bot.chat(&action.text);
            tokio::time::sleep(Duration::from_millis(150)).await;
        }
        "upload" => {
            let action = sw_performance_bots::action_input::waypoint_action(data)
                .map_err(anyhow::Error::msg)?;
            anyhow::ensure!(
                action.waypoints >= 1 && action.waypoints <= 4096,
                "upload request out of bounds"
            );
            anyhow::bail!(
                "published waypoint-data upload body is not in the vector set (compression={})",
                action.compression
            );
        }
        _ => anyhow::bail!("unsupported action"),
    }
    let after = bot.position();
    Ok(
        json!({"clientElapsedNs": started.elapsed().as_nanos() as u64,
        "markerRegistrationNs": marker_elapsed, "clock": "client-monotonic", "completionScope": if native_ack.is_null() {"client-issued"} else {"client-roundtrip"}, "nativeAck":native_ack,
        "before": [before.x, before.y, before.z], "after": [after.x, after.y, after.z]}),
    )
}
