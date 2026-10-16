# Protected supply ledger

This increment implements an internal production transport core. It does **not** yet connect MineColonies hut inventories, register player-facing depots, run local couriers, or expose a delivery menu. Nothing automatically takes items from a live colony.

## Implemented behavior

`SupplyNetwork` owns nine-slot depot buffers and route cargo in a single save unit. Routes are restricted to two distinct depots with the same registered owner and colony. A trusted server adapter must establish those identities; these registration methods are not player permissions or proof of native claim ownership.

Routes carry one component-aware item type with a destination target, source reserve, batch limit and travel duration. They can export logs and carry food, tools or saplings in the opposite direction. Each route has at most one active batch. Other committed deliveries, including returns, count toward the destination target; the highest active route reserve for the same source/item is preserved.

Transport advances only when its server-side driver supplies tick progress. The ledger never uses elapsed offline time, moves a citizen, spawns an entity, or loads a route corridor. The future endpoint adapter supplies the set of currently available depot IDs. An unavailable endpoint blocks departure/arrival; this is an internal availability contract, not an implemented native chunk/ownership resolver.

A full destination accepts only what fits and retains the remainder in cargo. Pausing freezes travel. Cancellation disables future dispatches and returns undelivered cargo; a full or unavailable source retains that return cargo. Already delivered goods stay delivered. Returned inventory/cargo snapshots are copies.

Limits are explicit: 128 depots, 256 routes, nine slots per depot, stock targets/reserves up to 4,096, batch limits 1–64, and travel 1–72,000 ticks. Actual batches also respect the item's maximum stack size. The tick API accepts at most 1,200 ticks per call. The first implementation uses deterministic route creation order, not a fair-priority scheduler.

## Persistence and safety boundary

`SupplySavedData` stores all depot contents, cargo, route identities, policy, remaining travel, pause/cancel flags and delivery counters in one dimension-local `colonyhorizons_supply.dat`. Reload does not re-extract cargo. Invalid versions, duplicate identities, foreign owner/colony route references, invalid stack counts, malformed fields and impossible cancelled-cargo states are rejected. If a primary save exists but cannot load, the factory refuses to replace it with an empty writable ledger.

The pinned NeoForge `SavedData.save` uses `IOUtilities.writeNbtCompressed`: it writes a separate temporary file, forces its contents, then replaces the target (atomic move where the filesystem supports it). Source and destination stock and cargo therefore roll back together to the last intact snapshot. This is not a promise of zero lost progress since the last save or protection from disk failure. Interrupted-write acceptance injects failure before publication and verifies the old custody snapshot remains readable.

**External inventory handoff is deliberately not enabled.** Copying a native hut inventory into this ledger and then saving two independent files would reintroduce a crash duplication/loss window. Likewise, simply exposing these buffers as an unrestricted native courier capability would not enforce district boundaries. A future bridge needs durable transfer receipts/reconciliation, native permission/identity checks, and tested local courier restrictions. Player deposits/withdrawals need the same explicit custody boundary before a UI is shipped.

## Tests and reproduction

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='<user-home>/Documents/ChatGPT/Minecraft Colony Mod/.gradle-local'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite Supply -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite SupplyRestart -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite Records -TimeoutSeconds 900
./gradlew.bat clean build runGameTestServer --no-daemon --console=plain
```

SupplyRestart uses four independent server processes: seed in-transit/paused cargo, reload and deliver logs, reload paused food and hold it at an unavailable endpoint, then reload and deliver food once. Expected IDs are saved separately by the QA fixture; later phases never seed stock or create missing depots/routes. Source + destination + cargo must remain exactly 32 logs and 32 named bread throughout.

Only Supply QA suites reduce unused spawn-chunk loading and choose a repeatable test origin, avoiding fresh random terrain on every restart. Real SavedData writes and normal server shutdown remain enabled. Normal gameplay, native construction tests and the resource audit do not use that hook.

## Next integration work

1. Implement and fault-test native inventory handoff receipts, including replaced huts, unloaded endpoints and changed permissions.
2. Bind ledger depot identities to the home/outpost records and enforce district-local courier work.
3. Run the actual Arcology forestry-to-home export and food/tool/sapling resupply loop.
4. Add founding/depot controls and consolidated route/shortage status, followed by Mayor management.

No Arcology assets, native progression rules, colony claims, worker behavior or Ars integration change in this increment.
## Verified results (2026-10-10)

The [retained acceptance report](evidence/protected-supply-ledger.json) contains 12 supply contract cases and four actual process-restart phases, all PASS. Both runner commands exited 0:

- Supply: `d8016a30b05e4156a47f9c813431589b`.
- SupplyRestart: `d73478fdc157405184c5f73a00158939` (seed, verify, verify_paused, verify_final).

The restart sequence preserved route/depot IDs, component-bearing cargo, remaining travel and pause state. Across all four processes, total stock plus cargo remained 32 spruce logs and 32 named bread. Final delivery occurred once. The interrupted-write case retained the previous complete snapshot after an injected write failure.

Early attempts reached passing assertions but exceeded a shorter shutdown timeout while Minecraft synchronously saved test-world chunks. Those attempts are not counted as successful runs. The successful runs retained normal world saving and exited cleanly under the documented 900-second phase limit.

The existing Records regression also passed all 15 cases with runner exit 0 (run 0340ba4ba03c4d30b353f0d1561c284b).

Final clean build and the existing resource-audit GameTest both exited 0. The audit reported 1,671 items, 1,592 recipes, 33 crafting modules and zero inherent-AI/candidate errors. The Supply-only world hook did not activate in that audit. Jar inspection found the supply classes and no bundled blueprint files. Java 21 / pinned NeoForge and MineColonies dependencies were unchanged.

A fresh-context code review identified two issues before completion: returning cargo needed to count as incoming stock, and a malformed cancelled/non-returning cargo snapshot needed rejection. Regression cases failed before those fixes and passed afterward; the follow-up review found no remaining material issue. This review and these tests establish the internal ledger contract, not native hut handoff or playable route integration.
