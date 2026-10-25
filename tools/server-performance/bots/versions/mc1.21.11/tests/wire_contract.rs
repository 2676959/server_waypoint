use serde_json::Value;
use sw_performance_bots::waypoint_protocol::{
    client_handshake, decode_chunk, reassemble, server_handshake,
};

fn unhex(text: &str) -> Vec<u8> {
    (0..text.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&text[i..i + 2], 16).unwrap())
        .collect()
}

#[test]
fn java_vectors_decode_and_round_trip() {
    let fixture: Value = serde_json::from_str(include_str!(
        "../../../../config/wire/paper-4.0.0-vectors.json"
    ))
    .unwrap();
    assert_eq!(client_handshake(2), vec![0, 0, 0, 2]);
    let mut groups = std::collections::BTreeMap::<
        String,
        (bool, Vec<sw_performance_bots::waypoint_protocol::Chunk>),
    >::new();
    for vector in fixture["vectors"].as_array().unwrap() {
        let bytes = unhex(vector["hex"].as_str().unwrap());
        let accepted = vector["accepted"].as_bool().unwrap();
        match vector["kind"].as_str().unwrap() {
            "client-handshake" => assert_eq!(bytes.len() == 4, accepted),
            "server-handshake" => {
                assert_eq!(server_handshake(&bytes).unwrap(), (2, 959, true));
            }
            "message-chunk" => {
                let chunk = decode_chunk(&bytes).unwrap();
                assert_eq!(chunk.encode(), bytes);
                let name = vector["name"].as_str().unwrap().rsplit_once('-').unwrap().0;
                groups
                    .entry(name.to_owned())
                    .or_insert((accepted, Vec::new()))
                    .1
                    .push(chunk);
            }
            "upload-request" => {
                let value =
                    sw_performance_bots::waypoint_protocol::decode_upload_request(&bytes).unwrap();
                assert_eq!(
                    value["dimensions"],
                    serde_json::json!(["minecraft:overworld"])
                );
                assert_eq!(value["list"], "perf");
                assert_eq!(value["waypoint"], "origin");
                assert_eq!(value["target"], 0);
                assert_eq!(
                    sw_performance_bots::waypoint_protocol::encode_upload_request(&value).unwrap(),
                    bytes
                );
            }
            _ => panic!("unhandled codec vector"),
        }
    }
    for (name, (accepted, chunks)) in groups {
        let result = reassemble(&chunks);
        assert_eq!(result.is_ok(), accepted, "{name}");
        if let Ok(body) = result {
            if chunks[0].kind == 2 {
                let edit = sw_performance_bots::waypoint_protocol::decode_edit(&body).unwrap();
                assert_eq!(edit["revision"], 17);
                assert_eq!(edit["requestId"], 959);
                assert_eq!(edit["dimension"], "minecraft:overworld");
                assert_eq!(edit["list"], "perf");
                assert_eq!(edit["waypoint"], "origin");
                assert_eq!(
                    sw_performance_bots::waypoint_protocol::encode_edit(&edit).unwrap(),
                    body
                );
            } else if chunks[0].kind == 6 {
                let catalog =
                    sw_performance_bots::waypoint_protocol::decode_empty_catalog(&body).unwrap();
                assert_eq!(catalog["state"], 1);
                assert_eq!(catalog["servers"], serde_json::json!([]));
                assert_eq!(
                    sw_performance_bots::waypoint_protocol::encode_empty_catalog(&catalog).unwrap(),
                    body
                );
            }
        }
    }
}

#[test]
fn rejects_bad_headers_sequences_and_limits() {
    assert!(decode_chunk(&[0; 38]).is_err());
    assert!(server_handshake(&[0; 8]).is_err());
    assert!(server_handshake(&[0; 10]).is_err());
}

#[test]
fn rust_encoded_frames_for_java_verification() {
    let frame = sw_performance_bots::waypoint_protocol::request_snapshot([0; 16]);
    assert!(reassemble(&[decode_chunk(&frame).unwrap()]).is_ok());
    let mut bad = frame.clone();
    bad[33] ^= 1;
    assert!(reassemble(&[decode_chunk(&bad).unwrap()]).is_err());
    let mut large_body = (4096_i32).to_be_bytes().to_vec();
    for _ in 0..4096 {
        large_body.extend(19_i32.to_be_bytes());
        large_body.extend(b"minecraft:overworld");
        large_body.extend(0_i32.to_be_bytes());
    }
    let large_frames =
        sw_performance_bots::waypoint_protocol::frame_message(1, &large_body, [1; 16], false)
            .unwrap();
    assert!(large_frames.len() > 1);
    assert_eq!(
        reassemble(
            &large_frames
                .iter()
                .map(|frame| decode_chunk(frame).unwrap())
                .collect::<Vec<_>>()
        )
        .unwrap(),
        large_body
    );
    if let Ok(path) = std::env::var("SW_PERF_RUST_FRAMES") {
        let hex = |bytes: &[u8]| bytes.iter().map(|b| format!("{b:02x}")).collect::<String>();
        let fixture: Value = serde_json::from_str(include_str!(
            "../../../../config/wire/paper-4.0.0-vectors.json"
        ))
        .unwrap();
        let mut groups = vec![
            serde_json::json!({"frames": [hex(&frame)], "accepted": true}),
            serde_json::json!({"frames": [hex(&bad)], "accepted": false}),
            serde_json::json!({"frames": large_frames.iter().map(|frame| hex(frame)).collect::<Vec<_>>(), "accepted": true}),
        ];
        for offset in [20, 24, 29, 37] {
            let mut invalid = frame.clone();
            invalid[offset] = 255;
            groups.push(serde_json::json!({"frames": [hex(&invalid)], "accepted": false}));
        }
        for vector in fixture["vectors"].as_array().unwrap() {
            let bytes = unhex(vector["hex"].as_str().unwrap());
            if vector["kind"] == "upload-request" {
                let value =
                    sw_performance_bots::waypoint_protocol::decode_upload_request(&bytes).unwrap();
                let encoded =
                    sw_performance_bots::waypoint_protocol::encode_upload_request(&value).unwrap();
                groups.push(serde_json::json!({"kind": "upload-request", "frames": [hex(&encoded)], "accepted": true}));
            } else if vector["name"] == "message-1-false-0" {
                let body = reassemble(&[decode_chunk(&bytes).unwrap()]).unwrap();
                let value = sw_performance_bots::waypoint_protocol::decode_edit(&body).unwrap();
                let encoded = sw_performance_bots::waypoint_protocol::encode_edit(&value).unwrap();
                groups.push(serde_json::json!({"frames": sw_performance_bots::waypoint_protocol::frame_message(2, &encoded, [2;16], false).unwrap().iter().map(|frame| hex(frame)).collect::<Vec<_>>(), "accepted": true}));
            }
        }
        std::fs::write(path, serde_json::to_string_pretty(&groups).unwrap()).unwrap();
    }
}

#[test]
fn decodes_native_snapshot_revisions_and_names() {
    let bytes = vec![0, 0, 0, 0, 0];
    assert_eq!(
        sw_performance_bots::waypoint_protocol::decode_snapshot(&bytes).unwrap(),
        serde_json::json!([])
    );
    assert!(
        sw_performance_bots::waypoint_protocol::decode_snapshot(&[0, 255, 255, 255, 255]).is_err()
    );
    let frame = sw_performance_bots::waypoint_protocol::request_snapshot([0; 16]);
    for offset in [16, 20, 24, 29, 37] {
        let mut bad = frame.clone();
        bad[offset] = 255;
        assert!(decode_chunk(&bad).is_err(), "offset {offset}");
    }
}

#[test]
fn failed_complete_transfer_releases_bytes_and_cannot_grow() {
    use sw_performance_bots::waypoint_protocol::{TransferReceiver, request_snapshot};
    let mut receiver = TransferReceiver::default();
    let mut bad = request_snapshot([0; 16]);
    bad[33] ^= 1;
    for _ in 0..1000 {
        assert!(receiver.receive(&bad).is_err());
        assert_eq!(receiver.retained_bytes(), 0);
        assert_eq!(receiver.pending(), 0);
    }
}

#[test]
fn conflicting_metadata_releases_the_transfer() {
    use sw_performance_bots::waypoint_protocol::{TransferReceiver, request_snapshot};
    let mut receiver = TransferReceiver::default();
    let mut first = request_snapshot([0; 16]);
    first[27] = 2;
    assert!(receiver.receive(&first).unwrap().is_none());
    assert_eq!(receiver.pending(), 1);
    let mut second = first.clone();
    second[23] = 1;
    assert!(receiver.receive(&second).is_err());
    assert_eq!(receiver.retained_bytes(), 0);
    assert_eq!(receiver.pending(), 0);
}
