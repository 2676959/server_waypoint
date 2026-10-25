//! Bounded release-v2 frames; vectors originate in the immutable Java codecs.
use std::io::Read;

pub const MAX_BYTES: usize = 64 * 1024 * 1024;
pub const MAX_CHUNK: usize = 24 * 1024;

pub fn client_handshake(version: i32) -> Vec<u8> {
    version.to_be_bytes().to_vec()
}

pub fn server_handshake(bytes: &[u8]) -> Result<(i32, i32, bool), String> {
    if bytes.len() != 9 || bytes[8] > 1 {
        return Err("invalid server handshake".into());
    }
    Ok((
        i32::from_be_bytes(bytes[..4].try_into().unwrap()),
        i32::from_be_bytes(bytes[4..8].try_into().unwrap()),
        bytes[8] == 1,
    ))
}

#[derive(Clone, Debug)]
pub struct Chunk {
    pub transfer: [u8; 16],
    pub kind: i32,
    pub sequence: i32,
    pub count: i32,
    pub compressed: bool,
    pub size: usize,
    pub checksum: u32,
    pub data: Vec<u8>,
}
impl Chunk {
    pub fn encode(&self) -> Vec<u8> {
        let mut out = self.transfer.to_vec();
        for value in [self.kind, self.sequence, self.count] {
            out.extend(value.to_be_bytes());
        }
        out.push(self.compressed as u8);
        out.extend((self.size as i32).to_be_bytes());
        out.extend(self.checksum.to_be_bytes());
        out.extend((self.data.len() as u16).to_be_bytes());
        out.extend(&self.data);
        out
    }
}

pub fn decode_chunk(bytes: &[u8]) -> Result<Chunk, String> {
    if bytes.len() < 39 {
        return Err("truncated chunk header".into());
    }
    let integer = |offset| i32::from_be_bytes(bytes[offset..offset + 4].try_into().unwrap());
    let kind = integer(16);
    let sequence = integer(20);
    let count = integer(24);
    let size = integer(29);
    let length = u16::from_be_bytes(bytes[37..39].try_into().unwrap()) as usize;
    if !(0..=7).contains(&kind)
        || count <= 0
        || count as usize > MAX_BYTES.div_ceil(MAX_CHUNK)
        || sequence < 0
        || sequence >= count
        || bytes[28] > 1
        || size < 0
        || size as usize > MAX_BYTES
        || length > MAX_CHUNK
        || length + 39 != bytes.len()
    {
        return Err("invalid chunk metadata/length/limits".into());
    }
    Ok(Chunk {
        transfer: bytes[..16].try_into().unwrap(),
        kind,
        sequence,
        count,
        compressed: bytes[28] == 1,
        size: size as usize,
        checksum: u32::from_be_bytes(bytes[33..37].try_into().unwrap()),
        data: bytes[39..].to_vec(),
    })
}

pub fn reassemble(chunks: &[Chunk]) -> Result<Vec<u8>, String> {
    let first = chunks.first().ok_or("no chunks")?;
    if chunks.len() != first.count as usize {
        return Err("incomplete transfer".into());
    }
    let mut ordered: Vec<_> = chunks.iter().collect();
    ordered.sort_by_key(|chunk| chunk.sequence);
    let mut transmitted = Vec::new();
    for (index, chunk) in ordered.iter().enumerate() {
        if chunk.sequence != index as i32
            || chunk.transfer != first.transfer
            || chunk.kind != first.kind
            || chunk.count != first.count
            || chunk.size != first.size
            || chunk.compressed != first.compressed
            || chunk.checksum != first.checksum
        {
            return Err("conflicting/duplicate chunk metadata".into());
        }
        if transmitted.len() + chunk.data.len() > first.size {
            return Err("retained byte limit".into());
        }
        transmitted.extend(&chunk.data);
    }
    let decoded = if first.compressed {
        let mut output = Vec::new();
        flate2::read::ZlibDecoder::new(&transmitted[..])
            .take(first.size as u64 + 1)
            .read_to_end(&mut output)
            .map_err(|e| e.to_string())?;
        output
    } else {
        transmitted
    };
    if decoded.len() != first.size || crc32fast::hash(&decoded) != first.checksum {
        return Err("size/checksum mismatch".into());
    }
    Ok(decoded)
}

pub fn request_snapshot(transfer: [u8; 16]) -> Vec<u8> {
    let body = vec![0, 0, 0, 0];
    Chunk {
        transfer,
        kind: 1,
        sequence: 0,
        count: 1,
        compressed: false,
        size: body.len(),
        checksum: crc32fast::hash(&body),
        data: body,
    }
    .encode()
}

struct Reader<'a> {
    bytes: &'a [u8],
    position: usize,
    objects: usize,
}
impl<'a> Reader<'a> {
    fn take(&mut self, count: usize) -> Result<&'a [u8], String> {
        if count > self.bytes.len() - self.position {
            return Err("truncated logical message".into());
        }
        let start = self.position;
        self.position += count;
        Ok(&self.bytes[start..self.position])
    }
    fn integer(&mut self) -> Result<i32, String> {
        Ok(i32::from_be_bytes(self.take(4)?.try_into().unwrap()))
    }
    fn count(&mut self) -> Result<usize, String> {
        let count = self.integer()?;
        if count < 0 || count as usize > 1_000_000 - self.objects {
            return Err("decoded object budget".into());
        }
        self.objects += count as usize;
        Ok(count as usize)
    }
    fn text(&mut self) -> Result<String, String> {
        let count = self.integer()?;
        if count < 0 {
            return Err("negative text length".into());
        }
        String::from_utf8(self.take(count as usize)?.to_vec()).map_err(|e| e.to_string())
    }
}

/// Returns independent dimension/list/revision/name assertions from a native download.
pub fn decode_snapshot(bytes: &[u8]) -> Result<serde_json::Value, String> {
    let mut reader = Reader {
        bytes,
        position: 0,
        objects: 0,
    };
    let kind = reader.take(1)?[0];
    if kind != 0 && kind != 3 {
        return Err("expected updates/world snapshot".into());
    }
    let dimensions = reader.count()?;
    let mut lists = Vec::new();
    for _ in 0..dimensions {
        let dimension = reader.text()?;
        let count = reader.count()?;
        for _ in 0..count {
            let name = reader.text()?;
            let _display = reader.text()?;
            let revision = reader.integer()?;
            let count = reader.count()?;
            let mut waypoints = Vec::new();
            for _ in 0..count {
                waypoints.push(reader.text()?);
                reader.text()?;
                reader.text()?;
                reader.take(16 + 3)?; // position, RGB, signed yaw, visibility
                let keywords = reader.count()?;
                for _ in 0..keywords {
                    reader.text()?;
                }
                reader.text()?;
                match reader.take(1)?[0] {
                    0 => {}
                    1 => {
                        reader.text()?;
                    }
                    _ => return Err("invalid icon flag".into()),
                }
            }
            lists.push(serde_json::json!({"dimension": dimension, "name": name, "revision": revision, "waypoints": waypoints}));
        }
    }
    if reader.position != bytes.len() {
        return Err("trailing logical message bytes".into());
    }
    Ok(serde_json::Value::Array(lists))
}

#[derive(Default)]
pub struct TransferReceiver {
    transfers: std::collections::BTreeMap<[u8; 16], (std::time::Instant, Vec<Chunk>)>,
}
impl TransferReceiver {
    pub fn pending(&self) -> usize {
        self.transfers.len()
    }
    pub fn retained_bytes(&self) -> usize {
        self.transfers
            .values()
            .flat_map(|(_, frames)| frames)
            .map(|frame| frame.data.len())
            .sum()
    }
    pub fn clear(&mut self) {
        self.transfers.clear();
    }
    pub fn expire(&mut self) -> bool {
        let before = self.pending();
        self.transfers
            .retain(|_, (started, _)| started.elapsed().as_secs() < 30);
        self.pending() != before
    }
    pub fn receive(&mut self, bytes: &[u8]) -> Result<Option<(i32, Vec<u8>)>, String> {
        let chunk = decode_chunk(bytes)?;
        let key = chunk.transfer;
        let (started, mut frames) = self
            .transfers
            .remove(&key)
            .unwrap_or_else(|| (std::time::Instant::now(), Vec::new()));
        if self.transfers.len() >= 8 {
            return Err("active transfer limit".into());
        }
        if let Some(first) = frames.first() {
            if chunk.kind != first.kind
                || chunk.count != first.count
                || chunk.compressed != first.compressed
                || chunk.size != first.size
                || chunk.checksum != first.checksum
            {
                return Err("conflicting transfer metadata".into());
            }
            if let Some(existing) = frames.iter().find(|frame| frame.sequence == chunk.sequence) {
                if existing.data != chunk.data {
                    return Err("conflicting duplicate sequence".into());
                }
                self.transfers.insert(key, (started, frames));
                return Ok(None);
            }
        }
        let retained: usize = frames.iter().map(|frame| frame.data.len()).sum();
        if retained + chunk.data.len() > chunk.size
            || self.retained_bytes() + retained + chunk.data.len() > MAX_BYTES
        {
            return Err("retained byte limit".into());
        }
        frames.push(chunk);
        if frames.len() == frames[0].count as usize {
            return reassemble(&frames).map(|body| Some((frames[0].kind, body)));
        }
        self.transfers.insert(key, (started, frames));
        Ok(None)
    }
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|byte| format!("{byte:02x}")).collect()
}
fn uuid_bytes(value: &serde_json::Value) -> Result<Vec<u8>, String> {
    let text = value.as_str().ok_or("UUID string missing")?;
    if text.len() != 32 || !text.is_ascii() {
        return Err("invalid UUID length".into());
    }
    (0..32)
        .step_by(2)
        .map(|i| u8::from_str_radix(&text[i..i + 2], 16).map_err(|e| e.to_string()))
        .collect()
}
fn write_text(output: &mut Vec<u8>, value: &serde_json::Value) -> Result<(), String> {
    let text = value.as_str().ok_or("text missing")?;
    if text.len() > MAX_BYTES {
        return Err("text exceeds limit".into());
    }
    output.extend((text.len() as i32).to_be_bytes());
    output.extend(text.as_bytes());
    Ok(())
}
fn integer(value: &serde_json::Value) -> Result<i32, String> {
    i32::try_from(value.as_i64().ok_or("integer missing")?).map_err(|e| e.to_string())
}
fn finish(reader: &Reader<'_>) -> Result<(), String> {
    if reader.position != reader.bytes.len() {
        return Err("trailing bytes".into());
    }
    Ok(())
}

pub fn decode_upload_request(bytes: &[u8]) -> Result<serde_json::Value, String> {
    let mut reader = Reader {
        bytes,
        position: 0,
        objects: 0,
    };
    let request = hex(reader.take(16)?);
    let count = reader.count()?;
    let mut dimensions = Vec::new();
    for _ in 0..count {
        dimensions.push(reader.text()?);
    }
    let mut optional = Vec::new();
    for _ in 0..2 {
        optional.push(match reader.take(1)?[0] {
            0 => serde_json::Value::Null,
            1 => serde_json::json!(reader.text()?),
            _ => return Err("invalid optional flag".into()),
        });
    }
    let target = reader.take(1)?[0];
    if target > 1 {
        return Err("unknown upload target".into());
    }
    finish(&reader)?;
    Ok(
        serde_json::json!({"requestId": request, "dimensions": dimensions, "list": optional[0], "waypoint": optional[1], "target": target}),
    )
}
pub fn encode_upload_request(value: &serde_json::Value) -> Result<Vec<u8>, String> {
    let mut output = uuid_bytes(&value["requestId"])?;
    let dimensions = value["dimensions"]
        .as_array()
        .ok_or("dimension list missing")?;
    output.extend((dimensions.len() as i32).to_be_bytes());
    for dimension in dimensions {
        write_text(&mut output, dimension)?;
    }
    for name in ["list", "waypoint"] {
        output.push((!value[name].is_null()) as u8);
        if !value[name].is_null() {
            write_text(&mut output, &value[name])?;
        }
    }
    let target = integer(&value["target"])?;
    if !(0..=1).contains(&target) {
        return Err("unknown upload target".into());
    }
    output.push(target as u8);
    Ok(output)
}

pub fn decode_edit(bytes: &[u8]) -> Result<serde_json::Value, String> {
    let mut reader = Reader {
        bytes,
        position: 0,
        objects: 0,
    };
    let request = i64::from_be_bytes(reader.take(8)?.try_into().unwrap());
    let dimension = reader.text()?;
    let list = reader.text()?;
    let waypoint = reader.text()?;
    let revision = reader.integer()?;
    let mut patch = Vec::new();
    for index in 0..10 {
        let operation = reader.take(1)?[0];
        if operation > 2 {
            return Err("unknown patch operation".into());
        }
        let value = if operation != 1 {
            serde_json::Value::Null
        } else {
            match index {
                0 | 1 | 2 | 8 | 9 => serde_json::json!(reader.text()?),
                3 => serde_json::json!([reader.integer()?, reader.integer()?, reader.integer()?]),
                4 | 5 => serde_json::json!(reader.integer()?),
                6 => {
                    let byte = reader.take(1)?[0];
                    if byte > 1 {
                        return Err("invalid visibility".into());
                    }
                    serde_json::json!(byte == 1)
                }
                7 => {
                    let count = reader.count()?;
                    let mut words = Vec::new();
                    for _ in 0..count {
                        words.push(reader.text()?);
                    }
                    serde_json::json!(words)
                }
                _ => unreachable!(),
            }
        };
        patch.push(serde_json::json!({"operation": operation, "value": value}));
    }
    finish(&reader)?;
    Ok(
        serde_json::json!({"requestId": request, "dimension": dimension, "list": list, "waypoint": waypoint, "revision": revision, "patch": patch}),
    )
}
pub fn encode_edit(value: &serde_json::Value) -> Result<Vec<u8>, String> {
    let mut output = value["requestId"]
        .as_i64()
        .ok_or("request ID missing")?
        .to_be_bytes()
        .to_vec();
    for key in ["dimension", "list", "waypoint"] {
        write_text(&mut output, &value[key])?;
    }
    output.extend(integer(&value["revision"])?.to_be_bytes());
    let patch = value["patch"].as_array().ok_or("patch missing")?;
    if patch.len() != 10 {
        return Err("ten patch fields required".into());
    }
    for (index, field) in patch.iter().enumerate() {
        let operation = integer(&field["operation"])?;
        if !(0..=2).contains(&operation) {
            return Err("unknown patch operation".into());
        }
        output.push(operation as u8);
        if operation != 1 {
            continue;
        }
        let value = &field["value"];
        match index {
            0 | 1 | 2 | 8 | 9 => write_text(&mut output, value)?,
            3 => {
                let coordinates = value.as_array().ok_or("position missing")?;
                if coordinates.len() != 3 {
                    return Err("three coordinates required".into());
                }
                for coordinate in coordinates {
                    output.extend(integer(coordinate)?.to_be_bytes());
                }
            }
            4 | 5 => output.extend(integer(value)?.to_be_bytes()),
            6 => output.push(value.as_bool().ok_or("visibility missing")? as u8),
            7 => {
                let words = value.as_array().ok_or("keywords missing")?;
                output.extend((words.len() as i32).to_be_bytes());
                for word in words {
                    write_text(&mut output, word)?;
                }
            }
            _ => unreachable!(),
        }
    }
    Ok(output)
}

/// Initial catalog vector is an empty canonical replacement; nonempty catalogs remain a later adapter.
pub fn decode_empty_catalog(bytes: &[u8]) -> Result<serde_json::Value, String> {
    let mut reader = Reader {
        bytes,
        position: 0,
        objects: 0,
    };
    let request = hex(reader.take(16)?);
    let state = reader.integer()?;
    if !(1..=4).contains(&state) || reader.count()? != 0 {
        return Err("unsupported/nonempty catalog vector".into());
    }
    finish(&reader)?;
    Ok(serde_json::json!({"requestId": request, "state": state, "servers": []}))
}
pub fn encode_empty_catalog(value: &serde_json::Value) -> Result<Vec<u8>, String> {
    let mut output = uuid_bytes(&value["requestId"])?;
    let state = integer(&value["state"])?;
    if !(1..=4).contains(&state) || value["servers"].as_array().ok_or("servers missing")?.len() != 0
    {
        return Err("unsupported/nonempty catalog vector".into());
    }
    output.extend(state.to_be_bytes());
    output.extend(0_i32.to_be_bytes());
    Ok(output)
}

pub fn frame_message(
    kind: i32,
    body: &[u8],
    transfer: [u8; 16],
    compression: bool,
) -> Result<Vec<Vec<u8>>, String> {
    use std::io::Write;
    if body.len() > MAX_BYTES || !(0..=7).contains(&kind) {
        return Err("outgoing message limit/type".into());
    }
    let compressed = if compression {
        let mut encoder =
            flate2::write::ZlibEncoder::new(Vec::new(), flate2::Compression::default());
        encoder.write_all(body).map_err(|e| e.to_string())?;
        encoder.finish().map_err(|e| e.to_string())?
    } else {
        Vec::new()
    };
    let compress = compression && compressed.len() < body.len();
    let transmitted = if compress { &compressed[..] } else { body };
    let count = transmitted.len().div_ceil(MAX_CHUNK).max(1);
    let mut frames = Vec::new();
    for sequence in 0..count {
        let from = sequence * MAX_CHUNK;
        let to = ((sequence + 1) * MAX_CHUNK).min(transmitted.len());
        frames.push(
            Chunk {
                transfer,
                kind,
                sequence: sequence as i32,
                count: count as i32,
                compressed: compress,
                size: body.len(),
                checksum: crc32fast::hash(body),
                data: transmitted[from..to].to_vec(),
            }
            .encode(),
        );
    }
    Ok(frames)
}
