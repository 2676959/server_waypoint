# Server selector metadata

The server-selector change originally extended application protocol version 1. The current
[permission-feedback contract](2026-10-05-permission-check-feedback.md) retains application version 1.
The selector fields remain part of the wire format below. Deploy matching builds of the client,
backends, and coordinator together. The Minecraft remote-catalog message carries the icon as well; the 4.0.0
release sends it under custom-payload protocol 2.

- `CatalogMetadata` (type 10) appends `iconItem` after export policy, encoded as a canonical UTF-8
  string. It must match `[a-z0-9_.-]+:[a-z0-9/._-]+` and be at most 256 characters.
- Canonical catalog headers and application envelopes carry version 1. Registration
  uses the shared `CrossServerProtocol.PROTOCOL_VERSION`, including coordinator validation.
- The receiver installs the icon together with display name when its correlated complete snapshot
  is accepted. Delta updates retain that metadata. Coordinator metadata byte budgets count both
  display name and icon. Full fan-out includes the icon.
- Minecraft remote-catalog messages encode the icon immediately after each server's display name,
  before catalog state and transport mode. The decoder bounds it before allocation.

Backend `cross-server.json` accepts `serverIconItem` (default `minecraft:beacon`); its value is
validated when creating the publisher. Registry resolution belongs only to the mods GUI, so Paper
and the coordinator do not depend on Minecraft item classes.
