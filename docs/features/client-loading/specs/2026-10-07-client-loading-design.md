# Client loading fixes

The 2026-10-07 client-loading report identifies a missing `message_chunk` client handler on
NeoForge 21.7.25-beta, a JECharacters transformer crash on a custom Fabric 1.21.10 profile,
and language discovery closing a loader-owned JAR filesystem. Fabric 26.2 reached OptionsScreen,
so its title-screen readiness remained inconclusive.

Split NeoForge releases at Minecraft 1.21.7: retain the existing registration on 1.21.6 and
use the separate client/server registration overload on 1.21.7–1.21.8. Open an independent
filesystem for language enumeration, closing only that filesystem. Update the incompatible
JECharacters client installation to a published Fabric 1.21.10 release, retaining a backup.

Use Java 17-compatible source, preserve existing Stonecutter branches and active targets,
do not commit, and retain original failure evidence. Startup validation stops at the title
screen with a 1024M heap; it does not validate gameplay or client/server communication.
