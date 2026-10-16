# Exploration Hut: real discovery, simulated gathering

Status: approved by the user on 2026-10-15; implementation plan requested. Supersedes the active survey-to-outpost implementation scope; existing outpost work is preserved and paused.

## Agreed intent

Automate access to biome resources without the player travelling to gather them or establishing another settlement. The user approved an Exploration Hut using real biome discovery followed by simulated gathering. Keep native MineColonies building, worker and inventory controls, protected expeditions, and normal local courier collection. Keep the installed addon and live worlds unchanged.

This is an expedition abstraction, not a remote physical mine or farm. Repeat trips create their advertised reward after paid active game time; they do not secretly harvest distant blocks. The menu must say this plainly.

## Reuse and boundaries

Reuse the existing native Surveyor hut/job and Arcology module, resource catalog, asynchronous terrain inspection, provision custody, protected worker travel, and return delivery. Display names become Exploration Hut and Explorer. Preserve registry IDs, schematic paths and class identities so saved huts and workers survive. Native build, upgrade, assignment, inventory and minimum-stock menus remain available.

Do not delete or migrate existing outposts. Their established runtime remains intact. Stop further outpost development, remove the outpost entry from the new exploration page, and stop looking for buildable settlement sites during discovery. Old site-search records remain readable but confer no gathering entitlement. A returning legacy expedition returns its existing cargo normally.

No new huts, colonies, claims, Mayor, field construction, warehouse, housing or inter-settlement courier route is needed for exploration. The hut itself holds inputs and results. Existing local couriers use native requests and inventory handling; the feature does not promise delivery without a courier or supply source.

## Discovery

The player selects an exact resource and clicks Discover. The existing bounded real-world search uses one assigned healthy worker, eight bread, and the listed tool. Preserve the current limit of 32 resource-search candidates, asynchronous loading, and at most two concurrent inspection leases per server.

A discovery succeeds only after a real matching resource was found and an actual sample successfully harvested and returned. Record the biome registry ID at the sampled position, dimension, exact resource, source position, colony, owner and expedition identity. A biome name or player-entered coordinates alone never unlock a reward. Do not treat unloaded terrain as proof or inspect it synchronously from a menu.

The durable discovery becomes usable after the sample return transaction is complete. It remains valid if the sample is consumed or the original source is later removed: discovery establishes access to that resource in that region, rather than ownership of one regenerating block. It is available to Exploration Huts in the same colony and owner only. Ownership changes require a new discovery in this first version.

Legacy completed surveys lack biome evidence and must not silently become new unlocks. Show them as historical samples with a Rediscover instruction. Existing active surveys capture new evidence only when they actually harvest; expeditions already returning finish normally without fabricated evidence.

## Gathering and initial balance

After discovery, select Gather once or Repeat. One expedition at a time per colony initially, shared with discovery, matching the existing custody limit. Repeat is opt-in and defaults off. Each trip lasts 6,000 eligible server ticks (five minutes at 20 TPS), costs two bread, and returns a fixed, displayed yield of the selected resource. These numbers are proposed initial balance defaults, not additional user requirements.

| Resource | Reward per trip | Equipment | Durability cost |
|---|---:|---|---:|
| Supported Overworld logs | 16 | Usable axe | 16 |
| Saplings and mangrove propagules | 4 | None | 0 |
| Flowers | 8 | None | 0 |
| Wheat seeds | 8 | None | 0 |
| Vines | 8 | Shears | 8 |
| Kelp | 16 | None | 0 |
| Seagrass | 8 | Shears | 8 |

Use the existing eight wood families and sixteen flower entries. Pumpkin and melon remain discovery-only in this first release, clearly labelled; do not disguise fruit as seeds. Additional crops and modded resources are later data-driven extensions.

Charge food and reserve the exact tool before departure. Require enough durability for the whole fixed cost; debit that cost once as part of the durable paid-departure transaction, preserving other item components. A tool may break on its last valid trip. This expedition cost is fixed, including for enchanted tools, and is disclosed in the menu. Never charge a second time after restart.

Time advances only while the server is ticking, the owner is online in the Overworld, and the original worker still belongs to the colony and is assigned and eligible. No offline catch-up or wall-clock rewards. Pause on owner absence. Stop repeat and return existing custody on worker reassignment, illness or disabled work. Do not teleport a substitute worker or take another citizen.

Recall before earning the reward returns the reserved tool, awards no goods, and does not refund consumed provisions or expedition wear. Once earned, the reward survives recall and restart and must be delivered exactly once. Repeat starts only after complete return/delivery and fresh eligibility, stock and output-space checks. Stop repeating when supplies, tools or output room are unavailable, with a visible reason; resuming requires player action.

## Protected travel and inventory safety

Use the existing native protected-travel mechanism and original citizen ID plus UUID. Gathering requires no remote chunk tickets and never spawns a courier in the wilderness. Only discovery examines remote chunks. Return may temporarily load the original hut's bounded local footprint using existing safeguards.

Persist a separate versioned gathering journal rather than weakening SurveyCargo's guarantee that samples came from actual harvesting. Store expedition identity, discovery reference, exact worker and hut identity, paid inputs, remaining active ticks, reward snapshot, return state and repeat preference. Validate all decoded bounds and transitions.

Use existing staged inventory operations and durable pending/receipt checkpoints for payment and output delivery. A crash in an ambiguous transaction stops for recovery; do not guess, duplicate output or delete unrelated items. A full hut retains earned cargo and pauses delivery. A removed hut retains custody for recovery. No additional reward is earned while delivery is blocked. Replayed menu messages and duplicated completion ticks cannot charge or reward twice.

## Simple menu

Keep native MineColonies tabs. The exploration page has a resource list, one details panel and context-appropriate actions: Discover for locked resources; Gather once and Repeat for unlocked resources; Recall during an expedition. Display exact cost, tool wear, yield, trip duration, discovered biome, progress and any missing supplies. Completed discovery details remain viewable.

Label gathering as simulated expeditions and state that distant blocks are not harvested. Do not display outpost founding controls or producer research gates. Native hut upgrades remain functional, but increased yield, more workers, efficiency bonuses and an upgrade tree are deferred until this loop is tested and balanced.

## Implementation boundaries

- `surveyor/`: preserve discovery identities and protected travel; add discovery evidence and a distinct gathering journal/service/catalog. One shared active-expedition guard prevents overlap.
- `menu/`, `network/`, `client/supply/`: server-authorized exploration actions and compact native-style snapshots; never accept reward quantities or discovery authority from clients.
- Existing language/assets: change display text while retaining IDs and Arcology paths.
- `qa/outpost/` and `tools/Test-OutpostFoundation.ps1`: reuse isolated native GameTest harness; add discovery evidence, gathering transactions, recovery and native worker/courier tests.

## Acceptance and release gates

1. Real discovery records the actual sampled biome and exact resource; absent targets, forged reports and foreign ownership never unlock gathering.
2. Every advertised reward has a deterministic cost/yield test. One paid trip earns exactly one reward only after its eligible duration. No remote terrain edits or tickets during gathering.
3. Real native worker leaves and returns with unchanged identity and assignment; the owner stays Home. Hut output is accessible to normal local couriers.
4. Save/reload during departure, timer, earned reward and delivery preserves or explicitly quarantines custody without duplication. Full inventory, recall, reassignment, illness, tool exhaustion, hut destruction, logout and replayed actions are covered.
5. Older huts, discovery records and outpost records remain readable. Prepared outpost work is preserved but not part of this release's completion criteria.
6. Build installable development and QA artifacts; verify asset boundaries, native menus and a normal packaged game in an isolated copy. Independent final review of this feature before calling it ready.
7. Verify installed addon hash unchanged. No install, public upload, push or live-world edit is authorized by this implementation request.

## Self-review

The design retains the requested real discovery and explicitly changes only repeat resource acquisition to simulated yields. All reward creation has an explicit paid-time rule and persistent custody. Expansion, terrain work and native producer setup are removed from the critical path. Initial balance, one-trip-per-colony concurrency and discovery-only fruit entries are stated limitations, not hidden functionality claims.
