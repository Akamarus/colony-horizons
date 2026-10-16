# Forester supply loop and courier districts

Status: approved 2026-10-14; implemented in the isolated development worktree. Verification evidence and limits are recorded in `docs/outposts/FORESTER-SUPPLY-RESULTS.md`. Not installed or released.

## Intended result and scope

An outpost forester harvests real trees, exports surplus logs to the main
settlement, and receives finite food, tools and saplings through protected
shipments. Native couriers perform local work without accepting a trip across
the main-settlement/outpost gap. The player's installed alpha.2, live world,
public source tag and CurseForge submission remain unchanged.

Continue the approved same-parent-colony model using existing MineColonies huts,
workers, native requests and OutpostSavedData membership. Independent colonies,
automatic founding, Mayor controls, new claims, research bypasses and magic-mod
integration are outside this milestone.

## Findings from the pinned source

- DeliverymenRequestResolver checks warehouse/courier availability and queues
  requests, but does not enforce addon districts.
- JobDeliveryman.getCurrentTask selects warehouse work and batches requests with
  the same target. Different source inventories can be combined into one trip.
- EntityAIWorkDeliveryman.prepareDelivery travels to a request's start location;
  pickup and deliver travel to buildings; dump and decide can travel to a warehouse.
  All these work destinations need coverage, not just delivery destinations.
- The existing operations fixture proves native harvesting/replanting with tools
  in local huts, but preloads bread and one starter sapling. It does not prove
  autonomous food/sapling resupply or export of harvested logs.
- NativeSupplyService already handles protected timed hut-inventory transfers,
  reserves, targets, unloaded endpoints, persisted custody and quarantine. It does
  not include warehouse racks or change native courier behavior.

## Integration choice

Use a shared district policy at request eligibility and courier execution.
Filtering only request assignment misses saved or batched work. Replacing the
native courier AI would unnecessarily duplicate MineColonies behavior. A shared
policy plus narrowly scoped pinned-version hooks covers both boundaries while
retaining native local delivery.

## District rules

1. Only colonies with registered addon outposts participate. Other colonies keep
   native behavior. Never define districts using a guessed distance radius.
2. Registered outpost hut anchors and approved chunk footprints identify remote
   destinations; all other verified parent-colony buildings are home. Positions
   outside verified claims, another colony or another dimension are invalid.
3. Courier workplace, assigned warehouse, pickup source and delivery destination
   must resolve to the same district. Pickup requests use their requester location.
   Every item in a batched delivery must pass independently.
4. Missing/corrupt membership, invalid ownership or unresolved endpoint identity
   blocks managed logistics with an explicit reason. Do not regenerate empty data
   or silently classify a failed lookup as home.
5. Paused/recovery outposts accept no new courier work. Work already holding cargo
   stops safely; no deletion, duplication, automatic teleport or unsafe reassignment.
6. Check again before work navigation, inventory pickup, delivery and warehouse
   dumping. If a rule changes during an active trip, stop navigation and retain
   cargo and request identity for safe resumption/reconciliation. Merely failing a
   request must not erase cargo ownership or trigger endless reassignment churn.
7. A local work target alone is not evidence of safe pathfinding. Test routes near
   district edges; reject work-path excursions into the inter-settlement gap.
   Local hostile mobs and unrelated emergency AI are not made invulnerable.

## Protected production loop

Use the existing native operations fixture and an isolated disposable world.
Place a real home supply hut and remote forester/housing plus locally valid courier
and warehouse infrastructure where native progression permits it. Fixture-created
terrain, finite starting supplies, initial structures/levels and forced test chunks
must be recorded as QA setup, not player-facing functionality.

Seed food, replacement tools and saplings only at home. Supply routes deliver them
to the remote hut; native workers must collect/use them. Retain any essential
one-time starter sapling as an explicit bootstrap limit, not proof of restocking.
Do not repeatedly reset saturation, inject items into worker inventories, harvest
trees from test code, or move harvested logs into export storage by hand.

Observe native tree cutting, axe wear, replanting, native log deposit, protected
export and receipt at home. Pause the export during a second production cycle,
resume it, and prove reserves and incoming cargo accounting. Check a deliberate
shortage blocks work and remote replenishment resumes it. Food consumption and
replacement-tool collection must be observed separately from delivery arrival.

Warehouse storage is native local logistics. This milestone does not pretend
protected routes can already extract from all racks; route endpoints stay at
validated hut inventories. If native deposit bypasses these buffers, report the
gap and implement a tested local handoff before claiming completion.

## Acceptance and failure cases

- Home-to-home and outpost-to-same-outpost courier work remains functional.
- Home-to-outpost, outpost-to-home and outpost-to-other-outpost jobs are rejected.
- A local destination with a remote source is rejected, including batched jobs.
- An already-queued job, changed district, changed owner and replaced warehouse
  cannot start or continue an unsafe work journey.
- Unloaded targets wait without loading the transport corridor.
- Cargo held during a boundary change survives pause, clean server restart and
  restoration of valid membership without duplicate delivery.
- Two native harvest/replant cycles, export of harvested logs and observed local
  resupply consumption pass with worker identity, movement and stock evidence.
- Existing 30 supply case/phase checks and outpost record/restart tests still pass.
- Development packaging includes the new production hooks but excludes QA.

## Delivery order

1. Add failing district-policy and native eligibility/execution tests.
2. Implement policy and minimal courier hooks; prove both blocked and allowed work.
3. Extend the native operations fixture into the finite-stock production loop.
4. Add restart/full-destination/shortage recovery coverage and run regressions.
5. Record performance of synchronous supply checkpoints during the real loop;
   retain the development-only status if stalls make ordinary gameplay unsuitable.

No claim of safe autonomous outposts until both production and courier tests pass.
Manual testing is reserved for interface/path visuals that assertions cannot prove.
