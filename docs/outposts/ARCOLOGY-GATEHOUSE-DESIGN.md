# Arcology Gatehouse 1–3 design

Source-backed design for approved missing-building work, 2026-10-11. Targets pinned MineColonies 1.1.1403-1.21.1. Candidate generation is isolated from both the original pack and `.setup/arcology-horizons`; static checks are not native operational acceptance. This adds a native terminal building only, with no new transport implementation.

## Native contract

All source paths below are beneath `.setup/minecolonies-audit/source/com/minecolonies/`.

- `core/blocks/huts/BlockHutGateHouse.java`: real hut block is `minecolonies:blockhutgatehouse`. `BuildingGateHouse` uses schematic name `gatehouse` and maximum level 3. Its equivalent military levels are 1, 3, 5; claim-radius results are 1, 1, 2 respectively.
- `apiimp/initializer/ModBuildingsInitializer.java` registers the gatehouse with knight/ranger guard work, guard list/settings, minimum stock, BED, statistics, and colony connections. Use `minecolonies:gatehouse` as the hut's building type and `minecolonies:colonybuilding` as its tile identity.
- `BuildingGateHouse.getGuardPos` requires at least **two `knight` and two `archer` tags**, even when one role is unstaffed. It selects a position by the worker's index in that role's module; missing tags fall back to the hut and log an error. Encode both exact positions. Native tag caches use sets, so serialized marker order does not determine the returned list order.
- `BuildingModules` gives both gate guard modules a size of two, while `GuardBuildingModule.isFull()` compares the building's **total** assigned citizens against that limit. This is two guards total, not two of each. Two real beds are appropriate; no `bed` marker is required. `BedHandlingModule` registers placed bed heads.
- Gatehouse task is always `guard`; settings only expose guard mode. The layout must support standing posts, not rely on patrol mode being enabled for its own guards.
- `BuildingGateHouse.setBuildingLevel` registers the hut position with the native connection manager at level >=1. `destroy` removes it. `onUpgradeComplete` informs the guard building manager. No special blueprint route marker or saved connection NBT replaces those callbacks.
- `TeleportToColonyMessage` uses the hut's cached world `gate` tags as player-arrival search origins, falling back to the hut if absent. `TeleportHelper.colonyTeleport` searches nearby for two air cells, without a support predicate in that method. Provide supported clear arrival space and validate actual arrival; the marker alone does not prove safety.
- Native stable cavalry chooses reachable `patrol_point` tags on a target gatehouse before fallback corners; include one clear target and a `groundlevel` fallback. These support the existing cavalry consumer, not a new transport system.
- Native colony signs and connection nodes use 50-block proximity and asynchronous path validation. Player connection travel, permissions and diplomacy remain native concerns. This blueprint does not establish a route or implement courier cargo transfer. See [native connection audit](NATIVE-CONNECTIONS-AUDIT.md).

## Chosen geometry

Use a fixed **15 × 6 × 10** shell, three by two five-block modules. Floor y=0, standing y=1, roof y=5. Central through-passage x=5..9 spans z=0..9 with four clear air cells y=1..4. Both ends remain open; there is no functional gate/door across this route. The passage also accommodates wider incoming vanilla horses and mounted cavalry. A five-wide opening is a design margin, not a native gatehouse minimum.

A 10-wide layout would constrain the two side rooms around a useful horse route; a taller guard tower adds stairs and a separate accessibility problem. The low 15-wide gatehouse fits the existing six-block Arcology storey and keeps all markers directly accessible on one floor. Side openings preserve archer sightlines without inventing an elevated-position requirement.

| Element | Local position / rule |
| --- | --- |
| Anchor/hut | `(12,1,6)`, facing north toward clear service cell `(12,1,5)` |
| Rack | `(13,1,6)`, facing north, front `(13,1,5)` |
| Knight posts, in order | `(6,1,1)`, `(8,1,1)`; offset from centre lane |
| Archer posts, in order | `(2,1,2)`, `(12,1,2)`; clear lines toward front windows |
| Player arrival `gate` | `(7,1,5)`; flat supported central passage |
| Cavalry `patrol_point` | `(7,1,8)`; clear rear approach |
| `groundlevel` | `(7,0,5)`; floor elevation |
| Two complete beds | x=1 and x=12; feet `(x,1,7)`, heads `(x,1,8)`, facing south |
| Front sight openings | z=0, x=1..3 and x=11..13, y=2..3; one-block-high solid sill |
| Roof and floor grid | Full frame lines at x=0,5,10,14 and z=0,5,9; flush full-block floor throughout |
| Lights | Perimeter x=0/14, y=3, z=5; outside passage and guard destinations |

Interior x=1..13, z=1..8 remains open except the listed hut, rack and beds. No inner room partitions, trapdoors, carpets, troughs or partial-height thresholds are placed in the movement routes. Markers and furnishings stay fixed across all three levels. Exterior floor continuity beyond each entrance remains the settlement placement responsibility.

## Metadata and palette

Encode tag positions relative to the hut `(12,1,6)`: knights `(-6,0,-5)`, `(-4,0,-5)`; archers `(-10,0,-4)`, `(0,0,-4)`; gate `(-5,0,-1)`; patrol `(-5,0,2)`; groundlevel `(-5,-1,-1)`. The hut's `blueprintDataProvider` corners are `(-12,-1,-6)` and `(2,4,3)`, enclosing local `(0,0,0)` through `(14,5,9)`. Schematic names are `gatehouse1`, `gatehouse2`, `gatehouse3`, with matching root filenames and one correct primary offset. Never reuse donor `work` tags, hut identities, inventories, entities, or connection state.

Native equivalent levels justify the measured Arcology archery1/3/5 palettes: L1 oak/spruce and oak roof slabs; L2 stone/cobblestone and cobblestone roof slabs; L3 beige/stone-brick, limited mossy/cracked accents, and Domum roof slabs with their full material metadata. Original assets are metadata-schema and material references only. The generator constructs a fresh block array, new tile records and original geometry, not a copied third-party structure. Keep native NBT scalar/list/array types intact.

## Acceptance boundaries

Static contracts must validate all three levels, exact hut/type/anchor/bounds, independent tag sets and ordering, complete bed pairs, rack identity, supported arrival cells, clear through-route with a two-by-two horizontal horse envelope and four-cell headroom, guard accessibility and front sightlines, tile/state consistency, Domum material data, deterministic typed NBT, and unchanged original assets. Deliberate marker/route/bed corruptions must fail the contracts.

Native integration still requires an isolated run with recovery disabled:

1. Build or place through the normal native building path, verify loaded class/level, registered beds, world-transformed cached tags, and guards navigating from outside to their role's tagged posts. Test knight and archer variants separately, respecting the shared two-person cap. Observe actual work behavior, equipment requests and stable identity; static marker counts do not prove guarding.
2. Verify native level-1 registration, level-2/3 upgrade callbacks, persisted terminal registration after reload, and native removal on destruction. Do not seed a fabricated connection manager record.
3. Exercise arrival on a legitimate existing native connection with its permissions/diplomacy and supported destination. Verify actual landing collision/support because the native air search may choose near the marker. Connection establishment and player travel are separate acceptance from terminal registration.
4. Exercise real cavalry approach to the patrol marker and passage in both directions. Check rotated/mirrored variants and exterior joins; native pathfinding can fail even when an air-volume flood fill passes.
5. Run a native builder upgrade chain to establish buildability and metadata preservation. Prebuilt placement alone is insufficient. No courier transport or same-colony route implementation is included or implied.
