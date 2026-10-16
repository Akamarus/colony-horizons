# Resource outposts, Mayor management and protected transport

Date: 2026-10-10
Status: Original direction approved; integration probes completed with documented limits. See the [multi-building Arcology revision](2026-10-10-multibuilding-arcology-outposts.md) for the next production design under review.
Project: Colony Horizons, a MineColonies addon for Minecraft 1.21.1 / MineColonies 1.1.1403-1.21.1.

## 1. Purpose and agreed decisions

The player should be able to settle in a preferred biome without repeatedly travelling to other biomes to supply the colony. Success means an established remote source repeatedly supplies requested resources while the player remains at home or travels elsewhere.

Agreed requirements:

- Resource outposts belong to and supply the main colony.
- Sites can be marked by the player or found by scouts.
- A Mayor develops outposts, managing construction, staffing, supplies and upgrades.
- Both Mayor modes exist: approve each new outpost, or allow automatic founding within a budget and permitted area. Approval is the default.
- Stock targets and shortages drive production and delivery.
- Local couriers serve their local settlement. Dedicated caravans handle long-distance routes.
- Transport safety is configurable per world. Protected is the default: delays and rerouting are possible, but workers and cargo are not permanently lost in transit. Survival transport is an optional later extension.
- Loaded-world journeys should be visible where practical. Unloaded travel uses saved progress rather than requiring the whole route to stay loaded.

Implementation choices below are proposed defaults, not claims that MineColonies already implements them. They deliberately limit the first milestone. Existing saves and the abandoned standalone prototype are not modified by this design work.

## 2. Scope and delivery order

This is an architectural change, delivered in focused increments rather than a single broad automation release.

### First playable milestone: one renewable supply route

Example: a home colony outside a spruce biome receives spruce logs from one player-marked spruce logging outpost.

- Same dimension, one home colony, one logging outpost, one route and one item stock target.
- Player marks and approves the site and allocates real materials and available people through a simple management screen.
- Builder constructs a small supported outpost blueprint; work must not require the player to place the building manually.
- Assigned workers harvest real local trees, replant and consume tools/supplies. Logs must not appear from a timed recipe detached from world harvesting.
- Remote workers remain uniquely associated with their home colony; no duplicate citizens or free population.
- A real outpost inventory and home receiving inventory hold goods. Dispatch moves actual goods into tracked cargo; arrival deposits them with capacity checks.
- Protected travel and save/reload/chunk-unload handling are included from the beginning.
- Outpost production can run without the player standing there through bounded, authorized work-area loading, if the integration tests demonstrate that it is safe.
- Basic resupply transports tools, saplings and food so the demonstration is a repeating loop rather than a single shipment.
- Requests for missing supplies and blocked operations are visible. Unsupported automation never silently fabricates an item.

Scouts and the full Mayor are required subsequent increments, not included in the claim that this first route works. A temporary management screen is an acceptable first-milestone control surface. It is not the completed Mayor feature.

### Following increments

1. Add bounded scout surveys and site proposals alongside manual site selection.
2. Add a dedicated Mayor role and both development modes, using the proven outpost lifecycle. Mayor planning persists independently of the screen but requires a valid assigned Mayor to initiate new plans. Existing work and transport continue when the role is vacant.
3. Add more outpost types, more routes and native request-system integration, using the resource map to identify meaningful biome-dependent supplies.
4. Add optional survival caravans, escorts and recovery after their risk rules have a separate reviewed design. Do not expose a nonfunctional survival toggle beforehand.

Cross-dimension portals, independent satellite-colony ownership, arbitrary professions at remote sites, unlimited offline production, talking AI, species/evolution and Ars integrations are outside this first subsystem release.

## 3. Player experience

A colony management view has an Outposts section with a short list: name, resource, status, workers, stock and next delivery. Details offer site marking, scout survey, stock target, supplies, route, pause and retirement. Development proposals show estimated materials, people, expected output, terrain work and unresolved needs before approval.

The player can set a permitted survey/development area. Automatic development is off until an area and budgets are configured; enabling automation is not permission to build anywhere. A manual location still needs ownership, terrain and resource validation.

Use explicit status messages: Needs saplings; Needs builder; Missing construction materials; No suitable site found; Destination full; Route delayed; Work-area loading limit reached; Mayor approval needed. Show required quantities and where to supply them. The player should not have to search individual buildings to diagnose the network.

Do not promise exact delivery times while a route is delayed. Display an estimate plus the reason for delay. Explain clearly that protected travel is simulated through unloaded regions.

## 4. Ownership and MineColonies boundary

An outpost is an addon-owned location linked to a home colony identity and dimension. It is not created by secretly founding a second ordinary MineColonies colony, abandoning the first, enlarging claims without limit, or bypassing another player's permissions.

Each outpost has a stable ID, footprint/work area, type, home colony, development state, assigned citizen IDs, registered inventory endpoints, blueprint/version, stock rules and pause reason. Server-side permission checks gate every action. If the home colony is missing or permission changes, pause new operations and retain recoverable state.

MineColonies integration stays behind focused adapters for identity/permissions, worker assignment, construction, request status and inventory access. Native citizens, progression and requests remain authoritative in their domains. Addon network records own outposts, route plans, Mayor proposals and shipments.

Critical feasibility question: can native construction and logging safely work beyond ordinary colony building/claim bounds without breaking navigation, job lookup, citizen lifecycle, happiness, housing, requests or save/reload? The source audit does not answer this. Prototype it before implementing a planner around it.

Preferred approach is public building/job/module APIs with a remote-work adapter. If those cannot support it, record the failing checks and evaluate a narrow addon job/building integration. Do not silently fork MineColonies, patch global ownership rules, or build a parallel citizen simulation. A material architectural change requires a revised design.

## 5. Discovery and terrain

Manual markers and scout reports feed the same site validator. A valid logging site needs the target resource, a workable harvest/replant area, safe access, space for its blueprint and a supported foundation. Generated terrain is examined at actual coordinates, not inferred solely from the biome name.

Scout jobs perform bounded surveys over time, persist progress and report evaluated locations. They do not synchronously generate or scan an unlimited world. Surveys avoid unauthorized claims and protected areas. Cancelled or exhausted surveys return a clear result, not an endless search.

Before construction, revalidate the site: claims, inventory-bearing blocks, existing structures, terrain, water/lava, access, footprint and material estimate may have changed. Construction may clear approved natural terrain and fill foundations with dirt. It must not erase protected structures or containers, leave floating buildings, or bridge arbitrarily deep terrain. Unsafe/excessive earthworks invalidate that proposal and require another location.

The implementation plan will choose measured bounds for survey work, footprint loading and earthwork limits after the feasibility probe; all are server-enforced and configurable. These are tuning values, not unresolved behavior: exceeding any bound pauses/rejects work instead of expanding it automatically.

## 6. Mayor decisions and budgets

Approval mode proposes new outposts and waits for the player. Once approved, development and operation continue automatically within the accepted footprint, staffing and material budget. Expansion beyond that scope becomes a new proposal.

Automatic mode may approve its own proposals only inside the permitted area, below configured outpost/staffing limits and within available budgets. Budgets are item allowances and minimum home reserves, not a new currency system. Unlimited spending is never the default.

Mayor priority order:

1. Keep existing routes and workers supplied and unblock essential operations.
2. Address unmet explicit player targets and accepted colony requests.
3. Repair or expand an existing suitable outpost when cheaper and permitted.
4. Propose or found another outpost only when existing capacity cannot meet demand.

The Mayor respects research, building levels, native worker requirements and available population. It cannot recruit free citizens, teach unsupported recipes, conjure seeds/tools or bypass building progression. A plan reserves its expected budget; competing plans cannot spend it twice. Changed shortages or player cancellation release unused reservations. Use bounded planning work and cooldowns so persistent shortages do not create repeated duplicate proposals.

Initially stock targets are explicit item IDs with component-aware matching where relevant. Count available home stock and committed incoming cargo, subtract conflicting reservations, and retain outpost operating reserves. Pause gathering/dispatch when the target is met; resume after actual demand returns. Later request integration must preserve native priority and completion semantics rather than declaring a request fulfilled on dispatch.

## 7. Production and local logistics

Outpost workers gather real resources at the site. Renewable logging requires replanting, valid soil, starter saplings, growth, tool replacement and food. The first milestone must demonstrate this cycle; if native logging is unsuitable, the integration finding is reported before expanding the scope.

The outpost provides a local supply/cargo interface. Local courier integration is a tested adapter, not an assumption that a native courier can serve arbitrary remote buildings. A home receiving depot must hand goods into the existing colony supply network. The first slice may use a deliberately limited depot interface rather than changing every warehouse.

Outbound materials have reservations but remain real inventory until extraction. Construction and operating supplies have precedence over exports needed to sustain the site. All missing inputs appear in the network status with their destination. Finite gathering types must later expose depletion rather than claim renewable output.

## 8. Caravan safety and movement

Protected transit is a distinct activity boundary. Local workers and the whole colony do not become immortal. Assigned transport personnel cannot be independently assigned to another job while travelling.

The route record is authoritative. A visible caravan is its loaded-world representation, not another owner of the cargo. Caravans do not duplicate citizen identities or simulate both entity motion and a second simultaneous journey. Where MineColonies citizen removal/spawning is unsafe, the prototype must determine a safe representation that preserves the assigned citizen record; visual stand-ins must not be presented as independently populated colonists.

- Loaded safe route sections: show movement where pathfinding is practical.
- Unloaded sections: retain elapsed server-tick progress, route distance and travel time. Do not load the full corridor just for visual movement.
- Transition between representations: preserve one shipment identity and one progress position; unload/reload cannot reset travel or duplicate cargo/entities.
- Dangerous or obstructed sections: protected transit pauses, reroutes or returns safely. It does not lose workers or contents. If safe re-entry cannot be found, remain in transit with a visible delay reason.
- No valid destination or destination full: retain cargo, retry with bounded backoff, or return to an authorized depot. Never discard the remainder or drop it into an unsafe world location.
- Server stopped: no wall-clock catch-up. Single-player pause/server-tick pause likewise suspends progress.

Protection must not create a mobile combat tank or invulnerable item farm. Transit representations do not fight, harvest, offer arbitrary storage access or drop cargo. Safe departure/arrival transitions and protection against mob damage, environmental hazards and administrative entity removal need explicit tests. World corruption and deliberate world deletion are outside gameplay guarantees.

Survival mode can later use the same shipment state machine with explicit loss/recovery outcomes. Mode changes must not silently expose an already dispatched protected shipment to losses; capture the policy at departure.

## 9. Persistence and inventory integrity

Server-owned records include a schema version, stable outpost/route/shipment IDs, assigned citizen IDs, endpoint identities, component-preserving item stacks, reservation IDs, travel progress, policy and transfer state. Clients display snapshots and submit validated intents, never authorize inventory changes.

Shipment lifecycle: reserved -> loading -> in transit -> unloading -> delivered. Delayed, returning and recovery-needed are explicit states with retained cargo. Retiring an outpost stops new reservations and requires a return/unassign process; it must not delete workers or in-flight supplies.

At any moment cargo has one authoritative holder: source inventory before extraction, shipment custody after extraction, or destination inventory after insertion. Partial insertion leaves the remainder in custody. Reservations are promises, not copied stacks. Expired reservations cannot erase extracted cargo.

Use idempotent transfer identifiers and persisted transfer/reconciliation records. Ordinary save/reload must conserve item counts and citizen identity. Endpoint inventories and world saved data may save at different times, so arbitrary process crashes cannot be claimed atomic merely because state is in SavedData. The prototype must test interrupted transfer recovery. If ownership of a transfer cannot be proven after interruption, quarantine it for recovery and stop that route instead of guessing and duplicating/destroying goods. Document the supported crash-recovery boundary before release.

Outpost production loading is separately bounded from caravan movement. A small configured number of active work areas can receive chunk tickets only under authorized colony activity rules. When loading expires or the cap is reached, production pauses honestly; existing transit can progress without loading those areas. No unbounded always-on farm simulation is introduced.

## 10. Acceptance criteria and evidence

The implementation plan must assign runnable checks to these outcomes, using real MineColonies integration worlds where mocks would hide the relevant behavior:

| Scenario | Required result |
|---|---|
| Mark remote spruce site | Valid site outside ordinary home work area accepted without making another owned colony; invalid/protected sites rejected with a reason. |
| Build on sloped terrain | Builder completes an authorized supported foundation; no floating structure or destroyed protected container. |
| Staff outpost | Existing citizen assigned once; native identity/population preserved across departure, operation and return. |
| Produce repeatedly | Harvest, replant, growth and supply replacement demonstrated; actual block/item changes account for logs. |
| Deliver to home | Actual exported logs arrive and become available to the home supply system; stock target stops further unnecessary supply. |
| Resupply | Actual tools/food/saplings travel outward without consuming protected home reserves twice. |
| Unload and reload route | Journey continues with one shipment, no duplicate entity/cargo and no full-route chunk loading. |
| Leave outpost area | Authorized bounded work loading permits production; expiry/cap pauses it visibly. |
| Full/broken destination | Cargo retained; partial inserts conserved; delay or return shown. |
| Threat or obstruction | Protected personnel/cargo survive; route recovers or reports a recoverable delay. |
| Save/restart at transitions | Identities, items, reservations and progression reconcile once; interrupted transfers cannot silently duplicate goods. |
| Competing requests | Incoming/reserved stock counted correctly; no two plans spend the same inventory or assign the same citizen. |
| Permissions/retirement | Unauthorized requests rejected; deleted or retired endpoint preserves pending recovery and returns/unassigns people. |
| Scouts, later increment | Bounded search finds/rejects real sites, resumes after reload, respects area and avoids uncontrolled generation. |
| Mayor, later increment | Approval mode waits; automatic mode stays within area/budget/population limits; repeated shortage produces one plan. |

First technical gate: prove remote worker/building lifecycle and a component-preserving depot-to-depot transfer that survives unload/reload. Failure here blocks the broader Mayor/scout implementation until the integration approach is revised. Passing a model-only test does not satisfy this gate.

## 11. Sources and boundaries

- [Audited system constraints](../../minecolonies-map/SYSTEMS.md): colony ownership, radius/loading, native courier scope, public building/job/request hooks.
- [Resource map](../../minecolonies-map/README.md): native gathering, crafting and unresolved resource routes.
- [Project transition](../../TRANSITION.md): MineColonies remains the required base; no standalone rewrite without demonstrated need.

No new claim of API feasibility is made here. The integration gate is deliberately ahead of broad automation. Future implementation plans should quote this scope and identify which acceptance criteria each increment satisfies.
