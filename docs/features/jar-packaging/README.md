# Jar packaging boundaries

Each release jar should ship only the classes and resources its platform uses. The cross-server
code that backends and the Velocity proxy share moves into its own `cross-server` module, so the
Velocity plugin stops packaging the waypoint core, backend translations, the chat-sprite table and
the translator credits. Backend jars stop packaging proxy-only classes, and the release gate checks
every jar's contents and internal class references.

Status: implemented on 2026-10-04. The release gate verifies all 42 jars.

- [Design](specs/2026-10-03-jar-packaging-boundaries-design.md)
- [Implementation plan](plans/2026-10-03-jar-packaging-boundaries.md)
- [Validation record](validation/2026-10-03-jar-packaging-boundaries.md)
