# Priority performance repairs

User request, 2026-10-26: optimize the high-priority measured issue and measure the
candidate on Paper and Fabric. This authorizes targeted repairs before completion
of the wider original-release matrix; runtime validation for this change is limited
to Paper and Fabric. Paper and Folia continue to share one plugin artifact.

## Inventory hot path

The existing Paper scan materializes Bukkit item metadata, bundle children and
shulker block states during read-only checks. Read the native immutable components
instead, preserving the byte-valued Bukkit PDC marker, portable container types and
the existing scan/cleanup depth boundaries. Keep mutation in the existing Bukkit
cleanup path, entered only after a read-only positive scan. No item-result cache,
session-based bypass, asynchronous inventory access or protection relaxation.

Owned production files: `paper/src/main/java/_959/server_waypoint/navigation/`
(`PaperNavigationItemManager.java` and a package-private component reader).
Tests: `paper/src/test/java/_959/server_waypoint/navigation/` plus native assertions
and matching before/after measurements in the retained diagnostic root.

- [x] Freeze current development artifacts and source identity; retain published
      release evidence separately.
- [x] Record the unchanged native allocation-budget failure before production edits.
- [x] Verify reader behavior: marker types, ordinary metadata, nested bundles and
      shulkers, scan/cleanup depth boundaries, mutation between repeated reads.
- [x] Implement component reads and negative-cleanup fast path; preserve the positive
      cleanup implementation and check real Bukkit-written markers at runtime.
- [x] Run focused and neighboring behavior tests and matched Paper/Fabric diagnostics.
      Record processing latency and allocation separately from server tick metrics;
      distinguish synthetic dispatch from physical inventory throughput.
- [x] Record results, remaining high-priority query/completion costs, localization
      impact (none expected) and runtime proof boundaries. Do not commit unless asked.

Retained root: `/Volumes/ssd/sw-performance-repair-20261026/`.

## Shared query and completion paths

Reuse normalized fuzzy-filter tokens and distance buffers within one list query;
retain the existing distance algorithm, immutable waypoint snapshots, result identity,
filtering and sort behavior. Scratch data never escapes the request or becomes shared
state. For plain, unstyled completion tooltips, use Brigadier's literal message;
formatted components retain the existing platform serializer. Preserve escaped
identifiers, all suggestions and tooltip content/styles.

Owned files: `common/src/main/java/_959/server_waypoint/core/waypoint/WaypointQueryEngine.java`
and `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, with
their existing query and command test classes.

- [x] Retain native 10,000-result query/completion baselines on both platforms.
- [x] Run regression cases for repeated fuzzy comparisons, unequal token lengths,
      keywords, escaped identifiers and plain/formatted tooltips before shared edits.
- [x] Apply request-local reuse and literal tooltip conversion, then run the common,
      Paper and Fabric tests and matching native measurements.

Results and exact artifacts: [priority repair report](../validation/2026-10-26-priority-repairs.md).
Read-only review found a shulker capacity edge case; a failing regression test proved
it and the final reader limits traversal to the same first 27 physical slots as Bukkit.
All supported Paper API branches compile after the correction.

The independent `bundle-9/map` world-spawn test still fails identically before and
after the repair, as already recorded by the original audit. Both failing command
exits remain retained; the other seven nested cases pass. Preserving the existing
depth boundaries does not resolve that separate protection defect. No full-release,
all-version runtime or soak acceptance is claimed by this focused plan.
