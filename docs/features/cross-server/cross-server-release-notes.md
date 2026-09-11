# Cross-server waypoints — release notes

Release candidate; deployment approval depends on the [verification record](validation/cross-server-release-readiness.md).

- Optional Velocity integration lets players browse PUBLIC waypoint catalogs and request prepared,
  destination-validated teleports across dedicated backends. The feature is disabled by default.
- Vanilla clients can use `/wp remote servers`, `/wp remote list` and `/wp remote tp`. Matching
  protocol-11 modded clients also receive a separate read-only remote manager and confirmation UI.
- Default Noise KK authenticates paired backend keys and encrypts TCP traffic. Pairing uses public
  keys and requires no manually created certificates. Explicit loopback-only plaintext is available
  for trusted local processes; it provides no cryptographic identity or encryption and is never a fallback.
- Source, proxy and destination checks bind each one-time handoff to the exact target and player.
  The destination resolves current waypoint coordinates again after arrival.
- Remote data does not replace local waypoint files. Existing local commands remain local.

Install the matching shaded Velocity plugin (Java 25) and backend artifacts together; remote client
synchronization uses Minecraft custom-payload protocol 11, independently of cross-server protocol v1.
See the [administrator guide](cross-server-admin.md) for setup, permissions, rotation and recovery.
