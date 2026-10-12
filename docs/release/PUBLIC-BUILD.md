# Public addon build

The code is GPL-3.0-only; see LICENSE and NOTICE. Third-party Arcology assets are
not included or licensed by this project. See TEMPLATE_LICENSE.txt for MDK terms.

Requirements: Java 21 and network access to Gradle/NeoForge/LDTTeam repositories.
On Windows run `gradlew.bat publicJar`; on Unix run `sh gradlew publicJar`.
The production JAR is built under build/public-libs. `publicSourceZip` creates the
matching source snapshot under build/public-libs. The normal `build` task in the
full development repository includes QA; use publicJar for distribution.

Install the public JAR into mods alongside Minecraft 1.21.1 / NeoForge 21.1.250,
MineColonies 1.1.1403-1.21.1, Structurize 1.0.832-1.21.1-snapshot,
BlockUI 1.0.199-1.21.1-snapshot, Domum Ornamentum 1.0.223-snapshot and
Multi-Piston 1.2.51-1.21.1-snapshot. Obtain dependencies from their official sources.
Their sources are available at github.com/ldtteam under the corresponding
minecolonies, structurize, blockui, domum-ornamentum and multipiston repositories.
Build coordinates pin the exact dependency versions.

This alpha supplies compatibility fixes. The separately distributed Arcology Horizons pack requires its own installation.
No style pack, automated outpost
founding, Mayor UI or player-facing supply routes are bundled. Test in a new
world first. Multiplayer is not certified. Optional Amendments integration was
previously tested at 2.0.5 with Moonlight 2.23.8.

Corresponding source must remain available alongside each distributed binary;
ship this source ZIP with the binary download or provide equivalent access at
the same release location. Preserve notices on modifications and dependencies.
