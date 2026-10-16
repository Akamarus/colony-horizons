# Surveyor Hut and Resource Outposts Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans. Implement inline, task by task; one independent final review. User explicitly requested implementation here.

**Goal:** A native Surveyor discovers and collects biome resources without player travel, and offers buildable, automatically prepared resource outposts from its hut.

**Architecture:** Keep survey records, sample custody and terrain operations separate from native worker/building records and founding grants. Add a registered hut/job and simple menu, a bounded persisted expedition service, resumable builder preparation and versioned production profiles. Reuse native producers, local couriers and protected warehouse routes.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.250 and pinned MineColonies/Structurize/BlockUI dependencies.

**Spec:** `docs/superpowers/specs/2026-10-15-surveyor-hut-and-resource-outposts-design.md`.

## Global constraints

- Installed playtest build and world unchanged. Work in existing isolated `feat/safe-supply-routes` checkout; preserve prior dirty source with a fresh baseline.
- Overworld only; native ownership, research and production limits remain authoritative.
- Defaults: radius 4096, 32 candidates, one expedition/colony, two inspections/server, 49 tickets/inspection, one new chunk request/tick globally; maximum 16 real sample items/expedition.
- Four new founders once/site; fourth is the producer. Legacy forestry records retain their original identities/layout/roles.
- No fictional survey loot, synchronous menu chunk generation, player terraforming requirement, unbounded wilderness pathfinding or changed installed artifact.
- Ration default: 8 bread per expedition. Search requires a living, assigned, unpaused and healthy worker and an online owner. Reassignment/recall/cancel releases tickets and returns existing custody.
- Terrain defaults: at most 8 blocks cut/fill vertically per column and 32768 operations/site. Reject player/protected structures, block entities, claims, lava and deeper unsupported foundations. Preserve aquatic production areas.

## Review focus

1. Registering addon huts must not alter native huts or lose ordinary build/upgrade/inventory controls (Task 1).
2. Unexplored generation, overlapping expeditions and owner logout must not leak tickets or stall server ticks (Task 3).
3. Harvest/drop/save failures must not duplicate samples or erase unrelated protected blocks (Tasks 2–3).
4. Newly placed containers and uneven arrival sites must not be bulldozed or deadlock the initial builder (Task 4).
5. Non-forestry producers must not inherit the legacy FORESTER identity or claim unsupported renewable resources (Task 5).

## Commands and evidence

Set Java 21 and the existing Gradle cache for each run. Use `powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite <suite> -ArcologyPath '<installed Arcology Horizons folder, read-only>' -TimeoutSeconds 900`. Add new suites to the runner's allowed/exact case lists and `FoundationGameTests`. Every suite must write complete named reports. Use RED then GREEN per task, not compilation alone. Do not rebuild development classes while a development-classpath server is running.

### Task 1: Native hut and job

**Files:** create `surveyor/SurveyorRegistration.java`, `BlockHutSurveyor.java`, `BuildingSurveyor.java`, `JobSurveyor.java`; initialize in `remote/RemoteRuntime.java`; resources under `assets/colonyhorizons` and recipe under `data/colonyhorizons`; create `qa/outpost/SurveyorHutTests.java`; Arcology transformation tool `tools/Build-ArcologySurveyor.py` with asset verification.

**Interfaces:** `SurveyorRegistration.SURVEYOR` BuildingEntry holder; `SurveyorRegistration.JOB` JobEntry holder; `BuildingSurveyor extends AbstractBuilding`; `JobSurveyor extends AbstractJob`. Expose `SurveyorAccess.require(ServerPlayer,int): BuildingSurveyor` for server-authorized menu use and `SurveyorAccess.worker(BuildingSurveyor): Optional<ICitizenData>` for expedition execution.

- [ ] Add `SurveyorHut` cases for registered block/item/building/job, native placement and assignment, serialization/reload, unrelated native registration unchanged, and hut research/level controls. Run RED against absent registry entries.
- [ ] Register the native building with worker/min-stock/stats modules and native building view; add ordinary recipe, English names, model/loot and safe render bindings. Reuse a matching Arcology module as a controlled derived asset, replacing its hut and removing incompatible worker tags for all five levels.
- [ ] Run GREEN and native building/pack discovery regression. Verify inventory/build/upgrade controls in isolated client.

### Task 2: Resource catalog and durable survey records

**Files:** create `surveyor/SurveyResource.java`, `SurveyRecord.java`, `SurveySavedData.java`, `SampleJournal.java`, `SurveyResourceCatalog.java`, `qa/outpost/SurveyorRecordTests.java`, `SurveyorResourceTests.java`.

**Interfaces:** resource entries bind exact item, matching block states, tool requirement, profile and renewal capability. `SurveySavedData.get(ServerLevel)` owns persisted records keyed by UUID; `SurveyRecord` contains colony/owner/hut/worker ID+UUID, target, revision/state/cursor/candidates and custody. State changes reject stale revisions. `SurveyResourceCatalog.find(ResourceLocation): Optional<SurveyResource>` never silently substitutes targets.

- [ ] RED tests for round-trip, unknown resource/state, reused citizen ID, duplicate sample receipt, stale mutation, search/sample limits and exact wood/flower/seed/water-plant identity.
- [ ] Implement bounded codecs and conservative catalog entries for the requested categories. Define research/renewal rules from pinned native APIs. Unverified native items remain discovery-only with an explicit reason.
- [ ] GREEN all record/catalog tests and legacy free-founder records.

### Task 3: Automated expeditions, real samples and hut menus

**Files:** create `surveyor/SurveyService.java`, `SurveyTickets.java`, `SurveyHarvest.java`, `SurveyorAccess.java`; native hut view/module and `client/supply/WindowSurveys.java`; extend `menu/SupplyMenuSessions.java`, payload actions and snapshot; modify Town Hall entry mixin and menu text; create `qa/outpost/SurveyorExpeditionTests.java`, `SurveyorMenuTests.java`, `SurveyorRestartTests.java`.

**Interfaces:** `SurveyService.start(ServerPlayer,int,ResourceLocation): UUID`; `tick(ServerLevel)`; `cancel(ServerPlayer,UUID,long)`; immutable menu report DTOs. `SurveyTickets` independently owns bounded survey tickets and pending chunk futures. `SurveyHarvest` prepares and applies one real block harvest with finite tool/loot/cargo receipt; ambiguous handoff stops for recovery.

- [ ] RED cases: actual resource outside loaded terrain, biome hint without target, two-colony global limits, unavailable worker/food, protected area, correct shears loot, finite samples, full inventory, duplicate/replayed requests and owner logout. Test restart during travel/harvest/return and hut demolition.
- [ ] Implement deterministic bounded candidate enumeration, async chunk inspection and durable result records. Use protected expeditions with same native worker identity; do not require Home and target to be player-loaded together. Return actual samples through hut inventory and local couriers.
- [ ] Implement native-style target selection, survey progress/results and existing outpost pages at the hut. Enforce server-side hut/owner validation. Remove addon Town Hall button while preserving all native controls.
- [ ] GREEN suites plus packaged menu/protocol checks. Verify no native Home job is reassigned and all survey tickets release after cancellation/restart.

### Task 4: Builder site preparation

**Files:** create `remote/SitePreparationPlan.java`, `SitePreparationJournal.java`, `SitePreparationService.java`; modify `RemoteSitePlanner`, `RemoteFoundingService`, `FoundingPlacement`, `FoundingConstructionSupplies`, `FoundingTravel`, `RemoteDevelopmentData`, native builder integration; tests `SurveyorTerrainTests`, `SurveyorPreparationRestartTests`.

**Interfaces:** `SitePreparationPlan.review(ServerLevel,SiteRequest,List<BuildingPlan>)` returns immutable bounded block operations, material estimate and safe staging. `SitePreparationService.advance(ServerLevel,UUID)` performs worker-reachable paid operations. Persist approved original/target block states and operation cursor separately from construction journals.

- [ ] RED for trees/hills accepted as costed work, supported dirt fill, native tools/drop recovery, shallow water containment, preserved aquatic area, deep void/lava/claim/container rejection, protected change after survey, interrupted block operation and cancelled staging.
- [ ] Replace blanket flat/clear requirement only for new surveyed plans. Native builder performs preparation from an approved safe temporary anchor, with finite custody and navigation; secure bootstrap before grant/arrival. Revalidate each block. Complete permanent builder hut, then warehouse/courier. Remove empty temporary staging safely after permanent custody is verified.
- [ ] GREEN terrain and restart cases plus legacy founding/placement/warehouse suites. Prove no floating blocks and no manual terrain edits after test approval.

### Task 5: Versioned resource outposts

**Files:** create `remote/ProductionProfile.java`; modify `RemoteModels`, `FoundingRoster`, grant journal, layout/catalog, founding/menu services, Mayor housing/production/logistics and operating supplies; native field construction adapters; tests `ResourceOutpostTests`, `ResourceOutpostRestartTests`.

**Interfaces:** `ProductionProfile` binds target, native producer job/hut, fields, research and required starter stock. New policy/role version stores explicit PRODUCER without changing FREE_FOUR_V1/FORESTER decoding. Founding from a survey references its durable ID and revalidated preparation plan.

- [ ] RED for four-founder profiles, no Home workers taken, exact native job/hut/field, locked research/capacity, starter-stock custody, legacy role preservation and wrong item rejection.
- [ ] Implement forestry, florist, farmer and Plantation profiles with native fields/extensions, finite starter sample transfer and explicit renewable item selection. Maintain native limits and ordinary hut menus. Export exact requested items through warehouse routes.
- [ ] GREEN real grow/harvest/delivery cases for every advertised target, plus free-founder/legacy restart, cancellation and upgrade tests.

### Task 6: Whole-feature verification and handoff

**Files:** isolated packaged runner extensions; `docs/outposts/SURVEYOR-RESULTS.md`; release artifact `0.1.0-dev.surveyor.1`.

- [ ] Run packaged native registration and menus, unexplored surveys, interruption tests, prepared founding and endurance for each producer profile. Verify reseeding, tools, food and requested-resource deliveries from finite supplies.
- [ ] Test migration and founding in a stable copied-colony backup. Hash installed addon before/after; never open its live world.
- [ ] One independent final review against this feature's initial source snapshot; reproduce and fix Important/Critical findings with RED/GREEN tests.
- [ ] Preserve attributable commits/checkpoint, results and actual limitations. No install or publication without a separate request.

## Plan self-review

All design sections map to Tasks 1–6. Survey tickets and grant tickets remain separate; sample receipts and founding grants use distinct IDs. New policy/profile prevents old FORESTER semantics from changing. Terrain staging resolves the hut-before-builder bootstrap dependency. Exact tests and commands must be registered before implementation claims; absent native capability is not silently advertised.
