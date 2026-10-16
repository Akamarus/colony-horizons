# Protected forestry supply and local couriers — development verification

This milestone is separate from the installed and published alpha.2. It is not a release or an installation instruction. The development artifact uses version `0.1.0-dev.forester.1`.

## Implemented behavior

- Native couriers in colonies without registered outposts keep native behavior. Registering an outpost permanently marks its parent colony as managed in native colony persistence.
- Managed couriers work only within one loaded, claimed district. Workplace, assigned warehouse, source and destination must agree. Native warehouse racks resolve to their uniquely registered owning warehouse, with the actual rack claim and district checked too.
- Assignment, batching, collection, delivery, dumping, asynchronous path submission and actual path segments are checked. Managed work cannot use native emergency-distance or stuck teleports. Emergency/non-work AI retains its native behavior; safe supply is not a general-purpose bodyguard system.
- Invalid work waits with a visible delivery reason. Native request tokens and citizen inventories remain authoritative. Owner, workplace, warehouse and endpoint identities are retained while work or cargo remains. A restored ongoing delivery resumes collection/delivery rather than dumping its cargo first.
- Missing or corrupt outpost data never silently turns an already-managed colony into an unmanaged one. The independent native marker survives restart.
- Atomic snapshot replacement retries a Windows access-denied error at most three times (20, 40 and 80 ms). Other I/O errors, persistent failures and interruption still stop custody advancement. The maximum added delay is 140 ms per snapshot, potentially 280 ms for both writes in a checkpoint; this is not a guarantee against slow disks.
- Protected supply shipments feed native forester hut inventories and carry native-produced logs back home. They use finite stock, source reserves, destination targets, bounded batches, real server-tick transit time and durable inventory handoffs. They do not send citizens across the unloaded corridor or generate resources.

## Verified scenarios

| Suite | Cases/phases | Evidence |
|---|---:|---|
| CourierDistricts | 9 | Unmanaged, same district, mixed/missing/foreign/dimension rejection |
| CourierMembership | 11 | Native huts/claims, ACTIVE migration, registration marker and refreshed membership |
| CourierNative | 22 | Native assignment/batching, rack source, changed ownership/warehouse, cargo, work paths, stuck/fallback prevention and indirect path submission |
| CourierTravel | 3 | Autonomous native home and outpost delivery, pause with cargo, native resume |
| CourierRestart | 5 | Native cargo/request persistence, autonomous post-restart delivery, exactly once after another restart, missing/corrupt index |
| ForesterSupply | 12 | Finite food/tools/saplings, harvest/replant/export, tool shortage/restock, pause, full and unloaded destination recovery |
| ForesterWithCouriers | 13 | All forestry cases alongside a native local Arcology warehouse and courier, with the export buffer retained |

Reports are under `build/reports/foundation/<run-id>/<phase>/`. Final verification and timing runs are recorded below when complete.

## What the fixtures do—and do not—prove

Forestry uses real prebuilt Arcology level-1 town hall, forester and residence, one native forester, and 16 planted test trees. Setup supplies native prerequisites, claims and endpoint chunk tickets. It discloses one starter sapling and initial hunger; finite home stock is 32 bread, 32 named saplings, a hoe and one nearly broken stone axe. One healthy replacement axe is supplied only at home after an observed 400-tick shortage. Production, eating, collection, tool wear, replanting and stock movement run through native AI and production supply code. The repeated cycles harvest multiple preplanted trees; natural regrowth timing is not benchmarked.

Destination fault injection temporarily holds existing home stock aside and fills its slots with cobblestone, then restores that stock exactly. No output logs are added. The unload test disables the disposable world's spawn and native colony chunk tickets, releases only its home endpoint tickets, waits for actual unloading, verifies 400 ticks without service force-loading, and reloads the endpoint. Final home inventory equals the route's recorded delivered logs.

The combined forestry fixture additionally places a native Arcology warehouse and courier hut, assigns a native courier, and sets the forester's native pickup priority to **Never**. This player-accessible setting preserves the hut export buffer rather than letting ordinary pickups drain it into warehouse racks. The baseline performance fixture has no courier; the combined run is reported separately.

The separate courier travel fixture uses native hut blocks on a flat prepared floor. After initial positioning the normal AI moves the courier. It checks each tick for unexpected jumps and requires actual collection and receipt. This is not a complete Arcology warehouse-layout or large-population benchmark. Restart setup positions the restored courier near its target, then lets normal AI decide and complete the delivery; the separate travel suite covers walking.

## Reproduce

Use Java 21 and the project's existing NeoForge/MineColonies versions. In PowerShell, run `tools/Test-OutpostFoundation.ps1 -Suite <suite> -ArcologyPath <local-pack-folder> -TimeoutSeconds 900` for the named suites. The runner creates disposable worlds and verifies exact report cases and native load diagnostics.

Build with `./gradlew.bat supplyDevJar supplyQaJar '-Pmod_version=0.1.0-dev.forester.1' --no-daemon --console=plain`. Verify with `python tools/Test-SupplyDevPackage.py build/supply-dev-libs/colony-horizons-0.1.0-dev.forester.1.jar`.

`supplyQaJar` is a separate disposable test harness, never part of the production artifact. `tools/Test-SupplyPackagedServer.py` runs it alongside the built addon on a separately installed NeoForge server under `build/isolated-supply-server`, using normal dependency JARs rather than the development source classpath. The harness explicitly enables GameTest registration, which NeoForge otherwise disables on installed servers; it does not replace production courier classes or guards.

## Scope and remaining work

This provides a tested development foundation, not the eventual settlement-management release. Player-facing district setup, mayor development, automatic route planning, larger-population load tests and a comfortable route-management UI remain later work. Supply endpoints currently use hut inventories rather than warehouse racks; recognizing native courier rack requests does not change that protected-supply limitation.

All pre-existing worktree edits are preserved. No remote push, publication, live-world modification or installed-mod replacement is part of this milestone. Scoped commit integration is deferred because the inherited checkout contains mixed prior release and development changes.

## Installed-pack boundary

Read-only comparison of 341 installed mod/blueprint files against the starting SHA-256 manifest found zero changes. Evidence: `build/reports/courier-districts/installed-verification.json`.

## Final verification — 2026-10-14

The final development build and package inspection passed. SHA-256 of `build/supply-dev-libs/colony-horizons-0.1.0-dev.forester.1.jar`: `34470986E0B7B9089F7C71A0D1AD92ED242320E8E61D9CAD9C4312056EC6F295`.

Final run IDs (all passed):

| Suite | Run ID |
|---|---|
| CourierDistricts (9) | 49486dba95b74145a5bafe9a40a9a0e5 |
| CourierMembership (11) | 043aa7ba75bf4ab1910eb077eacd5177 |
| CourierNative (22) | 0e5db6ac967b462c99a58ca8d69854c4 |
| CourierTravel (3) | f7e9537e175c43ad856c2cc7fb2e5b24 |
| CourierRestart (5 phases) | 4ea31cdee73a419589b6102bbaa0e63d |
| ForesterWithCouriers (13) | 9ef7cb5dce684554b70b66410be1e1cf |
| Records (15) | 85eb7d7a988a4654ac9a248b1005cedb |
| NativeRestart (3 phases) | 51a2b4eb853a4a83801c32945ea98656 |
| Supply (12) | bbe148f623cb42249701846f830dedb3 |
| SupplyBridge (10) | 81dc147baef74846acaad8b26dd9b851 |
| SupplyNative (8) | f932419eb81245fca2d4445cb7cf5f72 |
| SupplyNativeRestart (3 phases) | 8c088f436bbd4505976cfb094b178e7b |
| Operations (5) | baf90fac0a0440d097b57cd9c9331379 |

The 12-case ForesterSupply suite passed three final consecutive runs, including after the bounded snapshot retry fix. Timings below are milliseconds; checkpoint count is the number of observed durable handoffs.

| Run ID | Ticks | Tick p50 / p95 / max | Checkpoints | Checkpoint p50 / p95 / max |
|---|---:|---|---:|---|
| d1cf618ea14c4ef889884078b30ae130 | 19,419 | 0.1825 / 0.3951 / 104.1306 | 16 | 4.5720 / 7.1754 / 48.7560 |
| 074fa5e4ff67427baa1e462f3fe1859e | 21,819 | 0.1797 / 0.4095 / 100.7699 | 17 | 4.1047 / 5.8753 / 51.5348 |
| badaa77a8b434b2887dc5e575671783f | 20,219 | 0.1831 / 0.3977 / 93.7859 | 16 | 4.2829 / 5.6485 / 64.2631 |

All runs stayed below the p95 50 ms tick gate and had zero checkpoints over 250 ms. These are modest disposable fixtures on Windows/Java 21, 12 logical processors, with a roughly 15.8 GiB maximum test heap. Background applications were not controlled. They do not establish large-colony capacity or client frame rate; some individual ticks exceeded 50 ms. Timing instrumentation exists only in QA, not the development JAR.

The final packaged JAR also passed all three autonomous CourierTravel cases on the separately installed NeoForge server (4 GiB heap), report `build/reports/courier-districts/packaged-server/39319a4642a249d7b4d4ffc29f41b6c7`. The server shut down cleanly. This used the exact development JAR hash above and a separate QA harness.

Final read-only installed-file comparison: 341 checked, zero changes. Scoped `git diff --check` passed after removing a trailing blank line from the test runner. Code review findings were addressed and rerun; no outstanding finding was reported in the final snapshot-write review.
