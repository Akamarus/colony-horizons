# Arcology Stable 1–5 design

Status: isolated generated Stable1-5 candidate; five independent static tests and seven scoped native cases passed (2026-10-11). Native run d60e136cfa8c4b468891725462c66baa exited0. Candidate .setup/arcology-stable-testpack contains288 blueprints and remains separate from the main283-blueprint repair pack. Separate native builder level1 construction and upgrades through5 passed in run2a9865dc5405465390bb315f960cd72b (five cases, exit0) using the291-asset additions snapshot. This is not full native-ready acceptance. Coordinates below are local blueprint coordinates before rotation/mirroring.

## Purpose and chosen form

Add `military/stable1.blueprint` through `stable5.blueprint` in the existing Arcology style. Keep one anchor, footprint, entrance, floor elevation, and circulation layout throughout upgrades. Support native horse training, roundup, maintenance, breeding, and cavalry movement without production AI patches or teleport recovery.

Choose a **15 × 6 × 20** shell (X × Y × Z): three by four five-block horizontal modules, with the familiar six-block vertical envelope. A 10 × 10 animal-hut conversion is smaller but leaves too little comfortable space for ten mounts at level 5 and the staff area. A 15 × 15 barracks conversion inherits unrelated nested huts and competes with ten bays for circulation. The proposed extra five-block module provides explicit horse space and avoids either compromise. This footprint is a design choice, not a native minimum.

## Native requirements and limits

| Native fact | Blueprint or acceptance consequence |
| --- | --- |
| `ModBuildingsInitializer` registers `stable` with `ModBlocks.blockHutStable`, `BuildingStable`, cavalry work, BED, stablemaster work/herding, guard list, settings, stock, and statistics modules. | Use the real stable hut and stable schematic identity. Do not retain barracks, barrackstower, archery work markers, or donor hut metadata. |
| `BuildingStable.stallPositions()` reads exactly `stall`; missing tags warn, and `getNextStallPosition()` returns null. Otherwise positions rotate round-robin. | Store reachable `stall` positions in the building's tag map. These are destinations, not assigned pens or a containment system. |
| `StableCavalryBuildingModule` counts its own assigned citizens; the producer uses building level as its maximum. Stablemaster work has maximum one. | Expected staffing at levels 1–5: one stablemaster plus 1, 2, 3, 4, or 5 cavalry. |
| Stablemaster training and breeding compare managed mounts with `2 * building level`. Herding compatibility is `Horse`; food is two golden apples. Butchering chance is zero. | Design room for 2, 4, 6, 8, and 10 managed mounts. This is an AI target, not a hard physical occupancy cap. Donkeys/mules found during roundup do not establish the same breeding/training support. |
| Conversion selects a living adult compatible animal with no passengers, excluding an existing cavalry horse; native conversion sets its home building and increments horses-trained statistics. | Acceptance must observe real conversion from a disclosed initial vanilla horse; placing a cavalry horse proves neither training nor conversion. |
| Roundup searches nearby `AbstractHorse` within an actual radius of 30 around the hut, with reservation/rider/home exclusions. It needs a lead and leads horses toward a stall. | Supply a lead through the hut and provide an unobstructed walking route from outside. The source comment saying 20 does not match the implementation. |
| `CavalryHorseEntity.createNavigation` disables entering doors, entering gates, and opening doors. | No closed gate or door may be essential to the horse route. A citizen opening one successfully does not demonstrate independent horse access. |
| `ReturnToStableGoal` navigates to the hut, then a stall; it has distance/reservation checks and a 6,000-tick post-dismount delay. Missing stall ends that sequence. | Returning-home tests must arrange genuine native prerequisites and allow the delay. Merely seeing a horse inside the building does not prove it reached a stall. |
| Stable patrol targeting prefers `patrol_point`, then parent handling, then `groundlevel`/hut-below fallback and corners. | Provide an explicit reachable `patrol_point` to avoid an accidental wall corner target. `groundlevel` is useful fallback metadata, not a substitute for horse clearance. |
| BED registration derives bed-head positions from actual placed bed blocks. | Use complete correctly oriented beds, not invented `bed` tags. Native staffing limits are independent of the number of decorative beds. |

Other operational supplies: the stablemaster's extra tool is a lead; maintenance feeding matches `ModTags.feed`, readiness matches `ModTags.leather`, and leather horse armor has a special readiness path. Do not substitute an invented stable feed tag or treat decorative hay as an inventory supply. Initial horse age/taming/love eligibility must be disclosed in future tests; conversion and vanilla horse mating rules differ.

## Exact proposed layout

Floor surface is y=0; normal standing cells start at y=1. Roof occupies y=5. Reserve y=1..4 as clear air over all horse circulation cells. Outer walls are x=0/14 and z=0/19, except the entrance. Use full solid floor blocks on horse routes, without carpet, trough edges, water, slabs, or stairs changing their collision height.

| Element | Local coordinates / rule |
| --- | --- |
| Main entrance | z=0, x=5..9, y=1..4; permanently open portal, no gate. Connect to an equally level, at least three-block-wide outside route. |
| Main aisle | x=5..9, z=1..18, y=1..4 clear. Five-block width allows opposing movement and turning margin. |
| West bays | x=1..4, with five depth bands z=4..6, 7..9, 10..12, 13..15, 16..18. |
| East bays | x=10..13, same depth bands. Each bay is four by three clear floor cells, open directly to the aisle. |
| Stall destinations | Pair at `(2,1,z)` and `(11,1,z)` for z=5, 8, 11, 14, 17. Activate the first L pairs at level L; keep all coordinates unchanged on upgrades. |
| Front service strip | z=1..2 outside the central aisle; z=3 stays clear for access. |
| Stable hut / anchor | `(12,1,1)`, facing south toward z=2 access. Exactly one stable hut at every level. |
| Supply rack | `(13,1,1)`, with accessible front at z=2. More storage may occupy wall recesses only after collision review. |
| Beds | Use **north-facing** beds with heads `(x,1,1)`, feet `(x,1,2)`. Choose x=1,2,3,4,10,11, enabling the first L+1 complete beds at level L. Six at L5 includes one spare beyond cavalry count. |
| Patrol destination | `(7,1,1)`, centered on the clear entrance route. |
| Ground-level tag | `(7,0,1)` on the floor plane. |

All bay floors exist at L1; progressive markers and beds make upgrades meaningful without building walls through existing mounts. Use flush floor accents to suggest stalls. Do not install cross-aisle fences or gates. Open bays are deliberate: native `stall` tags do not stop animals wandering, and escape/return behavior must be measured in acceptance rather than assumed to be solved by appearance.

## Tag serialization and clearance

Use the hut's `blueprintDataProvider.posTagMap`; each `tagPos` is relative to the containing tile entity, not absolute blueprint coordinates. For anchor `(12,1,1)`, stall offsets are `(-10,0,z-1)` and `(-1,0,z-1)`, patrol offset `(-5,0,0)`, and groundlevel offset `(-5,-1,0)`. Set corners relative to that anchor to `(-12,-1,-1)` and `(2,4,18)`, covering local `(0,0,0)` through `(14,5,19)`. Set schematic names `stable1`…`stable5` and the pack/path using the generator's normal conventions. Do not copy stale donor tag-provider corner values. Existing archery1's hut at `(7,1,7)` demonstrates relative `work` offset `(-2,0,-1)` pointing at `(5,1,6)`; retain the serialization convention, not that work tag.

Verified vanilla `EntityType.HORSE` dimensions in local Minecraft sources are width **1.3964844**, height **1.6**. Native cavalry registration uses width **0.70**, height **1.6**. Do not size the stable around the narrower custom entity: incoming vanilla horses still need space, and a rider needs additional headroom. Two clear blocks are only a geometric lower bound for an unmounted vanilla horse; this design uses at least three-block route width, four clear vertical cells, five in the central aisle, and four-by-three bays. These margins are design choices; mounted collisions and pathfinding remain runtime acceptance obligations.

Lights and beams belong in perimeter walls or the roof plane; no hanging lantern may protrude into the reserved horse volume. A visible stable sign and hay accents may use the front wall/service area, outside beds and circulation.

## Measured Arcology palette

Actual blueprint NBT measurements: archery1–5 are 10 × 6 × 10 with anchor `(7,1,7)`; barracks1/5 are 15 × 6 × 15 with that anchor; cowboy1/5 and shepherd1/5 are 10 × 6 × 10 with anchor `(7,1,2)`. These support the five-block horizontal module and six-block shell; they do not impose a native stable size.

Archery1/2 primarily use spruce planks, oak planks, oak slabs and spruce stairs. Archery3/4 switch to stone, cobblestone, cobblestone slabs and stone stairs while retaining some spruce trim. Archery5 uses beige stone bricks, stone bricks, mossy/cracked accents and Domum slab components. Follow those same level families: L1/2 timber, L3/4 stone/cobble, L5 beige/stone-brick finish. Copy full blockstate/material metadata for Domum pieces from measured donor cells; a block identifier alone is insufficient. Preserve the floor/roof module lines, red beds, wall lighting, and limited weathering. Do not add foreign roof pitches or timber palettes.

## Required verification before calling this native-ready

1. Validate all five files: unique correct hut, fixed anchor and bounds, 2L unique in-bounds stall tags, patrol and groundlevel tags, L+1 complete beds, valid palette references and Domum material metadata. Check rotation/mirror transforms and upgrade tag preservation.
2. Check collision volumes and connected floor routes for every stall, hut, rack, entrance and bed. Include vanilla-width horses and mounted cavalry, not only a walking citizen or a block-air flood fill.
3. In an isolated native run with recovery disabled, observe lead pickup and roundup to a stall, adult vanilla-horse conversion plus native statistics/home association, and movement without fixture gates or post-start AI mutation. Keep original world/pack untouched.
4. Separately observe native breeding with disclosed eligible animals and golden-apple consumption, and appropriate feed/readiness consumption. Do not claim those operations from training alone.
5. Exercise real cavalry assignment, mounting, travel to another reachable native stable/gatehouse patrol target, and return after native prerequisites. Record movement, identity, delay, stall destinations and stuck diagnostics. One static prebuilt placement is not upgrade/build validation; run a native builder upgrade sequence separately.

Source map: `apiimp/initializer/ModBuildingsInitializer.java`; `apiimp/initializer/EntityInitializer.java`; `core/colony/buildings/workerbuildings/BuildingStable.java`; `core/colony/buildings/modules/{BuildingModules,StableCavalryBuildingModule,BedHandlingModule}.java`; `core/entity/ai/workers/production/herders/EntityAIWorkStablemaster.java`; `core/entity/ai/cavalry/ReturnToStableGoal.java`; `core/entity/other/cavalry/CavalryHorseEntity.java`; `api/util/constant/SchematicTagConstants.java`. Vanilla dimensions come from `net/minecraft/world/entity/EntityType.java` in `build/moddev/artifacts/neoforge-21.1.250-sources.jar`. The generated candidate passed all-five-level native stall/bed/patrol registration plus level5 conversion and worker-leashed stall delivery. Mounted movement, breeding, maintenance, return, rotation and restart remain unverified. Native construction/upgrades are separately accepted below.



## Accepted isolated evidence

[Native report](evidence/arcology-native-stable.json): five layout cases registered2L stalls andL+1 bed heads; level5 stablemaster converted one ordinary untamed adult horse, removed the original, registered the cavalry home, increased native training statistics/XP, and completed one worker-leashed delivery within2.5blocks of a stall with roundup statistics. Prebuilt structures, horse, tools/feed, worker food and daytime were supplied; recovery disabled. This does not establish outdoor collection, every-stall access or long-term containment. Five independent static tests passed (.setup/stable-static-tests.log), including exact geometry, typed NBT, deterministic bytes, source preservation and mutation rejection. [Builder evidence](evidence/arcology-native-stable-build.json) separately passed five actual stages (level1 construction, upgrades2-5), run2a9865dc5405465390bb315f960cd72b, exit0. All291 immutable snapshot assets match .setup/arcology-additions-testpack; this is separate from the main283 pack. Same stablemaster assigned after level1 and paused through upgrades retained identity/hut/module membership; exactly17 sentinel shards persisted across worker plus own stable/racks. Native pause deposits explain the earlier fixed-slot failure ([diagnosis](evidence/arcology-stable-paused-deposit.json)); only the assertion was corrected. Worker-carried inventory and active stablemaster work during construction are not certified.

## Current complete-pack acceptance

The final candidate is .setup/arcology-horizons-complete (305 assets). Native mounted run017144ef70d648949aa649659ab1d5c1 passes mounting, exterior patrol and return into the central aisle. Its vanilla starter is initially tethered; the native stablemaster converts it and the fixture waits for combat readiness before untying it and assigning a cavalry guard. Tools/feed/leather/horse armor are initial supplies; native mounting and patrol are not injected, and recovery is disabled. All five stable levels also pass native interaction-range approaches/returns under all eight rotations/mirrors in the160-case added-route suite. This does not establish mounted access to every stall, combat, breeding, crash recovery or long-term containment.
