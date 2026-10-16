# Remote Outposts and Mayor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Preserve the existing inline execution approach; do not launch parallel implementation agents.

**Goal:** Ship a testable next alpha in which a Mayor develops a real Arcology forestry settlement beyond Home's claim radius and keeps it producing and exchanging supplies while its owner remains at Home.

**Architecture:** Retain native colony ownership, huts, jobs, construction and inventories. Add bounded satellite founding, production activity, native-citizen Mayor management and development journals around the existing supply/courier services. Separate persistent intent from native side effects; every side effect has a stable operation identity and restart reconciliation.

**Tech Stack:** Java 21; Minecraft 1.21.1; NeoForge 21.1.250; pinned MineColonies 1.1.1403; Structurize and BlockUI already in this checkout; Arcology Horizons pack. No dependency upgrades or external AI service.

**Spec:** `docs/superpowers/specs/2026-10-14-remote-outposts-and-mayor-alpha-design.md` (approved).

## Global Constraints

- Existing alpha.2, its installed pack, submitted releases and real saves remain unchanged throughout development and isolated testing.
- Use the existing `.setup/safe-supply-routes` checkout on `feat/safe-supply-routes`; it contains inherited mixed changes. Inspect diffs and checkpoint only each task's owned files; never blanket-stage. Public author identity is Akamarus.
- Initial safety defaults: one developing settlement at a time, at most two active outposts per owner, and at most 49 approved chunks per outpost, including the work area. Server configuration may reduce these limits.
- Keep an unclaimed gap; do not increase the Town Hall radius or claim a corridor. Never force another colony's ownership to change.
- No floating structures, creative paste or building-level setters in production. No fake-player subscribers to keep production alive. No offline-server simulation or unattended world generation.
- Owner online at Home is sufficient for approved remote activity; offline owners pause addon-driven remote activity by default. Do not stop ordinary native Home simulation.
- Both approval-first and automatic development modes are included. Forestry is the first autonomous template; general profession planning, cross-dimension travel, AI conversation and Ars are excluded.
- Existing local districts get no automatic remote privileges. Do not delete or convert their data silently.
- Build development artifacts as `0.1.0-dev.remote.1`. Prepare `0.1.0-alpha.3` only after all release gates pass; this plan does not authorize publication or installation.

## Review Focus

1. Ownership or land changes between proposal and commit: reject without claiming foreign land or spending materials (Tasks 2, 4, 8).
2. Save ordering differs between native world, citizen data and addon journals: retain uncertain custody for recovery, never replay extraction/spawn/work orders blindly (Tasks 1, 4, 6).
3. Remote chunks are loaded but native workers sleep, freeze, seek Home food or accept Home construction: prove useful native work with no remote player (Tasks 3, 5, 9).
4. Arcology pack changes or module rotation alters future upgrade volume: invalidate stale layout approval before destructive work (Tasks 2, 6).
5. Repeated shortages and multiple plans share a reserve: one reservation and one action, with no overspend or proposal spam (Task 7).

## File map and shared contracts

New production package `dev.colonyhorizons.remote` owns remote lifecycle; `dev.colonyhorizons.mayor` owns appointments and decisions. Keep the existing `OutpostRecord` membership model and `OutpostSavedData` schema-1/2 readers intact. Use a separate strict saved-data file, `colonyhorizons_remote_development`, keyed by existing outpost UUID; absence means no remote authority. Do not infer authority from distance or old district names.

Define the following nested immutable records/enums in `remote/RemoteModels.java`, with copied collections and component-preserving item identities:

- `SiteRequest(int colonyId, String name, BlockPos center, BoundingBox allowedArea, BlockPos homeSupply, List<Integer> citizenIds, int mayorId, int maxBuildingLevel, Budget budget)`.
- `Budget(Map<ItemKey,Integer> allowances, Map<ItemKey,Integer> homeReserves)`; `ItemKey` wraps a count-one item/component identity using holder-aware NBT, not a mutable map-key ItemStack.
- `BuildingPlan(UUID operationId, String role, BlockPos anchor, String pack, String blueprint, String hash, RotationMirror transform, BoundingBox upgradeVolume, int targetLevel)`.
- `SiteDraft(UUID id, SiteRequest request, Set<ChunkPos> chunks, List<BuildingPlan> buildings, String fingerprint, List<String> blockers)`; blockers must be empty at commit.
- `Mode { APPROVAL, AUTOMATIC }`; `Phase { PROPOSED, CLAIMING, STAGING, BUILDING, OPERATING, PAUSED, RECOVERY_NEEDED, CANCELLED }`.
- `Development(UUID id, UUID owner, SiteDraft draft, Mode mode, Phase phase, long revision)`; operation journals, Mayor appointment and item reservations are separate records keyed by development ID.
- `StepResult(String stage, List<String> blockers, boolean progressed)`; quantities and affected hut/item IDs are structured fields in the eventual snapshot, not parsed from this display text.

Resolve native identifiers from the pinned API before adding mixins. Use narrowly scoped adapter entry points below; never copy native job implementations. Add hooks only for the named invariants, leaving unmanaged colonies unchanged. Register production additions through a shared initializer called by both main and release `ColonyHorizons` entry points, preventing the test build from having features absent from the packaged JAR.

## Verification commands and harness

Run Gradle processes sequentially. Use Java 21 and the existing dependency cache; no downloads/upgrades are part of this plan.

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='./.setup/gradle-arcology-recovery'
$pack='<user-home>/Documents/ChatGPT/Minecraft Colony Mod/.setup/alpha2-launcher-smoke/blueprints/arcology-horizons'
./tools/Test-OutpostFoundation.ps1 -Suite RemoteRecords -ArcologyPath $pack -TimeoutSeconds 900
./gradlew.bat test supplyDevJar supplyQaJar '-Pmod_version=0.1.0-dev.remote.1' --no-daemon --console=plain
```

Each task adds its named suite to `qa/outpost/FoundationGameTests.java` and `tools/Test-OutpostFoundation.ps1` as part of that task. The command above then substitutes that suite's name. Extend the runner with exact expected case IDs, phase manifests and the existing primary-load guard. Missing tests, unknown suites, timeouts, backup recovery and failed assertions must exit nonzero. Plain Gradle `test` currently has NO-SOURCE and does not count as acceptance evidence.

Restart suites must run separate server processes using the same disposable save: `seed`, `reload`, `paused-reload`. The runner owns phase orchestration. No reload phase may reconstruct missing colony data or reseed stock.

## Task 1: Persistent remote authority and limits

**Files:** create `remote/RemoteModels.java`, `RemoteDevelopmentData.java`, `RemoteLimits.java`, `RemoteRuntime.java`; modify both `ColonyHorizons.java` entry points; test `qa/outpost/RemoteRecordTests.java`.

**Interfaces:** `RemoteDevelopmentData.get(ServerLevel)`, `find(UUID): Optional<Development>`, `create(Development)`, `transition(UUID,long,Phase): Development`; compare revision before mutation. `RemoteLimits.forServer(MinecraftServer)` reads registered server config, capped at the spec maxima. A missing remote record grants no claim, loading or Mayor privileges.

- [ ] Write `RemoteRecords`: legacy membership remains readable; no implicit remote conversion; malformed/unknown remote schemas fail closed; duplicate IDs and stale revisions reject; defaults are 1/2/49 and reductions apply. Pin assertions including `h.assertTrue(remote.find(legacyId).isEmpty(), "Legacy district gains no remote authority")` and an unchanged saved-record count after a rejected transition.
- [ ] Run `RemoteRecords` RED; distinguish missing implementation/compiler failure from the later behavioral assertions.
- [ ] Implement strict persistence, identity validation and config registration; existing corrupt files cannot fall back to empty writable state. Include round-trip copies of component-bearing item budgets.
- [ ] Run `RemoteRecords` GREEN, then existing `Records`; review and checkpoint only this task.

## Task 2: Site survey and real Arcology layout approval

**Files:** create `remote/RemoteSitePlanner.java`, `ArcologySettlementLayout.java`, `RemoteSurvey.java`; modify `integration/ArcologyCatalog.java`; test `qa/outpost/RemoteSiteTests.java`.

**Interfaces:** `RemoteSitePlanner.review(ServerPlayer,SiteRequest): SiteDraft`; `RemoteSurvey.next(ServerLevel,UUID development): Optional<BlockPos>` advances a persisted bounded search cursor. Catalog must resolve installed pack display identity and real blueprint hashes, accepting Arcology Horizons without weakening hut/palette validation. Keep inspection read-only.

- [ ] Write `RemoteSites` tests for valid site outside configured Home radius, unclaimed gap, foreign claims, world border, liquids, containers, unsupported floor, overlapping upgrade volumes, missing pack and changed blueprint fingerprint. Assert `draft.blockers().isEmpty()` only for the valid measured layout; world blocks/claims/inventory must be identical before and after every review.
- [ ] Run RED. Implement one measured ground-supported forestry layout: builder, two foresters, and native housing sufficient for the approved party including Mayor. Reserve level-1–5 bounds up to the selected cap; resolve capacity from native levels. Include protected access and forest work area within 49 chunks. No arbitrary stacked-module solver.
- [ ] Implement manual site review and automatic survey of already generated chunks within the selected area: at most one candidate per 20 server ticks and 256 candidates per proposal, with a resumable cursor and No suitable site found on exhaustion. Never generate new chunks during search.
- [ ] Run `RemoteSites` GREEN, existing `Arcology` catalog checks, then checkpoint. Persist selected layout, bill inputs and fingerprint for revalidation at founding and each build action.

## Task 3: Remote claims and genuine unattended activity

**Files:** create `remote/RemoteClaimService.java`, `RemoteActivityService.java`, `RemoteTickets.java`; modify `RemoteRuntime.java`; create scoped `compat/mixin/RemoteClaimRebuildMixin.java` and, only if native activation tests require it, `RemoteActivityMixin.java`; add hooks to development mixin resources only. If the natural-growth test exposes a player-distance restriction, implement `remote/RemoteGrowthTicks.java` as described below. Test `RemoteClaimTests.java`, `RemoteActivityTests.java`, `RemoteClaimRestartTests.java` under QA.

**Interfaces:** `RemoteClaimService.commit(ServerLevel,UUID,long): StepResult`, `reconcile(ServerLevel,UUID): StepResult`; `RemoteActivityService.tick(ServerLevel)` and `active(ServerLevel,int): boolean`. Ticket ownership is addon/outpost-specific, not global forced-chunk state.

- [ ] Write `RemoteClaims` RED: commit beyond native maximum radius, unchanged Home bounds, ownership change between review/commit, partial claim interruption, duplicate commit and no corridor. Verify every affected native claim, not just record state.
- [ ] Implement pending claim journal before mutation, per-chunk prevalidation and postcondition checking. Use native claim APIs without forced owner changes. Reconcile only journaled claims; never unclaim unrelated preexisting land. Scope rebuild handling to authoritative approved remote chunks after native reconstruction.
- [ ] Write `RemoteActivity` RED: real owner online at Home, remote ticking/work possible with production tickets; third active outpost and fiftieth footprint chunk reject; two jobs requesting the same endpoint share tickets; owner logout, cancel and reload release/rebuild only addon tickets. Include main-endpoint ticket union in total accounting (maximum 16 extra endpoint chunks per owner, reject excess).
- [ ] Implement NeoForge ticket lifecycle and actual native activity integration. First test whether normal Home presence already keeps the shared colony active; do not override native activation unnecessarily. Never add a fake remote subscriber. Capture ticket and player-position evidence.
- [ ] Verify natural sapling/random-tick progress with the owner at Home, not merely block/entity ticking. If vanilla suppresses necessary growth outside player range, add production-scoped normal random-tick scheduling only for already-ticketed approved forestry sections, respecting the world's randomTickSpeed and excluding sections already receiving vanilla random ticks. Never double-tick near a returning player or grow trees by repeatedly placing blocks. Test owner approach/departure and randomTickSpeed zero. If this cannot be done correctly with bounded cost, this stage fails rather than shipping nonrenewable logging.
- [ ] Run `RemoteClaims`, `RemoteActivity`, `RemoteClaimRestart` GREEN and existing `NativeRestart`; checkpoint. Do not move forward with a claim-only demo if useful remote activity still needs proximity.

## Task 4: Survival bootstrap and protected founding-party travel

**Files:** create `remote/RemoteFoundingService.java`, `FoundingCargo.java`, `FoundingTravel.java`; modify remote journal persistence and runtime; test `RemoteFoundingTests.java`, `RemoteFoundingRestartTests.java`.

**Interfaces:** `RemoteFoundingService.approve(ServerPlayer,UUID,long): StepResult`, `advance(ServerLevel,UUID): StepResult`, `cancel(ServerPlayer,UUID,long): StepResult`. `FoundingTravel.begin(ServerLevel,UUID,List<Integer>)` and `reconcile(ServerLevel,UUID)` preserve citizen IDs and entity UUIDs; follow the pinned native travelling lifecycle established by the prior expedition spike.

- [ ] Write `RemoteFounding` RED: four allocated existing adults (Mayor, builder, two foresters), finite home stock, no destination huts. Reject foreign/busy/duplicate/dead citizens, wrong owner and changed approved terrain without spending. Assert `citizensAfter == citizensBefore` and conserved item/component totals across approved staging, cancellation and failed arrival.
- [ ] Implement bootstrap escrow using the existing shipment-custody principles, stable transfer IDs and real extraction. Pay for native hut items, starter supplies and all placed staging/support blocks; the accepted layout bill includes them. Store escrow in addon saved custody until native hut inventories are available. Do not create a free chest or fill worker inventories from nothing.
- [ ] Validate dry supported arrival and protected staging before native citizen travel completes. Place paid hut anchors through native survival setup; queue construction later through Task 6. Retain previous home/work assignments until successful reassignment, and preserve recoverable travel state on blocked arrival.
- [ ] Implement compensation for reversible claims/reservations only; partially built sites and uncertain cargo become RECOVERY_NEEDED, not destructively rolled back. Run `RemoteFounding` and three-process `RemoteFoundingRestart` GREEN with interruptions before extraction, after extraction, before arrival and after arrival; checkpoint.

## Task 5: Dedicated Mayor and local resident behavior

**Files:** create `mayor/MayorAppointment.java`, `MayorService.java`, `JobMayor.java`, `MayorJobRegistration.java`; create `remote/RemoteWorkerLocality.java`; scoped mixins `MayorAssignmentMixin.java`, `RemoteWorkerLocalityMixin.java`; add `mayor/MayorTerminalModule.java` if required by the pinned native job/workplace contract below; test `MayorRoleTests.java`, `RemoteLocalityTests.java`.

**Interfaces:** `MayorService.appoint(ServerPlayer,UUID,int): StepResult`, `vacate(ServerPlayer,UUID): StepResult`, `available(ServerLevel,UUID): boolean`; `RemoteWorkerLocality.permits(ServerLevel,int citizenId,BlockPos target): boolean`. Register the native job type and its serialization in production initialization. The Mayor's management station is the approved outpost staging/terminal anchor, not a second Town Hall or borrowed builder job slot.

- [ ] Write `MayorRole` RED: appointed native citizen is visibly Mayor, stays unique through save/reload, cannot be auto-hired into another role, remains in native citizen manager, and missing/dead Mayor yields Needs Mayor without cancelling existing shipments/orders. Check original UUID and job identity, not just an appointment record.
- [ ] Integrate the native job/workplace lifecycle with the management station and local housing. Verify the job can operate without a normal hut work slot; if pinned native APIs require an addon building module, provide that module at the same terminal and test its lifecycle before continuing. Do not silently represent an idle citizen as a functioning job.
- [ ] Write `RemoteLocality` RED: builder ignores Home work orders; foresters and Mayor keep local homes; food/tools and courier requests do not create journeys across the gap. When a service is missing, assert a local blocker and no cross-gap path. Unmanaged Home citizens retain native behavior.
- [ ] Implement targeted assignment/path/service guards and local supply handling; preserve native combat and emergency behavior without promising invulnerability. Run both suites GREEN plus `CourierMembership`, `CourierNative`, `CourierTravel`; checkpoint.

## Task 6: Native construction, access and permitted upgrades

**Files:** create `remote/RemoteConstruction.java`, `ConstructionJournal.java`; modify runtime and development persistence; test `RemoteConstructionTests.java`, `RemoteConstructionRestartTests.java`.

**Interfaces:** `RemoteConstruction.next(ServerLevel,UUID): Optional<BuildingPlan>`, `queue(ServerLevel,UUID,UUID operationId): StepResult`, `reconcile(ServerLevel,UUID): StepResult`. Material needs come from the actual native work order/blueprint and preserve components.

- [ ] Write `RemoteConstruction` RED: level-zero paid starter anchors become completed native buildings through actual builder work. Verify supports/access before dependent modules; Mayor home and worker homes have real beds. Assert no building-level setter or forced completion is used by the production path.
- [ ] Implement dependencies, one journaled operation per module/level and native builder assignment. Reserve future upgrade space; recheck pack hash, terrain, permissions and native requirements immediately before queueing. Missing research, supplies or worker level becomes an explicit blocker.
- [ ] Add upgrade tests within cap and rejection above cap, research denial, changed pack, occupied custom structure, and player-created duplicate native order. Reconcile by stable operation/building/level identity; do not duplicate work after interruption or mistake unrelated player work for addon permission.
- [ ] Run construction and restart suites GREEN plus relevant `ArcologyLanternSafety`, `ArcologyBuilderRestart` checks; checkpoint. Existing unrelated pack failures are reported separately, never masked by level setters.

## Task 7: Mayor budgets, decisions, resource targets and resupply

**Files:** create `mayor/MayorPolicy.java`, `MayorPlanner.java`, `MayorReservations.java`, `MayorSupplyController.java`; modify `supply/NativeSupplyService.java` only for required safe coordinator entry points; test `MayorPolicyTests.java`, `MayorSupplyTests.java`, `MayorRestartTests.java`.

**Interfaces:** `MayorPlanner.tick(ServerLevel,UUID): StepResult`; `MayorReservations.reserve(ServerLevel,UUID,UUID operation,Map<ItemKey,Integer>): boolean`, `release(ServerLevel,UUID operation)`; `MayorSupplyController.reconcile(ServerLevel,UUID): StepResult`. Planner dispatches Task 4/6 operations; it does not construct blocks itself.

- [ ] Write `MayorPolicy` RED with priority order: operating shortages, explicit target, accepted construction, allowed upgrades. Default APPROVAL leaves a new proposal PROPOSED; AUTOMATIC proceeds only inside the authorized area/budget/staffing cap. Repeated identical shortages yield one proposal and one reservation.
- [ ] Pin budget math with literal examples: stock 40, Home reserve 16 permits at most 24; an existing reservation of 8 leaves 16 spendable. A target of 64 with stock 32 and committed incoming 16 has unmet demand 16. Components with the same item ID but different data cannot be substituted.
- [ ] Implement persisted per-operation reservations and shared-owner accounting; withdrawals, cargo and consumed construction materials are distinct states, never double-counted. Cancellation releases unused reservations, not already spent goods. Planner runs at most once per 100 ticks per settlement, with at most one new mutation; changing shortages reset a bounded retry deadline rather than creating duplicate work.
- [ ] Write `MayorSupply` RED: imports actual food, suitable tools and biome saplings into local huts without needing a second warehouse; exports only surplus; refills after consumption; full destinations and unavailable source pause safely. Bind/reuse routes by stable managed purpose, not mutable display name, and never overwrite a player-managed route.
- [ ] Implement both modes, queue progression and visible blockers. Run `MayorPolicy`, `MayorSupply`, three-process `MayorRestart`, existing `Supply`, `SupplyNativeRestart` GREEN; checkpoint.

## Task 8: Simple native-style founding and Mayor menus

**Files:** create `menu/RemoteMenuService.java`, `network/RemoteMenuPayloads.java`, `client/supply/WindowRemoteFounding.java`, `WindowMayor.java`; modify existing menu snapshot/session/network/main window classes; create `assets/colonyhorizons/gui/supply/founding.xml`, `mayor.xml` and localization; test `RemoteMenuTests.java`, extend client navigation regression and asset checker.

**Interfaces:** `RemoteMenuService.review(ServerPlayer,SiteRequest): SiteDraft`, `approve(ServerPlayer,UUID,long): StepResult`, `snapshot(ServerPlayer,UUID): CompoundTag`; bounded dedicated payloads carry IDs/revisions and validated inputs, not arbitrary blueprints or client-authored bills. Reuse actor-bound session/late-reply semantics.

- [ ] Write `RemoteMenu` RED: unauthorized/stale/replayed proposals reject without native effects; 33rd selected citizen/oversized field/list rejects before allocation; old local districts stay readable but cannot trigger remote privileges. Test server-only class loading.
- [ ] Replace the primary New Outpost flow with Found Outpost: home supply, Mayor/party, purpose, site or bounded surveyed area, Review, Approve. Main page stays two tabs. Existing local grouping is explicitly Local Districts, not the remote founding path.
- [ ] Mayor detail shows mode, current stage/action, precise material needs/destination, home reserve/budget, development cap and Pause/Resume. Default approval mode and finite allowances; no unlimited toggle. Review describes terrain work and claim footprint. Shortcuts must not require typing commands for normal play.
- [ ] Implement numeric/item allowance editing with pagination; selectors preserve edits on error; pending mutations are visibly disabled except Back. Closing late replies cannot reopen a screen or repeat founding. Run menu/server and client navigation suites GREEN; perform focused 1280×720/1920×1080 input/layout checks, then checkpoint.

## Task 9: Real remote operation acceptance

**Files:** create QA-only `RemoteColonyAcceptance.java`, `RemoteAcceptanceFixture.java`, `RemoteAcceptanceRestartTests.java`; extend foundation runner; create `docs/outposts/REMOTE-MAYOR-RESULTS.md`.

- [ ] Write named `RemoteAcceptance` assertions before implementation is declared ready: site outside native radius in a different biome, real approved Mayor and native construction, two productive foresters, at least three harvest/replant cycles per forester, tool exhaustion/replacement, food use/resupply, logs arriving Home and no cross-gap local worker paths. Assert original citizen identities and conserved finite supplies.
- [ ] Fixture may prepare a home colony, initial finite resources, explored terrain and initial trees. After approval it must use production entry points only: no remote fake subscriber, QA tickets, native level setter, injected completion, replenished stock or repeated artificial harvested drops. Owner remains at Home; record position/subscriber/ticket evidence. Natural growth limits must be accounted for in the test horizon, not replaced with claimed production.
- [ ] Run separate-process acceptance restart during founding, construction and in-flight delivery; reload neither recreates nor reseeds. Include Mayor loss/replacement, owner logout/login and automatic-mode shortage creating exactly one permitted expansion proposal. Bound each runner phase to 900 seconds; split long real-life stages into explicit saved phases rather than lowering production assertions.
- [ ] Run existing MenuRead/MenuDistricts/MenuRoutes/MenuProtocol, Records, Supply, NativeRestart, CourierMembership/CourierNative/CourierTravel/CourierRestart, SupplyNativeRestart and ForesterWithCouriers sequentially. Record actual IDs and counts; any failure blocks the appropriate release gate.
- [ ] Review evidence against every spec acceptance clause and checkpoint. Model tests plus a command demo are insufficient for this stage.

## Task 10: Exact packaged build and isolated colony-copy upgrade

**Files:** modify `build.gradle` with separate next-alpha output task, `src/supplyDev/resources/colonyhorizons.mixins.json`, `tools/Test-SupplyDevPackage.py`, `tools/Test-SupplyPackagedServer.py`; create `tools/Test-RemoteMayorPackage.py`, `docs/release/ALPHA-3-TESTING.md`; update user documentation and results.

- [ ] Extend package tests first: production initializer, all intended hooks/resources and version are present; no QA classes/assets/profile fixture/delay hooks or personal machine paths. Wrong/stale JAR and missing required hook must fail before launch.
- [ ] Build `test supplyDevJar supplyQaJar -Pmod_version=0.1.0-dev.remote.1`; inspect exact JAR hash and launch it on an isolated normal NeoForge server with a separate QA harness exercising remote claims, tickets, Mayor construction and safe supply. The harness must call production founding, not reproduce it.
- [ ] Prepare alpha.3 in a separate output directory after Task 9 and package gates pass. Keep prior public build tasks/artifacts and installed alpha.2 unchanged. Test the exact alpha.3 JAR, not only compiled development classes.
- [ ] With the real game closed before copying, make a separate disposable copy of the playtest pack and a chosen colony save. Install only into the copy. Verify upgrade preserves existing native huts, citizens, assignments, inventories and alpha.2 fixes; verify new remote founding from that copy, then save/reload and close cleanly. Copying a live save is not an acceptable backup.
- [ ] Compare the original installed manifest and original save hashes, write known limits and exact install/rollback instructions. No publish/install/push occurs here. Mark the artifact ready for the user's colony only after this isolated upgrade check passes.

## Checkpoints and stop conditions

Each task ends with its named failing-then-passing evidence, a scoped diff review and a recoverable checkpoint. Existing native functions are not renamed/refactored solely for aesthetics. Use a fresh baseline of installed files and release artifacts before execution and verify it at completion.

If remote activity, Mayor native job integration, terrain safety or citizen custody fails its real native test, resolve that integration before proceeding to UI or release. Do not substitute district grouping, a decorative Mayor, temporary test teleportation or proximity-dependent operation and call the feature complete.

## Self-review and execution handoff

Spec mapping: remote authority/claims and owner-online operation → Tasks 1–3; survival founding/citizens → Tasks 4–5; Arcology construction/upgrades → Task 6; both Mayor modes/budgets/supplies → Task 7; usable player flow → Task 8; genuine remote acceptance → Task 9; alpha.2 preservation and exact release package → Task 10. Each Review Focus risk has explicit tests in its owning task. Added endpoint-ticket cap and survey/planner cadence are bounded implementation choices, not promises of unlimited loading or search.

Plan is prepared for review. Execution remains inline in this chat, matching the current workflow. No production code has been changed for this plan, no tests are represented as already passing, and no installed files have been modified.


## Warehouse-first amendment execution order

User steering on 2026-10-14 changes the logistics architecture before final endurance, expansion approvals and menus. Follow the corresponding spec amendment. Keep existing development regressions and installed alpha.2 intact.

1. Add failing layout/party tests for five original founders, five homes, builder + warehouse + courier + two foresters, measured nonoverlapping upgrade envelopes and finite reviewed bootstrap materials. Verify native warehouse/courier prerequisites before selecting anchors. Preserve schema compatibility without granting new authority to existing records.
2. Implement finite bootstrap custody and native construction order builder -> warehouse -> courier, with no repeating direct Home-to-workplace supply. Test shortages before departure, partial transfer, native completion, interrupted save and safe retry. Do not mark old direct-hut routes warehouse-compliant.
3. Add warehouse/rack inventory and durable checkpoint tests: exact components, full destination, replacement/unload, partial inventories, returns and separate-process restart. Implement warehouse-only managed endpoints and shared reservations.
4. Test and implement original local courier appointment, warehouse association and native request/pickup locality. Test Home/other-outpost warehouse resolution rejection before task scheduling, not only path refusal. Preserve ordinary unmanaged-colony behavior.
5. Run full native construction and production endurance with all original citizens housed, local warehouse delivery/pickup, protected imports/exports, natural forestry cycles, food/tool replacement, owner absence and restart. All resources finite; no production-level setters, fake remote subscribers, ongoing fixture restocking or generated drops.
6. Complete persisted expansion approval review against the amended layout and logistics readiness. Then implement simple native-style menus showing warehouse/courier readiness, precise needs and existing bounded Mayor controls.

Checkpoint: RemoteSettlementCompletion passed one integrated scenario in run332d8a61043845db90d8e2192392509e. Seven old-layout buildings completed with four original founders housed; initial overflow stock used ordinary vanilla hoppers, loaded before approval, with exact stock + dispatch conservation. No postapproval replenishment. This does not cover sustained production, the new warehouse layout or completed-settlement restart.
