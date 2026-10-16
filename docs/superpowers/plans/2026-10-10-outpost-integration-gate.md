# Remote Outpost Integration Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if that execution approach is selected. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Determine and demonstrate a safe MineColonies integration for one remote logging site and protected, persistent depot-to-depot transfers before implementing the playable outpost system.

**Architecture:** Opt-in integration probes run in disposable worlds against the pinned real MineColonies dependency. Keep remote worker/building probes separate from a small transfer model and inventory adapter. Record unsupported upstream behavior as a failed feasibility gate, not a successful test or a reason to silently fork the dependency.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.250, MineColonies 1.1.1403-1.21.1, existing Gradle wrapper/ModDev, native GameTests, PowerShell test orchestration.

**Spec:** [Approved resource-outpost design](../specs/2026-10-10-resource-outposts-design.md), especially sections 4, 7-10.

## Global Constraints

- MineColonies remains required; preserve current dependency pins and public-API-first integration.
- Same dimension, one home colony, one logging outpost, one route and one item stock target.
- Approval is the default for founding; Protected is the default transport policy.
- No fake resource generation, duplicate citizens, second owned colony, unlimited claim expansion or altered global ownership rules.
- No player saves or abandoned prototype files are used by probes.
- No full-route chunk loading or wall-clock production/travel catch-up.
- Protection applies to transport activity, not the entire colony.
- No broad Mayor/scout implementation, new resource profession, Ars integration or product UI in this gate.
- Probe success is not completion of the first playable milestone. Prototype classes remain QA-only until their conclusions are incorporated in the next plan.

## Plan boundary and coverage

The approved design spans several subsystems. This plan covers only its explicit first technical gate: remote construction/worker lifecycle, protection feasibility, item custody and reload safety. It produces a tested integration approach and a runnable demonstration, not a user-ready outpost release.

After the gate, write separate implementation plans for (1) the playable marked logging outpost, blueprint/depot UI, bounded work-area loading and resupply; (2) scout surveys; (3) the dedicated Mayor and both modes. Survival transport remains a later separately designed feature. All these remain required backlog; narrowing this plan does not drop them.

If remote native operation fails, finish independent cargo checks, report the exact blocker, and revise the integration design before writing the playable plan. Do not promise a particular native API can overcome claim/range constraints.

## Review Focus

1. Another player changes/removes an endpoint during loading: no unauthorized extraction or orphaned stack (Task 3).
2. A save occurs after partial insertion but before delivery acknowledgement: do not replay inserted items (Task 4).
3. A native citizen respawns or changes jobs after a chunk transition: preserve one identity and assignment, not two entities (Tasks 2 and 4).
4. Identical item IDs with different components or damaged tools travel together: preserve components and counts without merging unlike stacks (Tasks 3 and 4).
5. Protected travel ends into lava, blocked space, or a deleted endpoint: retain safe custody and report delay instead of creating an invulnerable worker at an unsafe site (Task 4).

## File map

Existing modifications:

- `build.gradle`: add isolated probe runs, retaining the existing resource audit and client smoke runs.
- `src/main/java/dev/colonyhorizons/ColonyHorizons.java`: opt-in QA registration only.
- `docs/superpowers/specs/2026-10-10-resource-outposts-design.md`: update only if evidence requires an explicitly reviewed architecture change.

New QA code under `src/main/java/dev/colonyhorizons/qa/outpost/`:

- `ProbeResult.java`: case ID, outcome and evidence, without masking failures.
- `OutpostProbeFixture.java`: real colony/owner/citizen fixtures and cleanup identifiers.
- `OutpostIntegrationTests.java`: native lifecycle, construction and harvest checks.
- `EndpointRef.java`, `ProbeShipment.java`, `ProbeTransferEngine.java`: minimal prototype records and custody state transitions.
- `ProbeInventoryAdapter.java`: component-preserving real inventory extraction/insertion with explicit authorization check.
- `OutpostTransferTests.java`: conservation, capacity and component checks.
- `OutpostRestartProbe.java`: server lifecycle hooks for staged persistent-world probes.
- `ProtectedTransitProbe.java`: native citizen reservation and loaded/unloaded representation feasibility.
- `ProbeReport.java`: machine-readable result output and gate summary.

New tools/docs:

- `tools/Test-OutpostProbe.ps1`: one entry point, isolated world/run paths, correct failure propagation, no general process killing.
- `docs/outposts/INTEGRATION-FINDINGS.md`: evidence, confirmed strategy, blockers and next-plan requirements.

No broad production package scaffold is needed before this gate succeeds.

## Verification commands

Run from Colony Horizons with Java 21. Task 1 introduces the commands below; they do not exist yet.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-OutpostProbe.ps1 -Suite Native
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-OutpostProbe.ps1 -Suite Transfers
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-OutpostProbe.ps1 -Suite Restart
.\gradlew.bat build runGameTestServer --no-daemon --console=plain
```

The wrapper maps Native/Transfers to `runOutpostProbe -PoutpostSuite=<suite>` using fresh unique GameTest worlds under `runs/outpost-probe`. Restart uses `runOutpostRestartProbe -PprobePhase=<phase> -PprobeWorld=<generated-name>`, a dedicated isolated server run with staged shutdowns and the same world across phases. A normal startup GameTest reset is not evidence of persistence. Do not create or accept a server EULA automatically; surface any actual prerequisite if the chosen native harness requires one.

Write results under `build/reports/outpost-probe`. Wrapper exit 0 requires expected case IDs, all PASS outcomes and no startup failure. FAIL, BLOCKED, missing reports or a timeout return nonzero. For a new suite before implementation, assert a named missing behavior/failing assertion; a launcher crash is not a valid red test.

## Task 1: A reproducible real-colony probe fixture

**Files:** build.gradle, ColonyHorizons.java, ProbeResult.java, OutpostProbeFixture.java, OutpostIntegrationTests.java, ProbeReport.java, Test-OutpostProbe.ps1.

**Interfaces:**

- `ProbeResult(String caseId, Outcome outcome, String evidence)`; `Outcome { PASS, FAIL, BLOCKED }`.
- `OutpostProbeFixture.create(ServerLevel level, BlockPos center, UUID ownerId)` returns a fixture exposing `IColony colony()`, `UUID ownerId()`, `BlockPos center()` and `ICitizenData createCitizen()`.
- `ProbeReport.write(Path directory, String suite, List<ProbeResult> results)` writes stable case IDs and outcomes.
- Native test registration is enabled only by `colonyhorizons.outpostProbe=true` and the selected suite.

**Grounded references:** `IColonyManager.createColony(ServerLevel, BlockPos, Player, String, String)`; `ICitizenManager.createAndRegisterCivilianData()` and `spawnOrCreateCitizen(...)`. The fixture needs an actual authorized player/fake-player identity, a valid structure pack and normal initialization. Do not substitute a detached mocked colony for the integration check.

- [ ] Add `fixture_owns_one_real_colony`: create through native manager, assert owner lookup returns that colony; register/spawn one citizen and assert one native citizen identity/entity. Assert QA registration is absent in an ordinary run.
- [ ] Run Native and capture the named failing fixture assertion before the fixture implementation.
- [ ] Implement the fixture and suite wrapper; use UUID run names, paths constrained under `runs/outpost-probe`, scoped logs and no deletion of unrelated worlds.
- [ ] Run Native: named fixture checks PASS and wrapper returns 0. Verify an intentionally absent report and deliberate failed case return nonzero, then remove the deliberate failure.
- [ ] Commit only this fixture/harness change with message `test: add isolated native outpost probe fixture`.

## Task 2: Prove or reject native remote building and worker operation

**Files:** OutpostIntegrationTests.java, OutpostProbeFixture.java, INTEGRATION-FINDINGS.md.

**Interfaces:** `OutpostIntegrationTests` adds native GameTests `remote_site_preserves_colony_identity`, `remote_builder_places_supplied_blocks`, `remote_logger_harvests_and_replants`, and `remote_worker_returns_without_duplicate_identity`. Reuse Task 1's fixture and report.

Use a site outside the live configured maximum colony radius plus a margin of two chunks, in the same dimension. Load only the fixture work areas for this test; separately observe native claims and do not bypass placement validation simply to make the test pass. Place a small deterministic spruce stand and a tiny supplied construction blueprint in the disposable test environment. Setup may provide supplies; observed worker output may not be injected by the harness.

- [ ] Add assertions: owner still has exactly one colony; claim settings are unchanged; build operation either follows the authorized native path or produces an explicit BLOCKED reason with source evidence; an assigned native builder consumes real supplied material and places intended blocks at the remote site.
- [ ] Add harvest assertions: existing logger's citizen ID is unchanged; actual spruce blocks are harvested, output enters its real inventory/depot, and saplings are planted. Run a second bounded cycle after fixture-controlled growth to prove replant compatibility, recording that growth was accelerated only for the test.
- [ ] Run Native with these checks; record failures including ranges/lookup/claim/navigation restrictions. Failure is expected if the native design cannot support the site.
- [ ] Add the smallest QA adapter using verified public hooks if one is available. Never patch global claim limits, duplicate jobs/population or force-set a passing outcome. Record API signatures actually used in the findings document.
- [ ] Run Native and record citizen job/home/housing consistency before/after work and return. If blocked, leave a failing gate with precise evidence, finish independent transfer tasks, and require architecture review before the playable implementation.
- [ ] Commit probe and evidence as `test: characterize remote MineColonies worker lifecycle`.

## Task 3: Real inventory custody and capacity rules

**Files:** EndpointRef.java, ProbeShipment.java, ProbeTransferEngine.java, ProbeInventoryAdapter.java, OutpostTransferTests.java.

**Interfaces:**

- `EndpointRef(UUID endpointId, ResourceKey<Level> dimension, BlockPos position, UUID ownerId)` identifies a registered probe endpoint; its stable ID is checked against the live endpoint so replacing a block at the same position does not inherit cargo access. It does not grant access.
- `ProbeShipment` carries UUID shipmentId, endpoint refs, copied component-preserving cargo stacks, state and inserted-count acknowledgements. `State { RESERVED, LOADING, IN_TRANSIT, UNLOADING, DELIVERED, DELAYED, RETURNING, RECOVERY_NEEDED }`.
- `ProbeInventoryAdapter.resolve(ServerLevel level, EndpointRef endpoint, UUID actor)` returns an optional authorized `IItemHandler`; unavailable/unloaded/unauthorized is empty.
- `ProbeTransferEngine.load(ProbeShipment shipment, IItemHandler source, List<ItemStack> requested)` and `unload(ProbeShipment shipment, IItemHandler destination)` return `TransferResult(int movedItems, State state)`. Engine mutates inventories on the server thread and updates custody exactly for successful movements. Zero/partial capacity is normal, not an exception.

- [ ] Add `load_moves_real_items_once`: start with 64 spruce logs, request 20; assert source=44, cargo=20 and duplicate invocation leaves the conserved total unchanged.
- [ ] Add `partial_delivery_retains_remainder`: destination accepts 7 of 20; assert destination gains 7, cargo retains 13, retry cannot reinsert the first 7, and eventual capacity allows exactly 20 total.
- [ ] Add `components_and_tools_survive_transfer`: send two differently named stacks and two differently damaged axes; assert counts/components/damage preserved and incompatible stacks never merged.
- [ ] Add `endpoint_permission_or_identity_change_blocks_transfer` and `slot_restrictions_are_respected`: unauthorized/replaced endpoint moves 0; item/slot limits are enforced by actual handlers. Simulated insertion is advisory; actual insertion remainder is authoritative.
- [ ] Run Transfers and observe named red assertions, implement the smallest custody prototype and handler adapter, then rerun to PASS. Do not use private builder/miner recipes or native warehouse assumptions in this inventory test.
- [ ] Commit as `test: prove component-preserving outpost cargo transfers`.

## Task 4: Persistence, chunk transitions and protected representation

**Files:** OutpostRestartProbe.java, ProtectedTransitProbe.java, ProbeShipment.java, ProbeTransferEngine.java, ProbeReport.java, build.gradle, Test-OutpostProbe.ps1, INTEGRATION-FINDINGS.md.

**Interfaces:**

- `ProbeShipment.save(HolderLookup.Provider registries)` returns CompoundTag; `ProbeShipment.load(CompoundTag tag, HolderLookup.Provider registries)` returns the record or rejects an unsupported schema.
- `ProtectedTransitProbe.reserve(ICitizenData citizen, UUID shipmentId)` returns boolean; `ProtectedTransitProbe.release(ICitizenData citizen, UUID shipmentId)` returns boolean and releases only that exact reservation. A probe must prove this interacts safely with native job lifecycle rather than merely storing a flag ignored by MineColonies.
- `ProtectedTransitProbe.tick(ServerLevel level)` advances only server-tick time and reconciles any visible representation against one authoritative shipment.
- Restart phases: `seed`, `verify_loaded`, `verify_partial`, `verify_arrived`; runner invokes them in order against the same generated world, with orderly saves/shutdowns. Fault-injection stages are explicit persisted transfer boundaries, not promises of OS-level atomicity.

- [ ] Add `shipment_roundtrip_preserves_components_and_policy`, `reload_does_not_duplicate_partial_delivery`, `ticks_do_not_advance_while_stopped`, and `unloaded_route_has_no_corridor_tickets`. Assert persisted source+cargo+destination totals, worker IDs and travel progress at each restart phase.
- [ ] Add `citizen_reappearance_does_not_duplicate_transport`: unload/reload the visible segment and exercise native respawn/job transitions; assert at most one active representation and one reserved native identity. If safe native suppression/resumption cannot be proven, report BLOCKED instead of deleting a citizen to fake success.
- [ ] Add `unsafe_arrival_retains_custody`: block destination with lava/solid terrain or remove the endpoint; assert no unsafe worker spawn, no cargo drop and a recoverable delay. Inject mob/environment damage to the loaded transit representation; assert no lost assigned person/cargo and no loot/combat exploit. The exact representation is chosen from Task 2 evidence, not presumed to be a native courier.
- [ ] Add interrupted-boundary probes after source extraction and partial insertion before acknowledgement. On restart, require proven reconciliation or explicit RECOVERY_NEEDED quarantine; uncertain records cannot auto-deliver a second copy. Document which abrupt-crash cases still require further engineering.
- [ ] Run Restart for red assertions, implement versioned persistence and the minimal protection/representation prototype, rerun all phases. Inspect actual world unload/restart logs; serialization roundtrip alone is insufficient.
- [ ] Commit as `test: verify protected outpost transit persistence`.

## Gate review and handoff

- [ ] Run all three suites, then existing `build runGameTestServer`. Existing resource-audit counts and export checks must remain valid; no player save is opened.
- [ ] Review all modified code and result files. Ensure QA classes have no ordinary-game activation, the wrapper cannot mistake missing tests for success, and prototype state cannot escape its generated world.
- [ ] Write `docs/outposts/INTEGRATION-FINDINGS.md` with exact dependency versions, executed commands/exit codes, case IDs, actual native hooks, failed alternatives, remaining crash limitations and one of `GATE_PASSED` or `GATE_BLOCKED`.
- [ ] Update the project README with that evidence, not a claim that outposts/Mayor are playable. Commit as `docs: record outpost integration gate results`.
- [ ] If GATE_PASSED, prepare the next implementation plan for the first playable route covering every deferred first-milestone requirement: marking/permissions UI, terrain validation, builder blueprint, native worker assignment, receiving depot, protected visible travel, work-area loading budget, repeat production/resupply, target accounting and retirement. If GATE_BLOCKED, propose the narrowest revised integration approach and identify whether it changes the approved spec.

## Self-review and execution choice

This plan does not implement the full approved spec and does not claim to. It completes the evidence gate explicitly required by that spec; later subsystem plans are listed above. Five likely failure classes map to named tests. New signatures and state names are consistent across tasks. Tuning of actual survey radii, earthwork and production-loading budgets is deferred to the playable/scout plans because no corresponding feature is implemented here.

Recommended execution: native execution in this thread, task by task. The probes depend closely on the previous task's discoveries, so parallel implementation would risk encoding incompatible assumptions. The user previously asked this assistant to take over from Cline; retain that preference unless changed. Review of this written plan is required before implementation under the planning workflow.
