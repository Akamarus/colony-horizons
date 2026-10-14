# Colony Horizons 0.1.0-alpha.2

Bug-fix release prepared by Akamarus. Requires the same Minecraft 1.21.1,
Java 21, NeoForge 21.1.250 and MineColonies 1.1.1403 dependencies as alpha.1.

- Arcology Mine shaft excavation seals neighboring fluid before removing a plug,
  preventing the reported overhead-water dirt placement/breaking loop. It requests
  missing fill and preserves waterlogged structures and inventories.
- Separate Arcology Horizons pack: supply raft waterline metadata corrected.
- Builder levels 1-5: two safety rails prevent recall into trapped edge pockets.
- Simple and Medium Quarry dirt-inflow regressions passed; no new quarry code.

Install both the new addon JAR and separate blueprint ZIP to receive all fixes.
The 305-building-blueprint count is unchanged. No new supply-route or Mayor feature.

## Verification and limits
62 focused native test cases passed: Mine 6, Quarry 8, raft transformations 8,
and elevated builder recall/navigation 40. Packaged source reproduces the JAR
byte for byte. All previous binary entries are unchanged except version and mixin
registration; one new miner mixin class is added. Exactly six blueprints differ.
Fixtures test native actions under controlled conditions, not full autonomous
construction/mining journeys. Multiplayer remains unverified. The miner fix is
limited to Arcology Horizons shaft excavation; it does not cover every tunnel or
aquifer. Waterlogged structures may require manual drainage. Native ship placement
tolerance remains unchanged and can still accept some raised previews.

Original Arcology: BlockyUniverseProductions, used with permission.
https://www.planetminecraft.com/mod/arcology-5819547/
Addon code GPL-3.0-only; separately permitted Arcology assets are not GPL.

Normal Minecraft Launcher smoke test passed on 2026-10-14: startup, new world, Arcology selection, Builder's Hut level 1/5 previews, save/reload and exit code 0. No ERROR/FATAL events. The user confirmed Builder's Hut and Mine fixes were already tested in the preceding local development build.
