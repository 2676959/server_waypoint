# Xaero handshake reconciliation

Extend the existing map synchronization coordinator so both Xaero and VoxelMap
reconcile managed markers on an unchanged remote handshake. The user authorized
fixing the confirmed Xaero readiness-order gap.

Use independent pending targets rather than VoxelMap-only state. Auto-sync
preferences remain independent of readiness; unavailable maps queue one full
reconciliation from the latest model. Xaero's world-ready callback marks readiness
without writing markers itself, avoiding duplicate writes around the handshake.
Reset its readiness on join/leave so a previous connection cannot admit new writes.

Preserve integrated-world entry suppression, manual sync preference independence,
disabled/uninstalled cancellation, and idle-tick behavior. No protocol,
localization, reload, icon, or backward-compatibility changes. Verify both readiness
orders, repeated readiness callbacks, target independence, disconnect cancellation,
and representative loader/version suites. Live game behavior remains a separate gate.
