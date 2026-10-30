## Task 7: Four manager views, profiles, copying, and composed rendering

**Dependencies:** Tasks 4-6.

**Files:** add `mods/common/client/ClientWaypointQuery.java`; update manager, add/edit forms,
list/details widgets, client config/profile controls, and renderer scene composition.
Extend manager/render tests; add `ClientWaypointQueryTest.java` and
`gui/screens/WaypointManagerScreenCollectionTest.java`.

**Interfaces:** produces source-tagged queries, four view modes, destination/profile controls, and
a composed render scene. Reuse the screen-owned `IconDropdownMenu` for source switching.

- [ ] Test Combined includes all three current collections, retains duplicate identifiers with
  distinct selection keys, and excludes Remote. Grouped headers and flat rows retain source labels.
  Search/sort/dimension choices use the selected sources and preserve provenance.
- [ ] Test Combined is the initial configuration and creation defaults to Local; single-source
  views default to their source. Unavailable sources cannot become accidental save destinations.
- [ ] Test Local/Combined remain ready when the server is absent, incompatible, loading, or denied.
  Preserve the separate remote-browser toggle and its existing read-only behavior.
- [ ] Test captured selection routing after view changes, copied drafts, per-source visibility,
  profile changes while forms are open, and identical-coordinate markers.
- [ ] Test a Shared resync does not remove Local/Private render entries and a private tombstone
  removes only that private entry. Existing renderer `local`/waypoint `global` visibility flags
  retain their meaning. Renderer capacity limits must not cause unrelated stores to be mutated
  or lose saved data.
- [ ] Run tests to establish failures; compose existing query results and marker instances without
  creating a merged mutable store. Add source labels, destination/profile controls, and Copy.
- [ ] Follow existing dropdown popup layering/hover suppression and semantic theme roles; add
  applicable client locale keys and update the GUI guide in the same task.
- [ ] Run manager, query, form, renderer, and existing map-integration tests on active Fabric.
  Expected: new collection behavior and existing Shared integration tests pass.
- [ ] Review constrained-layout/input changes before live validation.
