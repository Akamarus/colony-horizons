# Installed Arcology pack audit — 2026-10-10

Arcology is a strong base for our modular settlements, but this installed copy is not complete for our pinned MineColonies version. It contains **48 building types / 238 building blueprints**, plus **45 infrastructure and supply blueprints**. The most urgent work is repairing worker markers and two material dependencies, followed by newer crop layouts and missing buildings.

## Scope and confidence

Audited the actual user-installed folder:
`blueprints/arcology-horizons`.

Pack metadata: **Arcology**, author **BlockyUniverse**, version **1**, pack-format **1**. The metadata has no dated release identifier. Of 283 blueprints, 278 carry Minecraft data version 3120 and five Kitchen blueprints carry 3465. This is an older, mixed-version asset set; a metadata version of 1 does not identify a current MineColonies release.

Comparison target: the addon's **Minecraft 1.21.1 / MineColonies 1.1.1403 / Structurize 1.0.832** dependencies. The CurseForge instance containing this pack instead has **MineColonies 1.20.1-1.1.835** and Blocks You Need Luna 1.6.5 for 1.20.1 installed. Therefore an extra-mod dependency can work in that instance while being absent from our addon development profile. This audit does not claim this is the latest Arcology release available online.

All 283 files parsed successfully as compressed NBT, with valid block-array lengths, palette indexes and primary offsets. Every file is hashed in the [full inventory](evidence/arcology-full-inventory.csv); [summary data](evidence/arcology-full-audit.json) records building coverage, tags and block usage. The 25 files from the earlier native-loader test still match their recorded hashes.

**Static inspection is not full gameplay certification.** The previous native-loader checks covered five building types at levels 1–5; the previous construction/worker tests covered a six-building layout and forestry. This audit did not construct and operate all 48 building types or test every upgrade, mirror, rotation and stacked connection. Marker absences below are verified asset facts; their effects on an existing upgraded save can depend on retained building state.

## 1. Repair existing blueprints first

| Priority | Finding | Why it matters / action |
| --- | --- | --- |
| High | `fundamentals/miner3.blueprint` through `miner5.blueprint` lack the `ladder` and `cobble` position tags. Levels 1–2 have both. | Native `BuildingMiner.loadLadderPos()` uses those tags; its worker reports an invalid mineshaft if the locations cannot be resolved. Restore markers at the correct shaft coordinates, then test construction, upgrades and reloads. Do not infer that an existing miner's cached coordinates prove these blueprints are correct. |
| High | `agriculture/horticulture/plantation3.blueprint` has no crop position tags. Levels 1–2 and 4–5 contain `sugar`, `cactus` and `bamboo`. | Native placement/upgrade code derives legacy plantation fields from these positions. Level 3 can lack the information needed to create those fields. Restore its intended crop layout and test the upgrade transition. |
| High | `mystic/netherworker2.blueprint` through `netherworker5.blueprint` lack `portal` and `vault` tags present at level 1. | Native portal lookup returns null without a portal tag; Nether worker code explicitly aborts when the portal tag is missing. Restore and validate the markers, especially portal placement and worker access. This is native MineColonies Nether content, not Ars integration. |
| High for our addon profile | `craftsmanship/luxury/foodprep5.blueprint` actually places **one** `blocksyouneed_luna:steel_bent_pipe`. | Blocks You Need Luna is installed in the original CurseForge instance but not in our pinned addon profile. Prefer replacing this decorative block in a separate compatibility copy. Do not assume the entire Kitchen is missing: its hut is the native Kitchen and all five levels exist. |
| Medium | `infrastructure/pathways/wood/wooden walkway.blueprint` places **14 block positions** of `minecraft:petrified_oak_slab`. | The loaded recipe audit contains no recipe or teachable candidate mentioning this item. It is a survival-material risk, not a parser error. Replace with an ordinary obtainable slab after checking the design. This is a block-position count, not a builder bill of materials. |
| Check during validation | Archery levels 2–4 lack the `work` tags present at levels 1 and 5. | The native Archery can fall back to separately registered shooting stands, so this is not enough to declare it broken. Validate training positions and targets at every level. |

The pack-level `mods` list names only `structurize`; individual blueprints also declare MineColonies, Domum Ornamentum and, in the Kitchen above, Blocks You Need Luna. Dependency metadata should be made explicit when preparing a compatibility copy. All other non-vanilla block names used by the pack have corresponding items in our captured runtime registry; this does not validate every block-state property or embedded material component.

## 2. Missing native building styles

The pinned registry has 54 entries. Arcology covers 48. Two of the six absent entries are standalone utility blocks, leaving **four actual missing building types**:

| Building | Native levels needed | Effect |
| --- | --- | --- |
| Gatehouse | 1–3 | No matching Arcology terminal for the built-in colony connection system. Relevant to our main-colony/outpost plans. Adding a Gatehouse alone does not implement automatic item transport. |
| Stable | 1–5 | No Arcology home for native stablemaster/cavalry functionality. Its entrances must accommodate horses. |
| Simple Quarry | 1 | No matching quarry structure. Include its associated shaft blueprint when designing it. |
| Medium Quarry | 1 | Same gap for the larger quarry, including its shaft structure. |

That is **10 missing building-level blueprints**, plus quarry support structures. Postbox and Stash are native standalone utility blocks and are **not missing five-level huts**. Large Quarry is commented out in this pinned building initializer and is not counted as a currently registered missing building.

The Tavern's levels 1–3 are complete for its native maximum. Kitchen is present under the filename `foodprep1`–`foodprep5`; Residence is present under `citizen1`–`citizen5`. Warehouse and Enchanter use specialized block entities; they are present and must not be misclassified by looking only for generic colony-building entities.

## 3. Missing resource-growing layouts

The pack has an older integrated Plantation layout for **sugar cane, cactus and bamboo**. The pinned MineColonies code retains a migration/placement path for these three legacy tagged layouts, so their age alone is not a defect. The missing level-three tags above are a separate problem.

There are **no Arcology extension layouts/markers** for these ten additional native plantation categories:

- Cocoa beans and vines.
- Kelp, seagrass and sea pickles.
- Glow berries.
- Weeping vines and twisting vines.
- Crimson plants and warped plants.

These are crop extensions, not ten new worker professions. A present Plantation hut therefore does not establish that the style supplies layouts for every crop that the native Planter can grow. These gaps matter directly to automated cross-biome resource outposts. Fields from another style or suitable manually created layouts may fill a gameplay need, but would not make Arcology itself complete.

The three supplied `infrastructure/fields/* field.blueprint` files are ordinary crop-field pieces, not replacements for this full set of specialized plantation extensions.

## 4. Support pieces and modular assembly

The 45 non-building assets consist of:

| Content | Count |
| --- | ---: |
| Supply camp and supply ship | 2 |
| Ordinary fields | 3 |
| Pathways, stairwells, walkway entrances and walkways | 12 |
| Roof pieces | 4 |
| Frames in wood/stone/brick and four size groups | 12 |
| Walls, corners, entrances and windows | 12 |

Arcology already has connecting pieces; it is **not missing stairs and corridors altogether**. What our automated Mayor still needs is a validated layout catalog: connection faces, walking clearances, stair direction, compatible floor heights, foundations, and reservations for each building's largest upgrade envelope. Those are addon integration requirements, not proof the author made a faulty pack. No claim of safe arbitrary stacking follows from matching dimensions alone.

There are no Arcology-specific `infrastructure/mineshafts/` templates for the eight native miner segments: main shaft, straight tunnel, four-way junction, left/right bends and three variants of three-way junction. **This is not automatically a mining blocker:** pinned `WorkOrderMiner.loadBlueprint()` explicitly falls back to the `Minecolonies Original` pack. The Mine's missing shaft-position tags still need repair independently. Native mining with that fallback has not been exercised in this audit.

No addon Mayor office, protected-delivery depot or addon-specific outpost starter composition exists in this pack. Those are features we are planning, not omissions from native MineColonies coverage.

## 5. Recommended order for our project

1. Create a separate Arcology compatibility copy; preserve the installed original. Repair Mine, Plantation and Nether Mine markers, replace the extra-mod pipe and petrified slabs, and validate the affected upgrades in-game.
2. Add a Gatehouse that fits Arcology's grid, with usable approaches and native terminal metadata.
3. Add the ten missing native plantation extension layouts, starting with the biome resources needed by our first outposts.
4. Add Stable and both Quarries with their required support pieces.
5. Validate a small set of connected settlement layouts for automatic placement, including elevation, walking access and upgrades.

A separate new visual style or a wholesale rewrite of the existing 48 building types is not required to address these findings. This audit changes no installed pack files and implements none of the proposed repairs.

## Evidence and reproduction

Run with Python 3 and an extracted source archive matching the pinned MineColonies dependency:

```powershell
python tools/Audit-ArcologyPack.py --pack "blueprints/arcology-horizons" --source .setup/minecolonies-audit/source --output docs/outposts/evidence
```

The audit uses only Python's standard library. It checks the packed block format against the pinned Structurize conversion routine, identifies buildings by their primary-anchor metadata rather than filenames, and records actual used blocks rather than unused palette entries. No blueprint content or third-party assets are redistributed in these reports.

Primary local evidence: `ModBuildingsInitializer`, `ModBuildings`, `BuildingGateHouse`, `BuildingStable`, `BuildingMiner`, `BuildingPlantation`, `BuildingNetherWorker`, `EntityAIStructureMiner`, `EntityAIWorkNether`, `WorkOrderMiner`, `ModBuildingExtensionsInitializer`, and `SchematicTagConstants` in the extracted pinned sources. The inspected MineColonies binary matches the project's cached dependency, SHA-256 `c8d4373ffc8a76638c8edf2bb6716f9209599032209c48d0d559b787f69a0561`.

Useful public references: [official style-pack format](https://minecolonies.com/wiki/tutorials/schematics/style-packs/), [official schematic design guidance](https://minecolonies.com/wiki/tutorials/schematics/), and [native Stable requirements](https://minecolonies.com/wiki/buildings/stable/). The installed files and pinned source take precedence over newer wiki descriptions in this audit.

## Complete building inventory

All 47 non-Tavern families below have files for levels 1–5; Tavern has levels 1–3. Presence does not override the functional warnings above.

| Native building ID | Blueprint family | Levels | Bounds across levels (X×Y×Z) |
| --- | --- | --- | --- |
| alchemist | craftsmanship/luxury/alchemist | 1, 2, 3, 4, 5 | 5x6x10 |
| archery | military/archery | 1, 2, 3, 4, 5 | 10x6x10 |
| baker | craftsmanship/luxury/baker | 1, 2, 3, 4, 5 | 5x6x5 |
| barracks | military/barracks | 1, 2, 3, 4, 5 | 15x6x15 |
| barrackstower | military/barrackstower | 1, 2, 3, 4, 5 | 5x6x5 |
| beekeeper | agriculture/husbandry/beekeeper | 1, 2, 3, 4, 5 | 5x6x10 |
| blacksmith | craftsmanship/metallurgy/blacksmith | 1, 2, 3, 4, 5 | 10x6x10 |
| builder | fundamentals/builder | 1, 2, 3, 4, 5 | 5x12x5 |
| chickenherder | agriculture/husbandry/chickenherder | 1, 2, 3, 4, 5 | 5x6x10 |
| combatacademy | military/combatacademy | 1, 2, 3, 4, 5 | 10x6x10 |
| composter | agriculture/horticulture/composter | 1, 2, 3, 4, 5 | 5x6x5 |
| concretemixer | craftsmanship/luxury/concretemixer | 1, 2, 3, 4, 5 | 5x6x5 |
| cook | fundamentals/cook | 1, 2, 3, 4, 5 | 5x6x10 |
| cowboy | agriculture/husbandry/cowboy | 1, 2, 3, 4, 5 | 10x6x10 |
| crusher | craftsmanship/masonry/crusher | 1, 2, 3, 4, 5 | 5x6x5 |
| deliveryman | craftsmanship/storage/deliveryman | 1, 2, 3, 4, 5 | 5x6x5 |
| dyer | craftsmanship/luxury/dyer | 1, 2, 3, 4, 5 | 5x6x5 |
| enchanter | craftsmanship/luxury/enchanter | 1, 2, 3, 4, 5 | 5x12x5 |
| farmer | agriculture/horticulture/farmer | 1, 2, 3, 4, 5 | 5x6x5 |
| fisherman | agriculture/husbandry/fisherman | 1, 2, 3, 4, 5 | 5x6x12 |
| fletcher | craftsmanship/carpentry/fletcher | 1, 2, 3, 4, 5 | 5x6x5 |
| florist | agriculture/horticulture/florist | 1, 2, 3, 4, 5 | 10x6x10 |
| glassblower | craftsmanship/luxury/glassblower | 1, 2, 3, 4, 5 | 5x6x5 |
| graveyard | mystic/graveyard | 1, 2, 3, 4, 5 | 10x6x23 |
| guardtower | military/guardtower | 1, 2, 3, 4, 5 | 5x6x5 |
| hospital | fundamentals/hospital | 1, 2, 3, 4, 5 | 10x6x10 |
| kitchen | craftsmanship/luxury/foodprep | 1, 2, 3, 4, 5 | 5x6x10 |
| library | education/library | 1, 2, 3, 4, 5 | 5x6x10 |
| lumberjack | fundamentals/lumberjack | 1, 2, 3, 4, 5 | 5x6x9 |
| mechanic | craftsmanship/metallurgy/mechanic | 1, 2, 3, 4, 5 | 10x6x10 |
| miner | fundamentals/miner | 1, 2, 3, 4, 5 | 10x7x10 |
| mysticalsite | mystic/mysticalsite | 1, 2, 3, 4, 5 | 5x6x5 |
| netherworker | mystic/netherworker | 1, 2, 3, 4, 5 | 5x6x10 |
| plantation | agriculture/horticulture/plantation | 1, 2, 3, 4, 5 | 15x6x15 |
| rabbithutch | agriculture/husbandry/rabbithutch | 1, 2, 3, 4, 5 | 5x6x10 |
| residence | fundamentals/citizen | 1, 2, 3, 4, 5 | 5x6x10 |
| sawmill | craftsmanship/carpentry/sawmill | 1, 2, 3, 4, 5 | 5x6x10 |
| school | education/school | 1, 2, 3, 4, 5 | 15x6x15 |
| shepherd | agriculture/husbandry/shepherd | 1, 2, 3, 4, 5 | 10x6x10 |
| sifter | craftsmanship/masonry/sifter | 1, 2, 3, 4, 5 | 5x6x5 |
| smeltery | craftsmanship/metallurgy/smeltery | 1, 2, 3, 4, 5 | 5x6x10 |
| stonemason | craftsmanship/masonry/stonemason | 1, 2, 3, 4, 5 | 5x6x5 |
| stonesmeltery | craftsmanship/masonry/stonesmeltery | 1, 2, 3, 4, 5 | 5x6x10 |
| swineherder | agriculture/husbandry/swineherder | 1, 2, 3, 4, 5 | 10x6x10 |
| tavern | fundamentals/tavern | 1, 2, 3 | 15x6x15 |
| townhall | fundamentals/townhall | 1, 2, 3, 4, 5 | 15x12x15 |
| university | education/university | 1, 2, 3, 4, 5 | 10x6x10 |
| warehouse | craftsmanship/storage/warehouse | 1, 2, 3, 4, 5 | 15x6x15 |
