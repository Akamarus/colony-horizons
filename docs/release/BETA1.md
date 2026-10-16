# Colony Horizons 0.1.0-beta.1

First beta candidate for Minecraft 1.21.1 / NeoForge 21.1.250.

## Included since public alpha.2

- Exploration Hut with real biome/resource discovery and protected simulated gathering,
  native courier pickup, repeat trips, stock targets and five Arcology blueprints.
- Searchable recipe catalog: teach supported recipes without items in the player's
  inventory, choose ingredient variants, and retain native hut-level/profession limits.
- Prevent catalog duplicates of equivalent manually learned recipes, including disabled
  recipes. Existing duplicate entries are not automatically deleted.
- Preserve completed attached guard towers during Arcology Barracks upgrades.
- Seal water during Arcology Mine shaft construction, in addition to excavation sealing.
- Matching 310-blueprint Arcology Horizons pack, including Stone Smeltery availability.

## Supported scope and known limits

Exploration, recipe teaching and documented Arcology fixes are the beta scope.
Multi-building outposts, Mayors and inter-settlement transport remain paused, with
legacy code/save handling retained. Their completion is not a stable-release requirement.
Ars integration remains deferred.

Back up the entire instance before updating. Install one addon JAR and the matching
blueprint ZIP. Normal materials, food, tools, research and production costs apply.
Existing structures require repair/rebuild to adopt blueprint repairs.

Controlled regression tests passed for recipes, native crafting/smelting, Barracks,
Exploration/couriers, restart and miner water handling. A copy of the reported flooded
colony completed its next shaft level using finite replacement tools and dirt.
Public alpha.2 load/save/reload preserved 18 buildings, 9 citizens and 170 inventories.
These tests do not certify arbitrary modpacks, multiplayer, power-loss recovery or
indefinite endurance. See the release candidate verification report for exact scope.

## Credits

Original Arcology by BlockyUniverseProductions; adaptation published with permission.
Repairs and extensions by Akamarus. Addon code GPL-3.0-only; Arcology assets are
separately permitted and are not GPL. This is an unofficial MineColonies addon.
