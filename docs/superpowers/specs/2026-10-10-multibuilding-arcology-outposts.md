# Multi-building satellite outposts with Arcology

Date: 2026-10-10
Status: Approved by the user. The first implementation plan is docs/superpowers/plans/2026-10-10-arcology-outpost-foundation.md; foundation phase implemented and verified. Construction, production, logistics and player-facing founding remain future phases; see ../../outposts/MULTIBUILDING-FOUNDATION-RESULTS.md.
Parent: [resource outposts design](2026-10-10-resource-outposts-design.md).

## Intended outcome

An outpost is a small named settlement with several native MineColonies buildings and citizens. It belongs to the main colony, has its own boundary, work area, local supplies and deliveries, and exchanges goods with home. A forestry specialization is the first demonstration, not a limit of one hut or worker.

This revises the parent's first-milestone interpretation of a small remote work site. Existing huts and jobs in satellite claims are preferred over a replacement Expedition Lodge. Mayor automation follows the working settlement/supply loop; it is not implied by initial founding.

## First demonstration

One main colony and one forestry outpost in the same dimension:

- Two native Forester's Huts with two existing citizens.
- A native Builder's Hut with an assigned builder for construction.
- Native housing with sufficient capacity for all assigned residents at the tested building levels.
- Local warehouse/courier service where native progression permits, with a visible unmet requirement if it does not.
- Food and tool/sapling reserves delivered from home initially; local farms and cooking may be added later.
- A native Gatehouse as the preferred route terminal if the selected pack provides a working blueprint; otherwise founding reports the missing blueprint rather than silently switching style.
- A saved settlement record capable of listing multiple buildings and citizens, stock targets, supply needs, construction status and route state.

Buildings need not all exist on the first tick. A development queue and visible material needs describe what is planned, under construction and operational. Do not claim self-sufficiency until the resupply loop and relevant services work.

## Ownership and records

Each outpost has a stable UUID, home colony ID, dimension, name, approved claim chunks, a three-dimensional development envelope, separate forestry work area, building memberships, resident/worker IDs, terminal position and lifecycle state.

MineColonies remains authoritative for actual buildings, jobs, citizens, inventories, levels and construction orders. Membership stores native anchor positions and citizen IDs, not duplicated building/entity simulations. Workers can belong to only one local work district at a time. Pausing or removing an outpost never deletes a citizen or inventory.

Founding checks ownership and permission on the server, then checks every affected chunk. Satellite claims must not overlap another colony or another outpost district. Claim size is bounded by the approved site. A pending claim record precedes changes; partial/conflicting founding pauses with recoverable evidence. Do not forcibly change chunk ownership or silently enlarge the global colony radius.

Retirement stops new development/exports, handles goods and people, and releases only claims whose ownership and addon responsibility are established. It must not indiscriminately unclaim chunks around native buildings.

## Arcology style contract

Arcology is the preferred style for this milestone. Resolve buildings from the installed Structurize pack and native blueprint identities. Do not invent blueprint filenames, dimensions, anchors, required materials, connectors or level coverage.

The author's original listing is https://www.planetminecraft.com/mod/arcology-5819547/ and describes vertical colony construction. It lists Minecraft 1.19.2. The active dev instance has no Arcology folder and the pinned MineColonies jar contains no Arcology paths. Local copies were subsequently found in CurseForge instances MineColonies Dimensional Adventure (283 blueprints), Reach For The Skies - A Create Experience (1) (278), and WorldEdit (278). The Dimensional Adventure copy and WorldEdit copy have identical hashes for shared files; the former adds five foodprep blueprints. Preferred inspection source: blueprints/arcology-horizons. Builder, lumberjack, warehouse and deliveryman level 1-5 filenames are present. No Gatehouse-named blueprint was found; entrance decorations must not be mistaken for a functioning Gatehouse. Compatibility with Minecraft 1.21.1 is unverified. The direct page fetch returned 403; the search index exposed the author description and install instructions, not the archive.

Before placement integration, inspect the actual selected archive: pack metadata, loadability, hut mappings, missing block states, access routes, support blocks, upgrade extents and construction tags. Run a native preview/load and actual builder check in a disposable world. An older version label alone does not establish either compatibility or incompatibility.

Keep the pack installed separately; no asset redistribution is part of this change. If a required building is absent, report it by role and let the player select a suitable blueprint explicitly. Do not substitute Medieval Oak without approval.

## Layout and construction

Reserve a supported foundation and the maximum verified upgrade envelope of each planned module. The layout must track occupied volumes in three dimensions so stacked structures are distinguished from collisions. Any intended shared structure/connectors must be supported by actual pack layout evidence.

Construct supporting floors, access and lower modules before dependent upper modules. Verify walkable access for workers and material delivery; visual contact alone is insufficient. Revalidate terrain and blueprint volumes before native construction orders. Never leave an unsupported elevated module or excavate protected buildings/containers.

The first implementation supports a player-approved layout with explicit blueprint anchors. Automatic Mayor placement uses this same validated layout model later. Automated packing of arbitrary Arcology modules is not assumed to be solved by this design.

All building material lists come from the actual blueprints/native construction system. Follow native progression and limits, including warehouse counts, research, worker capacity and building upgrades. Missing prerequisites show a reason; they are not silently bypassed.

## Local work and supplies

Assign housing/workplaces close to each other inside the outpost. Native foresters gather real trees, consume tools and replant. A defined work area keeps the forestry operation outside the settlement's construction envelope.

Show consolidated construction needs and operating shortages on the outpost detail view, including quantities and supply destination. Maintain food, tools and saplings before exporting surplus wood. Count local reserves and incoming shipments once.

Native couriers must not accept work across the long-distance gap. Integration must establish an enforceable local assignment/request boundary before claiming the local delivery system works. A local warehouse alone is not sufficient evidence of that boundary. If an adapter cannot enforce it with public APIs, document the specific limitation before expanding scope.

## Routes

Use native Gatehouses/sign pathfinding where verified, but represent outpost links by addon endpoint IDs. Do not write self-alliances into MineColonies' colony-ID connection maps. A native recorded connection is not proof of safe travel or completed cargo delivery.

The protected shipment coordinator handles real extraction, custody, partial delivery, restart recovery and safe arrival. Reuse the tested native citizen travel lifecycle with persistent arrival validation. Keep the existing uncertain-transfer quarantine requirement. This revision does not authorize unbounded chunk loading or server-offline production.

## Delivery stages and acceptance

1. Pack inspection and settlement records: explicit multi-building membership, unique worker assignment, versioned persistence, invalid/stale references reported.
2. Disposable native settlement fixture: two workplaces and local housing/Builder support registered to one home colony; no second colony created; restore claims, buildings and assignments after a real restart.
3. Arcology construction: real compatible blueprints, supported terrain, native builder completion, usable access and upgrade space.
4. Native production: both foresters harvest/replant and obtain supplies locally without walking home for normal needs.
5. Logistics loop: local couriers stay within districts; one protected route exports real logs and resupplies food/tools/saplings; replay/restart and full-destination tests preserve goods and workers.
6. Player-facing milestone: founding/layout approval, consolidated needs, pause/resume and route status are usable without commands.

A passing stage is not evidence that later stages are playable. Keep QA fixture shortcuts (instant registration, generated supplies or forced chunks) out of production behavior and label their limits.

## Remaining risks and decisions already made

- Pack version/blueprint compatibility must be verified from the actual archive.
- Normal native claim reconstruction remains radius-limited; satellite recovery needs tests.
- Colony-wide services may pull workers back home; test food, housing, tools and construction independently.
- Native warehouse limits and research remain authoritative.
- The first outpost belongs to the main colony. No separate town hall/independent colony is required.
- Mayor creation, scouting and general automatic layout are later stages of the approved broader direction.
