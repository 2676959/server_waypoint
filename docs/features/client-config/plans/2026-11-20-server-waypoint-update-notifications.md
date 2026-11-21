# Server Waypoint Update Notifications Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` for inline execution or `superpowers:subagent-driven-development` when the user chooses delegated execution. Steps use checkbox syntax for tracking.

**Goal**  Let players choose **Off**, **Chat**, or **Toast** for successful incremental server waypoint updates.

**Architecture**  Give the preference its own client config field. Report the actual result of each incremental apply and route eligible notifications through the chat and toast delivery shared with map sync.

**Tech stack**  Java 17 language features, GSON, Minecraft client components, Stonecutter, and JUnit 5.

**Spec**  [Server waypoint update notifications](../specs/2026-11-20-server-waypoint-update-notifications-design.md)

## Global constraints

- Default to **Off**. Apply dropdown changes immediately, persist them through the existing config save path, and include the setting in **Reset to defaults**.
- Emit at most one confirmation for an applied Shared or Private `ADD`, `UPDATE`, or `REMOVE`. Restores delivered as `ADD` follow the add path. Use the updated display name for an edit and the removed waypoint's display name for a removal.
- Keep server broadcast chat, command results, errors, and map sync on their existing paths. Scope the feature to incremental server updates. Keep packet formats and server configuration intact.
- Put translations in `mods/src/main/resources/assets/server_waypoint/lang` and use Minecraft `%s` or `%1$s` for the single waypoint argument. Retain the separate `MessageFormat` convention in `common/src/main/resources/lang`.
- Read [the GUI guide](../../../tips/gui/local-guide.md) before changing GUI code. Retain the active Stonecutter project and the existing version branches for chat and toasts. Leave changes uncommitted unless the user authorizes a commit.

## Review focus

- A matching broadcast without an applied delta produces only the server chat line. A delta still produces a confirmation with server broadcasts off. Exercise both in Task 3.
- A successful GUI edit acknowledgement and its matching delta produce one confirmation regardless of arrival order. Exercise this in Tasks 2 and 3.
- Replayed revisions, revision gaps, and an edit or removal targeting a missing waypoint produce no confirmation. Verify actual resulting state and delivery count in Task 2.
- A renamed waypoint displays its new name. Removal displays the name from the waypoint that was actually removed. Use distinct identifiers and display names in Task 2.
- Private snapshots, permission loss, and packets from a previous connection never publish a confirmation from stale cache contents. Exercise these in Task 2.

### Task 1 — Persist and expose the setting

**Files**

- Modify `mods/src/main/java/_959/server_waypoint/common/client/ClientConfig.java` and `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/ClientConfigSettings.java`.
- Modify the six JSON files in `mods/src/main/resources/assets/server_waypoint/lang`.
- Test `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigSettingsTest.java` and `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/ClientConfigTranslationTest.java`.

**Interfaces**

- Use `ClientConfig.SyncNotificationMode` for the three choices. Store `@Expose private SyncNotificationMode waypointUpdateNotificationMode` with `DEFAULT_WAYPOINT_UPDATE_NOTIFICATION_MODE = SyncNotificationMode.OFF` and expose `SyncNotificationMode getWaypointUpdateNotificationMode()` and `void setWaypointUpdateNotificationMode(SyncNotificationMode mode)`.
- Declare `ClientConfigSettings.WAYPOINT_UPDATE_NOTIFICATIONS` as an `EnumSetting<SyncNotificationMode>` keyed by `waypoint_update_notifications`. Place it at the end of `WAYPOINT_MANAGER`. The screen and reset list both read that setting list.

- [x] In `ClientConfigSettingsTest`, extend the existing dropdown coverage with `waypointUpdateNotificationDropdownPersistsIndependentlyAndResetsToOff`. With `GSON.fromJson("{}", ClientConfig.class)`, assert `assertEquals(OFF, config.getWaypointUpdateNotificationMode())` and `assertEquals(CHAT, config.getSyncNotificationMode())`. Set the map sync preference to Toast. Use the actual waypoint dropdown to select Chat, Toast, and Off, and verify each choice on the trigger and after a GSON round trip. Assert the map sync preference stays Toast throughout the selections. From Toast, run `resetAll(config, forScreen(Set.of()))` and confirm the trigger and stored waypoint choice show Off. Assert the row is present with no installed map mods.
- [x] Add the five setting keys and three message keys below to `ClientConfigTranslationTest`. Assert every locale defines them and the success messages each have `List.of(1)` arguments. Include `en_us`, `es_es`, `he_il`, `zh_cn`, `zh_hk`, and `zh_tw`.
- [x] Run the focused tests and confirm the red failures point to the missing setting API and keys.

  ```sh
  rtk ./gradlew :mods:26.1.2-fabric:test --tests '*ClientConfigSettingsTest' --tests '*ClientConfigTranslationTest' --console=plain
  ```

- [x] Add the config field and setting descriptor. Follow the existing defaulted enum getter and dropdown setter conventions so the selection uses the normal screen save and reset paths.
- [x] Add the client translations. Use these English values and translate them in the other five client locales.

  | Key | English value |
  | --- | --- |
  | `server_waypoint.config.waypoint_update_notifications` | Waypoint update notifications |
  | `server_waypoint.config.waypoint_update_notifications.tooltip` | Choose how your client confirms waypoint changes it receives from the server. |
  | `server_waypoint.config.waypoint_update_notifications.off` | Off |
  | `server_waypoint.config.waypoint_update_notifications.chat` | Chat |
  | `server_waypoint.config.waypoint_update_notifications.toast` | Toast |
  | `server_waypoint.update_notification.added` | Waypoint added: %s |
  | `server_waypoint.update_notification.updated` | Waypoint edited: %s |
  | `server_waypoint.update_notification.removed` | Waypoint removed: %s |

- [x] Rerun the focused command. Expect `BUILD SUCCESSFUL` and both test classes to pass.

### Task 2 — Deliver notifications after actual application

**Files**

- Create `mods/src/main/java/_959/server_waypoint/common/client/ClientNotifications.java`.
- Move `mods/src/main/java/_959/server_waypoint/common/client/integrations/WaypointSyncToast.java` to `mods/src/main/java/_959/server_waypoint/common/client/WaypointSyncToast.java`.
- Modify `mods/src/main/java/_959/server_waypoint/common/client/integrations/MapModSyncNotifications.java` and `mods/src/main/java/_959/server_waypoint/common/client/integrations/MapModChatHelper.java`.
- Modify `mods/src/main/java/_959/server_waypoint/common/client/WaypointClientMod.java` and `mods/src/main/java/_959/server_waypoint/common/client/ClientWaypointCollections.java`.
- Test `mods/src/test/java/_959/server_waypoint/common/client/ClientManualDownloadTest.java` through its existing chunked `receive` fixture.

**Interfaces**

- `ClientNotifications.show(Minecraft minecraft, SyncNotificationMode mode, Component chatMessage, Component toastMessage)` is the shared delivery entry point. It keeps the existing no-player guard and selects local chat, the existing compass toast, or no display.
- In `ClientWaypointCollections`, retain `boolean apply(WaypointScopedMessage message, long receivedGeneration)` and delegate it to package-private `ApplyResult applyWithResult(WaypointScopedMessage message, long receivedGeneration) throws IOException`. Define `record ApplyResult(boolean applied, @Nullable String modifiedWaypointDisplayName)` in that class. `applied()` preserves the boolean contract. The name is present only when a waypoint mutation returned a waypoint.
- Use package-private `WaypointClientMod(Minecraft mc, Path gameRoot, Path configDir, BiConsumer<WaypointModificationType, String> waypointUpdateNotification)` for the recording fixture. The existing three-argument constructor supplies the production notification handler. The handler reads `getWaypointUpdateNotificationMode()` at delivery time and uses the Task 1 message keys.

- [x] Extend `ClientManualDownloadTest` with a recording notification sink and these focused regressions. Widen its `receive(WaypointData data)` helper to `receive(ChunkedMessage message)` and use `onMessageChunk(frame, receivedGeneration)` for the old-generation case. Use the actual chunk receiver and real list revisions.

  | Test | Assertions |
  | --- | --- |
  | `appliedWaypointDeltasNotifyOncePerMutation` | Run for Shared and Private. Apply `ADD` at revision 8 with identifier `home` and display name `Home Base`, then `UPDATE` at 9 to identifier `camp` and display name `Camp Alpha`, then `REMOVE` at 10. Assert `List.of(Map.entry(ADD, "Home Base"), Map.entry(UPDATE, "Camp Alpha"), Map.entry(REMOVE, "Camp Alpha"))`, final revision `10`, and no remaining waypoint. |
  | `replayedOrUnappliedWaypointDeltasStaySilent` | Run for Shared and Private. After a valid `ADD` at 8, replay revision 8, apply `UPDATE` and `REMOVE` at 9 targeting a missing identifier, then send a delta at revision 10. Assert the notification count is still `1` and revision is still `8`. |
  | `editResultAndDeltaNotifyOnlyOnceInEitherOrder` | Install a fixture `ClientWaypointAccess` on the test client using reflection, with the client's existing collections and profiles and the recording transport pattern from `ClientWaypointAccessTest`. Submit through `client.access().edit(...)` and use the recorded request ID to send a matching success result and `UPDATE` delta at revision 8 in both orders. Assert the recorder is empty after the result if it arrives first. After both messages, assert `List.of(Map.entry(UPDATE, "Camp Alpha"))` and that the edit future completed with `SAVED`. |
  | `snapshotsListsAndStalePrivatePacketsStaySilent` | Assert the recorder stays empty after Shared and Private snapshots and a list change. Revoke Private authorization and send a Private delta. Send a valid delta tagged with the old generation after switching the backend. Assert `assertTrue(notifications.isEmpty())`. Keep the existing assertions that Private changes do not sync to map mods. |

- [x] Run `rtk ./gradlew :mods:26.1.2-fabric:test --tests '*ClientManualDownloadTest' --console=plain`. Confirm the new tests fail at the missing sink contract or missing notification assertions.
- [x] Move the existing chat and toast delivery into `ClientNotifications.show`. Keep both producers using the same existing Stonecutter branches and keep the distinct chat and toast copy for map sync. Move `WaypointSyncToast` with its replacement tokens in place and remove the superseded `MapModChatHelper.displayClientMessage` method.
- [x] In `ClientWaypointCollections.applyWithResult`, capture the `SimpleWaypoint` returned by the existing incremental `ADD`, `UPDATE`, and `REMOVE` calls. Preserve the revision, permission, cache-save, change-listener, and boolean behavior. Produce the name from the returned object after the existing apply path finishes. A null update or removal result produces no name even when the message was accepted for processing.
- [x] In `WaypointClientMod`, notify Shared from the `ADD`, `UPDATE`, and successful `REMOVE` branches of `onWaypointModification`, after the cache save and before map-mod delivery. Notify Private from `applyCollectionMessage` using the `ApplyResult` name, after the apply error handler. This helper is also the Private path used by `applyChunkedMessage`. Keep edit acknowledgements, snapshot and list updates, and local mutation paths outside the notification calls.
- [x] Rerun the focused tests with `--tests '*ClientWaypointCollectionsTest'` and `--tests '*MapModIntegrationsTest'` added. Expect `BUILD SUCCESSFUL`. Existing snapshot/map tests must still pass as well as the new apply regressions.

### Task 3 — Verify version branches and player-visible behavior

**Files**

- Update `docs/features/client-config/README.md` with the shipped setting location, modes, default, and server-update scope. Add the setting to the existing client dropdown entry in `docs/tips/gui/local-guide.md`.
- Record results in `docs/features/client-config/validation/2026-11-20-server-waypoint-update-notifications.md`.

- [x] Run the active Fabric test suite and representative compiles across the three toast manager API branches. Confirm `BUILD SUCCESSFUL` for each task and `rtk git diff --check` passes. Check the moved toast and dispatcher Stonecutter blocks and tokens before running.

  ```sh
  rtk ./gradlew :mods:26.1.2-fabric:test :mods:1.20.1-forge:compileJava :mods:26.3-neoforge:compileJava --console=plain
  rtk git diff --check
  ```

- [x] In a modded client attached to a dedicated test server, use two players to confirm Shared `/wp` adds, edits, and removes from both the actor and the observer. In **Off**, check the Waypoint Manager still updates and server command feedback appears. In **Chat**, check for one client confirmation per applied mutation and separately observe the announcement with server `broadcastFeedback=all`. Reload with `broadcastFeedback=off` and repeat. Replay a captured announcement via server system chat with no mutation to check the broadcast-only case.
- [x] In **Toast**, verify a Private add, GUI edit, and removal as the owner. Confirm the GUI edit produces one compass toast for the applied change and that a rejected edit continues to show its form error. Check that resetting to **Off** suppresses the next confirmation immediately, and verify the saved choice across a client restart before and after reset.
- [x] Confirm a full sync and a Local profile change are silent. Retest the map sync preference independently with an installed integration. Record the version, loader, commands, and observed results. Keep build and fixture evidence distinct from rendered chat and toast observations.
