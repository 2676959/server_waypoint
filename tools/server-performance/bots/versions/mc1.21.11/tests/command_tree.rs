use azalea::protocol::packets::game::{ClientboundCommands, c_commands::{BrigadierNodeStub, NodeType}};
use sw_performance_bots::command_tree::install;

fn fixture() -> ClientboundCommands {
    let node = |kind, children| BrigadierNodeStub {
        is_executable: false, children, redirect_node: None, node_type: kind, is_restricted: false,
    };
    ClientboundCommands { root_index: 0, entries: vec![
        node(NodeType::Root, vec![1]),
        node(NodeType::Literal { name: "wp".into() }, vec![2]),
        node(NodeType::Literal { name: "tp".into() }, vec![]),
    ] }
}

#[test]
fn installs_decoded_edges_and_permission_branch_without_dropping_restricted_flags() {
    let mut packet = fixture();
    packet.entries[2].is_restricted = true;
    let tree = install(&packet).unwrap();
    assert_eq!(tree["nodeCount"], 3);
    assert_eq!(tree["branches"], serde_json::json!(["tp"]));
    assert_eq!(tree["installed"], true);
    assert_eq!(tree["nodes"][2]["restricted"], true);
    assert_eq!(tree["packetBodyBytes"], 16);
    assert_eq!(tree["packetCrc32"], 2768323456_u64);
}

#[test]
fn rejects_invalid_roots_edges_redirects_and_child_cycles_before_installation() {
    for kind in 0..4 {
        let mut packet = fixture();
        match kind {
            0 => packet.root_index = 3,
            1 => packet.entries[1].children.push(99),
            2 => packet.entries[2].redirect_node = Some(99),
            _ => packet.entries[2].children.push(1),
        }
        assert!(install(&packet).is_err(), "malformed graph {kind}");
    }
}
