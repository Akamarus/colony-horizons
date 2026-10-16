# Forester supply and courier districts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Preserve the user's choice of inline implementation.

**Goal:** Prove real outpost forestry supported by protected shipments, with native couriers confined to their local district.

**Architecture:** Resolve district membership from native building identity, claims and OutpostSavedData. Apply one shared policy to native request selection, batched work, inventory actions and navigation; retain native request/cargo ownership when blocked. Extend isolated GameTests to observe native production and finite-stock resupply through NativeSupplyService.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.250, pinned MineColonies 1.1.1403, Mixin, native GameTests, PowerShell runner.

**Spec:** `docs/superpowers/specs/2026-10-14-forester-supply-and-courier-districts.md` (approved by Akamarus on 2026-10-14).

## Global Constraints

- Keep the player's installed alpha.2, live world, public source tag and CurseForge submission unchanged.
- Use `.setup/safe-supply-routes`; preserve its existing changes. Stage only task-owned hunks, never the entire tree.
- Outposts remain districts of one parent colony. No independent-colony support, new claims, research bypass, automatic founding, Mayor UI or magic integration.
- Preserve native behavior in unmanaged colonies. Managed invalid data must fail closed, never silently become home.
- Protected transport uses validated hut inventories, not warehouse racks. No transport corridor force-loading or courier teleportation.
- No repeated worker food injection, saturation reset, test harvesting or manual export-stock transfer in the production proof.
- All initial structures, claims, forced QA chunks, finite seed stock and bootstrap exceptions must be recorded as fixture setup.
- Only create development artifacts. Do not invoke public release, install, launcher-profile update or publication tasks.

## Review Focus

1. Corrupt/missing district data mistaken for an unmanaged colony: fail closed without overwriting evidence (Task 1).
2. Same destination batched with a remote pickup: check every request's source and destination (Task 2).
3. Valid endpoints connected by an unsafe path: stop before crossing the district boundary (Task 3).
4. Warehouse/owner/membership changes while carrying goods: retain cargo and request identity across restart (Tasks 3 and 5).
5. Resupply arrives but is never consumed, or QA seeds masquerade as exports: observe actual native use and account for finite stocks (Task 4).

## File boundaries and commands

All paths below are relative to the development worktree. New production files live under `src/main/java/dev/colonyhorizons/supply/`; new hooks under `src/main/java/dev/colonyhorizons/compat/mixin/`; tests under `src/main/java/dev/colonyhorizons/qa/outpost/`. Each task lists exact basenames within these directories.

Shared test registration: `qa/outpost/FoundationGameTests.java` and `tools/Test-OutpostFoundation.ps1`. Add suite names to both, exact expected report cases to the runner, and restart phase lists where required. A suite succeeds only with exit 0, every expected case present and passed, and no primary-load error; a server merely starting is not a pass.

Use this PowerShell session setup once:

```powershell
Set-Location '<user-home>\Documents\ChatGPT\Minecraft Colony Mod\.setup\safe-supply-routes'
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:GRADLE_USER_HOME='<user-home>\Documents\ChatGPT\Colony Horizons\.setup\gradle-arcology-recovery'
$arcologyFixture='<user-home>\Documents\ChatGPT\Minecraft Colony Mod\.setup\alpha2-launcher-smoke\blueprints\arcology-horizons'
function Run-Foundation([string]$suiteName) {
    & .\tools\Test-OutpostFoundation.ps1 -Suite $suiteName -ArcologyPath $arcologyFixture -TimeoutSeconds 900
    if (!$?) { throw "Failed suite: $suiteName" }
}
```

Run Gradle sequentially against this cache. Do not use the installed playtest pack as test output. Before implementation save a read-only SHA-256 inventory of installed mod JARs and blueprints to `build/reports/courier-districts/installed-before.json`; compare again at completion. Do not read/write live save contents for these tests.

### Task 1: Resolve districts and active membership safely

**Files:** Create `supply/CourierDistrictPolicy.java`, `supply/NativeCourierDistricts.java`, `qa/outpost/CourierDistrictTests.java`; modify `outpost/OutpostRecord.java`, `outpost/OutpostSavedData.java`, `qa/outpost/OutpostRecordTests.java` and shared registration.

**Interfaces:** `CourierDistrictPolicy` defines `Status { UNMANAGED, ACTIVE, BLOCKED }`, `District(ResourceKey<Level> dimension, int colonyId, Optional<UUID> outpostId)`, `Endpoint(Status status, Optional<District> district, String reason)`, and `Decision(boolean allowed, String reason)`. Expose `static Decision check(Endpoint workplace, Endpoint warehouse, Endpoint source, Endpoint target)`. `NativeCourierDistricts.resolve(ServerLevel level, int colonyId, BlockPos anchor)` returns an Endpoint using loaded native identities and authoritative claims, without loading chunks.

- [ ] Add suite `CourierDistricts` and named cases: `unmanaged_passthrough`, `home_local`, `outpost_local`, `cross_district`, `foreign_claim`, `dimension_mismatch`, `unknown_hut`, `unloaded_hut`, `paused_outpost`, `corrupt_index`, `membership_refresh`. Test assertions include `check(home,home,home,home).allowed()==true`, `check(remote,remote,remote,remote).allowed()==true`, and false for each mixed district, blocked endpoint or foreign dimension. Unmanaged passthrough requires all four endpoints to be genuinely unmanaged; mixed unmanaged/managed fails closed.
- [ ] Run `Run-Foundation CourierDistricts`; record missing policy/registration as initial RED, then ensure behavioral assertions fail against a permissive stub rather than accepting compilation failure as the only proof.
- [ ] Implement resolver and pure policy. Home means a verified same-colony building outside every approved outpost footprint. A hut inside a footprint but missing from its registered building map is blocked. Validate current native colony ownership and worker/building identity each decision; do not cache membership indefinitely.
- [ ] Add explicit `ACTIVE` to `OutpostRecord.State`; existing PLANNED/PAUSED/RECOVERY_NEEDED remain blocked. Add schema 2 writing and explicit schema 1 reading with states preserved, never auto-activate old records. Test active round-trip, old paused record remains paused, unknown state/schema throws. Expose `NativeCourierDistricts.activate(ServerLevel level, UUID outpostId)` only for validated existing records: validate all registered anchors, current claims and worker assignments before replacing state; no founding or claim mutation.
- [ ] Preserve existing corrupt-file guard. Add a persisted managed-colony marker when a record is registered/activated, and check it before treating an absent index as unmanaged. Store it in native colony persistent data using a namespaced key; first activation requires it to be persisted. Test deleting only the index in an isolated restart fixture blocks marked colonies. On a corrupt index whose ownership cannot be established, block courier integration for that dimension with a diagnostic; do not synthesize empty data.
- [ ] Run `Run-Foundation CourierDistricts` and `Run-Foundation Records`; require all new and old cases green. Review staged hunks and commit as `feat: resolve safe courier districts`.

### Task 2: Filter native assignment and each batched request

**Files:** Create `supply/NativeCourierGuard.java`, `compat/mixin/CourierRequestResolverMixin.java`, `compat/mixin/CourierJobMixin.java`, `qa/outpost/CourierNativeTests.java`, `qa/outpost/CourierNativeFixture.java`; modify development `src/main/resources/colonyhorizons.mixins.json` and shared registration.

**Interfaces:** `NativeCourierGuard.checkRequest(ServerLevel level, int colonyId, BlockPos workplace, BlockPos warehouse, IRequest<?> request)` returns Task 1's Decision. Resolve Delivery start/target and Pickup requester location through pinned native request APIs. Unknown managed request types are blocked with a reason. Fixture exposes `start(GameTestHelper helper)` and emits `CourierNative.json`.

- [ ] Add `CourierNative` cases `native_home_delivery`, `native_remote_delivery`, `native_cross_rejected`, `native_remote_source_rejected`, `native_mixed_batch`, `native_unmanaged_delivery`, `native_paused_queue`. Drive real native requests and couriers; assert local goods arrive, cross-district goods remain at source and no forbidden request becomes active. For a two-request same-target batch with one remote source, assert local completion and remote rejection separately.
- [ ] Run `Run-Foundation CourierNative` before hooks; expect cross-district selection to fail the tests while native local controls work. Record observed request tokens and stock counts.
- [ ] Hook pinned `DeliverymenRequestResolver.canResolveRequest`, `attemptResolveRequest` and `resolveRequest` eligibility/enqueue paths; hook `JobDeliveryman.getCurrentTask` candidate selection and `getTaskListWithSameDestination`. Reuse guard decisions at every seam; resolver eligibility uses the candidate warehouse and its actual assigned courier workplaces. Do not remove disallowed requests from native ownership or mark them delivered.
- [ ] Use narrowly targeted injections/redirects with `require=1`; confirm exact descriptors against pinned source/bytecode before writing selectors. Avoid replacing entire native methods or mutating shared queue lists just to filter candidates. Keep the original request state machine authoritative.
- [ ] Run `Run-Foundation CourierNative` and `Run-Foundation CourierDistricts`. Require permitted work to finish, rejected work to remain retryable and one batch member's rejection not to strand the valid member. Review and commit only task hunks as `feat: keep native courier assignments local`.

### Task 3: Guard ongoing work and paths without losing cargo

**Files:** Create `compat/mixin/CourierWorkMixin.java`, `compat/mixin/CourierNavigationMixin.java`; extend `supply/NativeCourierGuard.java`, `qa/outpost/CourierNativeTests.java`, `qa/outpost/CourierNativeFixture.java` and development mixin config.

**Interfaces:** Add `NativeCourierGuard.checkWork(EntityCitizen courier)` and `NativeCourierGuard.checkPath(EntityCitizen courier, Path path)` returning Decision. `checkWork` resolves current job, workplace, assigned warehouse and every concurrent request afresh. Both are no-ops for verified unmanaged couriers. Denial stops work navigation and returns a waiting AI state; it must not call native request completion/cancellation or clear inventory.

- [ ] Add cases `queued_before_boundary_change`, `owner_changes_with_cargo`, `warehouse_replaced`, `pause_with_cargo`, `edge_path_rejected`, `local_path_allowed`, `unloaded_wait`. Assert held stack components/count and request token remain identical on denial; assert no world item drops and no corridor chunk tickets. A local-to-local path deliberately detouring through an unapproved gap must be stopped before entering the gap.
- [ ] Run `Run-Foundation CourierNative`; expect these execution/path cases RED despite Task 2's assignment filter.
- [ ] Guard `EntityAIWorkDeliveryman.pickup`, `prepareDelivery`, `deliver`, `dump`, `decide` and pickup inventory mutation entry. Cover already-held/concurrent tasks, warehouse return and inventory dumping. At actual navigation path acceptance and before advancing a work path, inspect every remaining path node plus segments connecting nodes. Outpost paths must remain in their approved chunks and native parent claims; home paths must remain in verified parent claims outside outpost footprints. Reject absent/unresolved claim data, not just endpoint mismatch. Handle asynchronously computed/replaced paths on the server thread before movement.
- [ ] Restrict navigation hooks to deliveryman work navigation; do not globally disable citizen emergency behavior. Capture current path/request diagnostics when waiting, without log spam. Resume only after fresh successful policy validation; no force teleport, request cancellation or alternate-warehouse assignment with unresolved cargo.
- [ ] Run `Run-Foundation CourierNative`; all new and local control cases must pass with per-tick position evidence, not a coarse final-distance check. Commit reviewed task hunks as `fix: stop unsafe courier work paths while retaining cargo`.

### Task 4: Prove the finite-stock native forester loop

**Files:** Create `qa/outpost/ForesterSupplyTests.java`, `qa/outpost/ForesterSupplyFixture.java`; extract only reusable setup from `qa/outpost/ArcologyOperationsFixture.java` if needed; modify shared registration. Production changes, if tests expose a gap, stay in `supply/NativeSupplyService.java` or `supply/NativeCourierGuard.java` according to ownership.

**Interfaces:** Fixture `start(GameTestHelper helper)` emits `ForesterSupply.json`. Consume existing `NativeSupplyService.bind/link/pause/route`, Task 1 activation, and native worker AI. Use actual native hut capabilities and assignments; do not bypass native warehouse/research limits to create an otherwise impossible player layout.

- [ ] Add `ForesterSupply` cases `finite_home_resupply`, `first_harvest_replant`, `native_log_deposit`, `export_home_receipt`, `food_consumed`, `replacement_axe_used`, `sapling_restock_used`, `second_cycle_pause_resume`, `shortage_then_recovery`, `forester_identity_and_bounds`. Start with zero logs in all participant inventories and record every finite QA input. Seed 32 bread, 2 stone axes, 32 oak saplings and 1 stone hoe at home only; document any single starter sapling separately. Track finite bread through native food requests/restaurant use if that is the real consumption path, rather than claiming hut arrival feeds the worker.
- [ ] Run `Run-Foundation ForesterSupply` with production routes disabled for RED: no remote harvest/resupply/export completion should be reported. Then enable real protected routes in the fixture, not direct inventory writes.
- [ ] Configure home-to-forester axe target 1/reserve 0/batch 1, sapling target 8/reserve 0/batch 8, hoe target 1/reserve 0/batch 1; route bread to the native local food intake appropriate to the tested configuration, target 8/reserve 0/batch 8. Export oak logs forester-to-home target 64/reserve 4/batch 8. Let native workers collect and consume supplies. One finite axe may start near breakage as disclosed QA setup so native break/replacement is observable within the timeout.
- [ ] Observe native tree removal/replanting, axe damage, log deposit and route cargo/home arrival with tick-stamped inventory accounting. If native hauling drains the hut before export, configure native local pickup limits/requests and verify the buffer remains usable; never populate it from test code. If a new production handoff is necessary, first add a failing native handoff assertion, implement it within this task's production boundary, and keep warehouse rack transport out of scope.
- [ ] Pause log export for a second harvest, prove no export movement while paused, then resume and require receipt without draining the 4-log reserve. Exhaust a finite required supply and assert the native worker waits; replenish only the home input and require resumed work. Food consumption, replacement axe wear and a replanted supplied sapling must each have their own evidence, not just arrival counts.
- [ ] Run `Run-Foundation ForesterSupply` and `Run-Foundation Operations`; all cases green with surviving same worker IDs and no unsafe courier travel. Commit as `test: prove native forestry over protected supply routes`, including only production fixes established by failing tests.

### Task 5: Restart and recovery with goods in custody

**Files:** Create `qa/outpost/CourierRestartTests.java`; extend `qa/outpost/CourierNativeFixture.java`, `qa/outpost/ForesterSupplyTests.java`, `qa/outpost/ForesterSupplyFixture.java`; modify shared registration/runner phase handling. Production fixes confined to the responsible policy/guard/service files.

**Interfaces:** `CourierRestart` suite has `seed`, `verify`, `verify_final` processes using the same disposable world. Use existing probe-phase/report conventions. Native saved citizen inventory and request tokens remain authoritative; do not introduce a second persisted cargo ledger for native couriers.

- [ ] Add `cargo_seed` (pick up a known stack, then block membership while held), `cargo_verify` (new JVM, same citizen/request/count/components, no forbidden travel), `cargo_verify_final` (restore valid membership, exactly one completed delivery, empty carried stack). Include changed owner and replaced warehouse controls: restoration of a location alone is not restoration of trusted identity.
- [ ] Run `Run-Foundation CourierRestart` before any recovery fixes; preserve the actual failure evidence. Add the marked-colony/missing-index restart case from Task 1 to this isolated suite. Never delete data from a real world.
- [ ] Repair only observed persistence/revalidation defects. On unresolvable identity keep waiting and expose the blocker; do not guess a recipient. Add `full_export_destination` and `unloaded_export_destination` to `ForesterSupply`: bounded held cargo, source reserve preserved, exactly-once delivery after freeing/reloading target, no forced corridor loading.
- [ ] Run `Run-Foundation CourierRestart`, `Run-Foundation ForesterSupply`, `Run-Foundation NativeRestart`, `Run-Foundation Records`, `Run-Foundation Supply`, `Run-Foundation SupplyBridge`, `Run-Foundation SupplyNative`, `Run-Foundation SupplyNativeRestart` sequentially. Preserve at least the existing 30 supply case/phase assertions; do not silently reduce runner expectations.
- [ ] Review stock conservation and report coverage, then commit task hunks as `test: cover courier custody and forester supply recovery`.

### Task 6: Development packaging, performance evidence and handoff

**Files:** Create `src/supplyDev/resources/colonyhorizons.mixins.json`, `tools/Test-SupplyDevPackage.py`, `docs/outposts/FORESTER-SUPPLY-RESULTS.md`; modify `build.gradle` only for `supplyDevResources` and development verification, and fixture timing instrumentation in `qa/outpost/ForesterSupplyFixture.java`.

**Interfaces:** Package test CLI: `python tools/Test-SupplyDevPackage.py <jar-path>`, nonzero on missing new hook classes/registrations, missing entrypoint or any QA class/test mixin. Read ZIP members and mixin JSON using the Python standard library. Development resource task overlays the supplyDev config after release resources; never add experimental hooks to public release resources.

- [ ] Add packaging assertions for all four courier mixins, required policy/guard classes, production entrypoint, and absence of `dev/colonyhorizons/qa/` and `FoundationTestSaveMixin`. Build the current development jar and run the check for RED: the existing supplyDev task copies the release mixin config and would omit new hooks.
- [ ] Overlay the dedicated development mixin config, retaining existing production fixes. Use a distinct `-Pmod_version=0.1.0-dev.forester.1`; do not change installed artifacts or the published version.
- [ ] Run `./gradlew.bat supplyDevJar -Pmod_version=0.1.0-dev.forester.1 --no-daemon --console=plain`, then `& '<user-home>/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' tools/Test-SupplyDevPackage.py build/supply-dev-libs/colony-horizons-0.1.0-dev.forester.1.jar`. Require both exit 0. Run an isolated server smoke with this built JAR and normal dependencies, excluding the source classpath, to prove mixin application and native local delivery; store logs under build/reports only.
- [ ] Instrument the actual production fixture server tick/checkpoint durations without changing checkpoint semantics. Run three complete `ForesterSupply` trials; report transaction count, p50/p95/max checkpoint milliseconds, tick durations and machine/load context. Treat a sustained p95 tick over 50 ms or repeated checkpoint stalls over 250 ms as a development-only performance blocker, not a green release claim. Do not weaken disk verification to meet timing.
- [ ] Compare installed JAR/blueprint hashes with the starting manifest. Require equality; report any independently changed file rather than overwriting it. Record all new test counts, report paths, actual native setup limitations, remaining blockers and development artifact hash in results documentation. No publication or installation.
- [ ] Review final diff for unmanaged behavior, all work/path hooks, cargo custody, real consumption evidence and packaging. Commit only task hunks as `build: package and document isolated forester supply milestone`.

## Completion and review

The implementation is complete only when both allowed local courier controls and rejected unsafe-work cases pass, native production/resupply evidence passes, restart conservation passes, and development packaging is verified. A failing production or safety gate is unfinished work; a performance blocker must remain explicit and precludes a player-ready recommendation.

Plan self-review: spec coverage maps district rules to Tasks 1–3, production to Task 4, recovery/regressions to Task 5 and packaging/performance to Task 6. Existing records have no ACTIVE state; Task 1 supplies a versioned, non-activating migration rather than treating PAUSED as active. The existing development packaging inherits public mixins; Task 6 supplies a separate overlay. Public/installed alpha.2 stays unchanged throughout. Review this plan before gameplay implementation.

## Execution outcome — 2026-10-14

Tasks 1-6 are implemented and verified in the isolated development worktree. See `docs/outposts/FORESTER-SUPPLY-RESULTS.md` for final report IDs, production JAR hash, three timing runs, normal installed-server smoke, and fixture limitations. Historical checkboxes below/above are the original step list; this outcome is the final task-level status. Per-step commit/integration actions are deferred because the inherited checkout contains mixed prior release/development edits. No installed alpha.2 files, live world, published tag or submission were changed. Player-facing district/route UI and larger-colony testing remain future scope, not completed features.
