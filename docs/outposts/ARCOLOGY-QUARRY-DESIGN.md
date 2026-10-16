# Arcology Simple and Medium Quarry design

Status: source-backed design proposal, 2026-10-11. No quarry generator, blueprint, native acceptance or gameplay fix is delivered by this document. Scope is the pinned MineColonies 1.1.1403 / Minecraft 1.21.1 implementation. Large Quarry is excluded: its registration is commented out. Coordinates are unrotated local blueprint coordinates.

## Native identities and ownership

| Item | Simple | Medium |
| --- | --- | --- |
| Block and item registry identity | minecolonies:simplequarry | minecolonies:mediumquarry |
| Building registry/type | minecolonies:simplequarry | minecolonies:mediumquarry |
| Schematic family | simplequarry | mediumquarry |
| Native maximum building level | 1 | 1 |
| Building implementation | DefaultBuildingInstance | DefaultBuildingInstance |
| Assignment module | SIMPLE_QUARRY / QuarryModule(32) | MEDIUM_QUARRY / QuarryModule(64) |

These block names do not have a blockhut prefix. Registration is explicit in ModBuildingsInitializer.java:630-642 and ModBlocksInitializer.java:148-150; the runtime loot tables independently name the same block items. Both have one native construction level. Medium is a separate building, not level two of Simple; do not manufacture quarry levels2-5.

A quarrier is employed at a BuildingMiner through QUARRIER_WORK, not at the quarry as a conventional WorkerBuildingModule. The Mine registers both miner and quarrier work modules. The quarrier's primary/secondary skills are Strength/Stamina, with a one-worker producer limit and the Mine's combined hiring limit. The external QuarryModule also holds at most one assignment. JobQuarrier.findQuarry finds the building whose QuarryModule contains that citizen; assignTo explicitly permits that external module. Quarry auto-hiring looks for existing JobQuarrier citizens lacking another quarry. Tests must establish the native Mine job and external quarry assignment, then assert both relationships. Merely creating a quarry building or assigning a generic worker is insufficient.

The Mine owns progress, requests, tools and statistics; getBuildingToDump selects the quarry as the output destination. Provide accessible quarry inventory/racks and a usable route back to the Mine. Native mined-item statistics are recorded on the Mine, and blocks/ores mined on the colony. The quarry registers no separate worker/statistics module in the pinned initializer.

## Required shaft lookup and placement

Proposed new files:

- fundamentals/simplequarry1.blueprint
- fundamentals/mediumquarry1.blueprint
- infrastructure/mineshafts/simplequarryshaft1.blueprint
- infrastructure/mineshafts/mediumquarryshaft1.blueprint

Put exactly one origin tag on each quarry hut: shaft=infrastructure/mineshafts/simplequarryshaft1.blueprint or shaft=infrastructure/mineshafts/mediumquarryshaft1.blueprint. Serialize it in the hut tile's blueprintDataProvider.posTagMap with tagPos=(0,0,0). This is relative to the containing hut tile, not a blueprint-origin coordinate.

EntityAIQuarrier.getShaftPath (162-200) uses the quarry's structure pack. Without a tile it falls back to infrastructure/mineshafts/<schematic>shaft1.blueprint. With the tile it derives a path from the actual building blueprint path: replace 1.blueprint with shaft1.blueprint, otherwise replace .blueprint with shaft.blueprint. An explicit shaft= value containing '/' is pack-relative; a bare filename is resolved in the building's directory. A missing .blueprint extension is appended.

Pinned implementation trap: the tag loop has an unconditional break at line189. Only the first tag at BlockPos.ZERO is inspected. Make shaft= the sole origin tag; do not place an unrelated tag before it. Tags at other positions do not select the shaft. The proposed fundamentals and infrastructure directories therefore require the explicit path; otherwise the worker would look for fundamentals/simplequarryshaft1.blueprint.

loadRequirements creates a WorkOrderMiner at quarry.getPosition().below(2), using the quarry's rotation/mirror and the Mine as owner/claimant (142-147). This is one full shaft blueprint, not a repeating six-block slice. The shaft primary offset must align its upper infrastructure with the surface structure after this two-block displacement. Quarry code does not request the ordinary Miner's ladder/cobble markers; those remain requirements of the support Mine, not invented quarry tags. No stall, work or bed tags are required by this quarry AI.

The structure runs layer-by-layer from sizeY-1 down, with BUILD_SOLID, DECORATE and CLEAR stages. Decoration is applied on the layer above newly placed supports. Native progress is stored on the Mine; assignment/removal resets that progress. Completion calls QuarryModule.setFinished. Clearing at the world minimum build height can end the work early. A loaded shaft or finished flag alone does not prove the intended excavation volume was completed.

## Depth and ground constraints

The values32/64 above are QuarryModule footprint parameters, not enforced shaft size or excavation depth. Actual horizontal dimensions, depth and construction resources come from the selected shaft blueprint and its primary offset.

There is a separate pinned source concern to measure before relying on containment APIs: QuarryModule.getAdditionalCorners returns (0,+height,0) for the first corner. AbstractSchematicProvider.setCorners sorts lower/upper coordinates, and isInBuilding adds that positive offset to the lower corner. For a short surface building this can put the adjusted lower Y above the upper Y. Do not describe32/64 as a verified downward claim extension or enlarge decorative metadata to hide this behavior. Assert actual native corners, work-order coordinates, protection/claim behavior and excavation reach during acceptance; isolate any required gameplay correction as a reproduced defect.

Proposed depth conventions below deliberately choose32 and64 to express Simple/Medium scale; this is a design choice. Let H be the placed quarry hut Y and D the shaft sizeY. With shaft primary Y=D-2 and work-order anchor H-2, shaft local y maps to world H-D+y. Its bottom is H-D and its top is H-1. For untruncated acceptance require H-D greater than the world minimum build height, leaving a safety margin. Never assume a flat surface supplies32/64 blocks of rock beneath it.

Place the quarry at ground level over surveyed, claimed terrain. Reserve the entire shaft prism: no lower Arcology modules, roads, utilities, other buildings, protected inventories or player construction underneath. This is an excavation module, not a freely stackable tower room. Initial tests should use a disclosed dry stone volume with identifiable ore blocks and sufficient depth; cave, gravel/sand, aquifer/lava and bedrock interactions require separate coverage. Native doMining tries to fill neighboring fluids using the Mine's configured fill block and available inventory; that is not evidence of a waterproof or unattended quarry.

## Concrete Arcology surface layouts

These are original layouts, using measured local Arcology material families and module spacing, without copying another style's quarry geometry.

| Geometry | Simple | Medium |
| --- | --- | --- |
| Surface bounds X/Y/Z | 15/7/15 | 25/7/25 |
| Surface primary hut | (7,2,2), facing south | (12,2,2), facing south |
| Shaft bounds X/Y/Z | 15/32/15 | 25/64/25 |
| Shaft primary offset | (7,30,2) | (12,62,2) |
| Interior excavation rectangle | x3..11, z5..11 | x3..21, z5..21 |
| Interior size | 9 by7 | 19 by17 |
| Liner boundary | x2/12 and z4/12 | x2/22 and z4/22 |
| Ladder column | (3,y,5), east-facing | (3,y,5), east-facing |
| Ladder solid backing | (2,y,5) | (2,y,5) |

Surface origin Y is H-2. The deck floor is local y1 (world H-1); ordinary walking starts y2. This seven-block envelope matches the measured Arcology Mine's vertical envelope rather than pretending every asset is six blocks tall. The overall X/Z dimensions use the existing five-block module grid.

Use a north service strip z0..4 with a three-wide open entrance centered on the hut's X coordinate. Put the hut at the stated anchor, two accessible racks to its west/east outside the entrance route, and a five-by-five open-front service canopy with roof at y6. Preserve y2..5 clearance over working routes. Do not place an additional Mine hut in this blueprint: the employing Mine is a separate building.

Around the hole, keep a two-block outer deck where space allows. Put a waist-high rail along the liner's surface edge, except a two-block opening at x3..4,z4 leading to the ladder landing. Keep the hut approach, rack fronts and this landing unobstructed. North-edge service access remains level; ladders, not a temporary jump across an excavation gap, connect the deck to the shaft. Place wall lights outside headroom and ladder collision cells. Rail placement must be checked after native neighbor updates.

Simple uses spruce module frames, oak deck/service trim, oak slabs only on the canopy, and cobblestone shaft lining. Medium uses stone/cobblestone module frames and deck, stone or cobblestone slabs on its canopy, and the same clear shaft access. These follow measured Mine1 and Archery3 material families; Medium remains a level1 native building despite a more substantial finish. Use full blocks on walking floors. Avoid Domum components in this first quarry increment unless full matching shape/material tile metadata is carried and tested; no foreign roof style, pipes or unavailable blocks are needed.

## Exact shaft construction proposal

For each footprint W and depth D in the table:

1. Initialize non-excavation cells as structurize:blocksubstitution (preserve existing terrain), not air. Keep surface module infrastructure outside the hole from being cleared by the shaft.
2. At local y0, construct a cobblestone base across x2..W-3,z4..W-3. This leaves a permanent bottom working floor.
3. For y1..D-1, line the rectangle perimeter x2/W-3 or z4/W-3 with cobblestone. Keep the interior x3..W-4,z5..W-4 as explicit air, except the ladder. Air is an excavation target here, not a harmless blank.
4. Put east-facing, non-waterlogged ladders at (3,y,5), y1..D-1, with full backing at x2. The top shaft cells are at world H-1; extend the same backed ladder through surface local y0..4, producing a continuous overlapping connection and an above-deck handhold. Surface/shaft overlap must agree exactly at those ladder/backing cells.
5. Add supported wall torches on interior liner faces at six-block vertical intervals, away from the ladder. Do not add a cross-shaft floor or beam that seals the descent. Bottom access and intermediate-layer working positions need native proof.
6. In the surface asset, preserve the excavation rectangle below the safety rail using substitution cells rather than pre-clearing a deep pit. The quarrier, not the surface builder or fixture, must perform the shaft excavation.

Shaft primary-offset metadata is explicit and contains no quarry hut block: a second hut would register an unintended building. Keep shaft palette, packed block array and typed NBT consistent; include Structurize dependency for substitution blocks. The shaft is an infrastructure asset; do not invent a native building entry for it. Verify the loader honors the declared primary offset rather than assuming the array center.

The exact shaft heights and pit widths are proposed dimensions, not copied native minimums. Local Mangrove examples measured for convention checking use surface/shaft dimensions18x7x22 /18x26x22 and34x7x39 /34x57x39, with shaft primary Y24/55. Those differing depths confirm that32/64 are not a mandatory blueprint height. Their layouts/materials are not part of this design.

## Acceptance plan and remaining uncertainty

Static contracts should independently check all four files, exact native identities, single quarry hut per surface and zero in shafts, maximum-level1 naming, sole origin shaft= tag and path resolution, typed dimensions/arrays/tile lists, original hashes, deterministic output and available materials. Check the displacement equation and every surface/shaft overlap under all rotations/mirrors. Mutation tests should reject a wrong shaft path, later-position origin tag, missing ladder backing, blocked landing, pre-cleared quarry volume and mismatched anchor.

Native placement must prove both buildings resolve their intended shafts from the same immutable pack and register their own native types. Then create the real Mine-employed JobQuarrier and external QuarryModule assignment; require the actual WorkOrderMiner path, Mine ownership and H-2 anchor. Supply disclosed tools, fill blocks, shaft construction materials and worker food. Do not inject work-order success, progress, finished status, mined drops or worker AI.

Observe real stone removal, tool wear, colony blocks/ores-mined counters, Mine item-obtained statistics, and corresponding physical drops collected/deposited into accessible quarry storage. Require both downward worker movement through the constructed ladder route and native placement of new shaft infrastructure. Verify more than the first layer: the lower-half traversal and final bottom floor distinguish a working shaft from successful surface registration. Test Simple and Medium separately; partial production is not completed depth acceptance.

A real builder should construct each level1 surface from materials; there are no invented upgrades between these two buildings. Save/restart during shaft progress and require the same worker, Mine, quarry assignment, work order and further excavation. Separately test final completion without premature world-minimum termination, output handling, movement without recovery, dangerous ground, and player-facing deployment/claim checks. The reproduced footprint anomaly is covered by the scoped footprint fix and native boundary regressions.

## Local source map

All Java references are relative to .setup/minecolonies-audit/source/com/minecolonies:

- apiimp/initializer/ModBuildingsInitializer.java:282-294,630-649; apiimp/initializer/ModBlocksInitializer.java:148-150.
- core/colony/buildings/modules/BuildingModules.java:506-512; QuarryModule.java:53-73,115-145,182-184; MinerBuildingModule.java.
- core/colony/jobs/JobQuarrier.java:66-99.
- core/entity/ai/workers/production/EntityAIQuarrier.java:132-200 (path/work order),213-250 and289-334 (layers),254-257 (dump),552-598 (mining/fluids),639-703 (completion/substitution/statistics).
- core/colony/buildings/AbstractSchematicProvider.java:255-258,482-497 (corner behavior).
- Original local Arcology measurements: Mine1/5 10x7x10 with primary(7,2,7), Builder1 5x12x5, Barracks1 15x6x15, Archery3 10x6x10. Reference assets remain untouched.

## Native evidence update

Both generated surface buildings pass native builder construction. Exact shaft registration, assignment, footprint boundaries and completed-ladder round trips have separate native reports. Run422b6ed791604d50be49c6f14d5ee37b completed both32/64-depth shafts:31/63 continuous ladders, final floors and full blueprint matches, minimum workerY-15/-47; a native courier deposited64 mined coal. Materials, tools and food were supplied in dry uniform terrain, with recovery disabled. This is full-depth acceptance for that fixture, not hazards or restart.

Excavation revealed delayed ladder placement and stale unsupported work positions. Scoped compatibility repairs finish each layer's liner, then its wall-mounted access, then normal clearing; decoration skips blueprint air to preserve mined output. A subsequent focused access-cell test showed that ladder placement also needs to request native mining when its target contains ore. The new guard preserves matching existing access blocks and passed the stronger full-depth regression e4ce84dc471f464cb56441d55783b5a2:34coal per shaft and68 delivered, including the two access-cell ore blocks per shaft.

Clean active excavation restart bb099cb0fe3e435a96c51ca8624827bc now passes: same two citizen/entity/work-order identities, each clearing at least100 fresh cells after a second server process starts. Fluid/hazard terrain, crash recovery and transformed excavation remain separate acceptance work. The addon is required for compatibility hooks; the generated assets alone do not contain those fixes.

## Enclosed water and lava acceptance

Native run d98c2ee916094068a2e8612e14514c6c passed all4 hazard cases with runner exit0. Both Simple and Medium workers plugged one enclosed water source and one enclosed lava source with native cobblestone fill, then excavated12 previously solid cells at Y11 after both Y13 sources were sealed. Native worker identities/assignments persisted, recovery was disabled, and no burning/lava contact was observed. No post-start terrain edit or health restoration; requested supplies, tools and food were provided. Open aquifers, lava lakes, shortage handling and full-depth wet completion remain outside this proof. No production repair was needed.

## Connected pools and depleted fill supplies

Native connected3x3 pools passed (Simple water, Medium lava). A focused zero-stock reproduction failed because doMining did not wait for fill. The addon now guards only the two Arcology shaft paths, requesting the selected native fill material for all adjacent fluid cells before permitting the native filling/mining sequence. Exact consumption, insufficient partial stock, dry mining and foreign-pack boundaries passed.

A live test withdraws fill from the real worker and Mine hut immediately before a fluid encounter, verifies native NEEDS_ITEM and the unchanged barrier for200 ticks, then restocks the hut only. Both workers resume, seal their pockets and mine12 fresh cells below them; runner exit0. No output/terrain/health injection after work begins. This is supplied-hut recovery, not automated courier procurement or unlimited aquifer safety. Evidence is indexed under ArcologyQuarryShortage, ArcologyQuarryPools and ArcologyQuarryRestock.
