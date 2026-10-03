# Action logging validation — 2026-10-03

The focused initial test failed because successful list creation emitted no action log. It passed
after implementation. Regression coverage exercises real command dispatch and SLF4J output:

- Actor name and UUID, target identity, and one success event per mutation.
- Duplicate additions and read-only browsing produce no false success events.
- Delayed saves do not log early; failed saves report `save_failed`.
- Teleport completion distinguishes success from cancellation/failure.
- Add, edit, remove and restore all produce audit entries.
- Non-player command sources are explicit; control characters cannot forge log lines.
- GUI edits distinguish persisted changes from save failures.
- Uploads report aggregate counts and request/player identities; later-dimension failures report partial commits.

Final verification command:

```sh
./gradlew :common:test :mods:26.1.2-fabric:compileJava :mods:1.20.1-fabric:compileJava :mods:26.3-neoforge:compileJava :mods:26.2-forge:compileJava :paper:26.2-paper:compileJava --continue --console=plain
```

All five platform compile targets passed, including the old and current teleport API branches.
Common tests: **765 passed, 1 failed, 766 total**. The remaining failure is
`DimensionScreensTest.theDimensionListPutsTheViewersDimensionFirstAndEmptyOnesOnOneLine`,
line 84: expected `dark_gray`, received `white`. This asserts unrelated text styling; neither its
production implementation nor its assertion was changed for logging.

An earlier retained suite report also contained
`WaypointFilesManagerConcurrencyTest.deferredNestedCallbackFailurePropagatesToOutermostMutationCaller`;
it passed on the final full-suite run. No concurrency code was changed.

`git diff --check` passed. Stonecutter generated and compiled the touched version branches. Existing
Forge mapping/deprecation warnings and the NeoForge Xaero deprecation warning remain.

The independent review agent could not run because of its usage limit; the final diff was reviewed
inline. No live Minecraft/Paper/Folia server, actual console output, or cross-server transfer was
validated. Changes are uncommitted.
