# Remote outposts and Mayor — next alpha design

Status: approved by Akamarus on 2026-10-14. Implementation plan prepared separately; feature not yet implemented or installed.

## Outcome and correction

Akamarus wants a multi-building settlement in a different biome, beyond the main colony's ordinary claim radius, that gathers real resources, receives supplies and sends surplus home automatically. A dedicated Mayor must manage its development in the next alpha. Grouping existing central-colony huts does not satisfy this requirement.

The next alpha is not ready until the player can return home and the remote settlement continues its permitted work. Existing alpha.2, its installed pack, submitted releases and real saves remain unchanged throughout development and isolated testing.

## Architecture decision

Use a geographically separate satellite settlement with explicit, bounded remote claims belonging to the original native MineColonies colony. A separate native colony ID is not required for separate territory. Keep an unclaimed gap; do not increase the Town Hall radius or claim a corridor. Native huts, citizens, research and jobs remain authoritative.

This restores the intent of the approved October 10 multi-building design. Existing fixtures have registered native huts 512 blocks from home and verified persistence with an unclaimed gap. These fixtures establish feasibility, not a playable remote founding system: they use test supplies, loading and registration shortcuts that must not enter production.

Alternatives considered: a second normal native colony would provide its own services but introduces single-owner lookup assumptions, ownership and progression complications; a simulated remote-production building would be simpler but would not provide the real multi-hut settlement requested. Prefer bounded satellite claims unless the production integration tests demonstrate a blocker. A blocker must be reported, not hidden by reverting to in-boundary districts.

## Player flow

1. Choose a home supply hut in the Town Hall's Outposts & Supplies menu and appoint an available citizen as Mayor for the proposed outpost.
2. Choose a purpose, initially Forestry. Select a surveyed site in another biome, or allow the Mayor to search an explicitly designated, already explored area. Show the remote site, biome, planned Arcology layout, staffing, supplies and development limits before founding.
3. Default mode requires approval of each new settlement. Automatic mode can approve proposals only within the player's allowed area, staffing cap and material allowance. Both modes use the same server validation and development logic.
4. Commit only the approved remote footprint. Send a finite founding party and real bootstrap supplies using protected travel/cargo custody; never spawn free replacement citizens or supplies.
5. Native builders construct the planned Arcology buildings. The Mayor assigns the approved residents, keeps operational supplies stocked and enables the resource export route.
6. From home, see stage, next action, resource targets, shortages and a single Pause/Resume control for Mayor development. Route pause remains separate and explicit.

Existing local district records are preserved as local districts. They must not silently acquire remote claims, a Mayor, loading tickets or building permissions. The normal New Outpost flow becomes founding a remote settlement; grouping local huts is not advertised as the core outpost feature.

## Mayor responsibilities in this alpha

The Mayor is an appointed, persistent native citizen with a dedicated management role, not an external conversational AI. The role must be protected from normal automatic worker assignment and cannot simultaneously fill another job. The citizen lives at the outpost once suitable housing exists; initial travel and bootstrap must have a safe, explicit staging state.

The first fully supported development template is an Arcology forestry settlement with a builder, two foresters and sufficient local housing for the Mayor and workers. Local warehouse/courier buildings are added only when native progression allows them; the initial supply loop must work with hut inventories and must not require an unavailable second warehouse.

The Mayor must:

- Validate and carry out the accepted building sequence using native construction work orders, with foundations and access before dependent modules.
- Assign only available or explicitly allocated citizens, respecting real housing/work capacity and keeping homes and jobs local.
- Request/import actual building materials, food, tools and appropriate saplings; keep operating reserves before exporting resources.
- Maintain an explicit resource stock target at home, accounting for incoming cargo and existing reservations exactly once.
- Queue permitted building upgrades within the player's selected development cap and native research/building limits; never replace an occupied custom build without its own approved plan.
- Report specific blockers and quantities. Missing materials or research pauses that action rather than creating free items or repeatedly queuing duplicate work orders.
- Resume saved work safely after reload. If the Mayor is absent, dead or unassigned, start no new development decisions; preserve existing construction, people and cargo and show Needs Mayor.

Priority: sustain existing operations, satisfy accepted player targets, complete approved construction, then permitted upgrades/expansion. Material budgets are real item allowances with minimum home reserves, not invented currency. Player cancellation releases unused reservations but does not erase inventory or undo already built structures.

General autonomous development for every MineColonies profession, unlimited site search, conversation AI, cross-dimension outposts and Ars integration remain outside this alpha. Native huts already supported by the pack remain usable, but do not label their development autonomous without an implemented and tested Mayor template.

## Land, construction and activity

Every claim mutation requires current owner permission, server-side validation of every affected chunk, no foreign colony overlap, no overlap with another outpost, and world-border validity. Save a pending founding record before mutation, track exactly which claims were added, and reconcile partial failures. Never force another colony's ownership to change. Native claim reconstruction and building upgrades must preserve only approved satellite claims; native Home boundaries remain unchanged.

Terrain/layout validation uses real installed Arcology blueprint anchors and measured bounds, including the maximum permitted upgrade envelope. Reject unsupported foundations, liquid hazards, protected containers and inaccessible layouts. Bounded earthwork can be part of the approved construction bill. No floating structures, creative paste or building-level setters in production. Automatic site selection uses the same validation as manual selection.

Remote work uses bounded, addon-owned chunk tickets for active sites and required supply endpoints, plus a narrowly scoped native activity adapter where required. Do not use fake-player subscribers to keep production alive. Initial safety defaults: one developing settlement at a time, at most two active outposts per owner, and at most 49 approved chunks per outpost, including the work area. Server configuration may reduce these limits. Site layouts that exceed a bound are rejected, not partially loaded. Tickets are reconciled on restart and released when no longer needed; pending cargo is retained safely when activity is suspended. No offline-server simulation or unattended world generation.

While an authorized owner is online at Home, outpost construction and forestry must function without player proximity. When the owner is offline, pause addon-driven remote activity by default. Existing cargo and work orders remain persistent. These are explicit operating rules, not a hidden requirement to stand beside the outpost.

## Safe supplies and worker locality

Reuse component-preserving inventory transfers, shipment custody, reserve rules, restart recovery and quarantine on uncertain reconciliation. Remote claims participate in endpoint validation. Safe routes do not send a normal courier on foot across the unclaimed gap. Founding-party travel preserves original citizen identities and validates arrival before releasing workers.

Local couriers remain in their assigned settlement. Also verify food, sleep, tool requests, emergencies and builder work selection: courier confinement alone does not guarantee that other workers stay local. If a service cannot be satisfied locally, expose the shortage rather than silently making an unsafe journey home.

## Release acceptance — required, not optional follow-ups

1. Survival owner founds a multi-hut settlement beyond the configured normal colony radius, with an unclaimed gap and no changes to the main radius. Native reload and upgrades preserve its bounded claims.
2. Conflict, ownership change, cancellation, repeated approval and interrupted founding tests leave no foreign claims, duplicate settlement, duplicate citizen or lost inventory.
3. Actual appointed Mayor issues native work orders, consumes/imports finite real materials and completes the supported Arcology starter settlement. Repeated ticks and reload produce no duplicate orders or budget spending.
4. Two native foresters harvest biome-appropriate trees, use and replace tools, replant and receive resupply through multiple work cycles. The player stays at Home throughout; no fake subscribers or QA force-loading satisfy this check.
5. Logs reach Home and operating supplies reach remote huts. Full destination, exhausted source, paused site, ownership changes and restart conserve goods and preserve recoverable cargo.
6. Original citizens retain identity, local housing and jobs. No routine work, food or courier path crosses the gap. Missing/dead Mayor pauses new planning visibly.
7. Mayor approval mode waits; automatic mode stays within area, staffing and budget limits. Upgrade limits/research are respected, and unsupported terrain has no destructive partial build.
8. Chunk-ticket budget/release/reload tests show bounded remote activity and no leaked tickets or unlimited generation.
9. Simple native-style menus expose Found Outpost, Mayor, current stage, needs and routes. Test in a disposable copy of the actual playtest pack and a backed-up colony copy before any installation.

A model-only Mayor scheduler, a command-only QA fixture, a nearby district demo, or a teleport-and-seed test does not meet this gate. If these requirements cannot all be completed, do not publish the next alpha as the requested outpost/Mayor release.

## Work order

Implement and test in dependency order: production remote claims and activity; safe founding and bootstrap; Arcology construction orchestration and citizen locality; dedicated Mayor decisions/budgets/supply integration; simple founding/management UI; real remote multi-cycle acceptance; isolated packaged upgrade test. Existing transfer tests and alpha.2 compatibility remain regression gates.

## Evidence inspected for this revision

- `docs/outposts/MULTIBUILDING-FOUNDATION-RESULTS.md`: remote registration, claims and clean restart, with fixture limits.
- `docs/outposts/ARCOLOGY-OPERATIONS-RESULTS.md`: native Arcology construction/forestry, with loading/provisioning limits.
- Pinned MineColonies `ChunkDataHelper`: normal building claims are radius-limited; static claim entry points exist and require addon prevalidation and reconciliation.
- `OutpostMenuPlanner`: current UI explicitly requires existing claimed land and completed huts, which is the usability defect being replaced.
- Approved October 10 resource and multi-building designs: Mayor priority, both approval modes, real budgets, safe transport and satellite settlement intent.


## Warehouse-first amendment (2026-10-14, user steering)

The user requested that a new outpost build its warehouse and courier infrastructure first and use those exclusively for inbound and outbound resource transport. This supersedes direct Home-to-workplace managed routes in the original design. Installed alpha.2 remains unchanged.

### Intended behavior

1. Review a finite founding kit and the measured Arcology layout. Default party becomes five original citizens: Mayor, builder, local courier and two foresters. Provide five level-one homes; do not silently replace a forester with the courier.
2. The protected founding journey carries a finite, reviewed construction kit for the builder hut, warehouse and courier hut. This one-time founding custody is distinct from a recurring supply route. Shortage blocks departure with an exact bill. No automatic free materials or ongoing direct delivery to workplace huts.
3. Complete the builder hut at level one first (native WorkOrderBuilding.canBuildIgnoringDistance requires sufficient builder level for another building). Build the warehouse next and courier hut third, through original native work orders. The Mayor uses the finite carried kit to supply this bootstrap.
4. Assign the original courier to the completed local courier hut and associate them with the local warehouse using native modules. Enable recurring protected supply only after the warehouse/courier infrastructure is ready. Then finish homes and production huts.
5. Inter-settlement routes have warehouse endpoints only: Home warehouse <-> outpost warehouse. Local couriers collect production and satisfy local worker requests. They never physically walk between settlements. Existing generic/legacy routes do not acquire remote privileges.
6. Use actual warehouse inventory including native registered racks, exact item components, finite allowances and Home reserves. Preserve durable custody and restart handling across all touched inventories. Never treat only the warehouse hut buffer as all warehouse stock.
7. If the warehouse, courier or safe local service becomes unavailable, show a specific blocker and retain cargo safely. Do not fall back to sending a native courier across the gap or silently resume direct workplace shipments.
8. Keep the menu simple: settlement status, warehouse link, courier status, material shortages and existing Mayor approval controls. Server-derived bills and actor/revision validation remain mandatory.

### Work still required

This amendment is a design change, not a claim of implementation. Re-measure warehouse/courier level-one through level-five envelopes within the allowed footprint; expand party/housing validation; account for native warehouse assignment/research restrictions; update founding custody, supply endpoints, rack checkpointing and courier request locality. Preserve old development records without silently migrating active cargo or interpreting old approval as approval for extra buildings/materials.

The full acceptance gate must use this warehouse-first layout. The seven-building test remains a regression for the earlier construction implementation, not evidence of the new logistics flow or sustained forestry.
