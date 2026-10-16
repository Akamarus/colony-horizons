# Exploration Hut Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline in the current worktree. One independent whole-feature review at the end; no implementing subagents. Steps use checkbox syntax.

**Goal:** Make biome resources obtainable through real discovery and paid simulated expeditions from a native MineColonies Exploration Hut, without an outpost.

**Architecture:** Keep Surveyor registry IDs and real discovery, add authoritative discovery evidence and a separate gathering journal, and share protected native-worker travel and return custody. Discovery examines terrain; gathering advances a persisted timer and creates only its snapshotted reward. Both use one colony-wide active-expedition guard and the same hut's native inventory.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.250, pinned MineColonies 1.1.1403, existing Gradle/native GameTest and packaged QA harnesses.

**Spec:** `docs/superpowers/specs/2026-10-15-exploration-hut-design.md` (approved by user).

## Execution outcome (2026-10-15)

Tasks 1–5 are implemented and verified; Task 6 evidence is in `docs/outposts/EXPLORATION-RESULTS.md`. The detailed checklist below is the original implementation prescription, not a live progress counter. Actual suite case grouping and report IDs are recorded in the results and local ledger. Task 1 was committed separately; remaining feature changes are saved in one scoped completion commit rather than the proposed per-task commits. Existing unrelated dirty/untracked work is preserved.

Normal-client interaction verified the already-built native fixture and expedition controls; it did not manually repeat schematic placement or a complete upgrade. Those native modules/blueprints were retained and checked by the automated hut suite. The server harness was adjusted to allow initial chunk generation to settle before requiring a clean shutdown. A newly observed disappearing error message received a RED/GREEN packaged client regression and independent review follow-up. Installed build, live worlds and public releases remain unchanged.

## Global constraints

- Work in the existing `.setup/safe-supply-routes` checkout. Preserve its dirty/untracked predecessor work. Take a feature-start snapshot of affected files, including untracked files, for final review; Git HEAD alone is not the baseline.
- Outpost implementation is paused. Preserve existing outpost runtime and saved data; remove its entry only from the exploration page. Do not delete old plans or claim the outpost system is finished.
- Keep registry IDs, native hut/job classes, blueprint paths, inventories, ordinary assignment/build/upgrade controls and five hut levels. User-facing names become Exploration Hut and Explorer.
- Discovery: eight bread, existing required tool, at most 32 candidates, asynchronous inspection, at most two inspection leases per server. Real sample plus recorded biome and successful return are required.
- Gathering: one active expedition per colony shared with discovery; 6,000 eligible ticks, two bread per trip; repeat opt-in, initially off. No offline catch-up, wilderness worker/courier exposure, or remote terrain loading during gathering.
- Exact yield/wear: logs 16/16 axe; saplings/propagules 4/0; flowers 8/0; wheat seeds 8/0; vines 8/8 shears; kelp 16/0; seagrass 8/8 shears. Fixed wear applies to enchanted tools too. Pumpkin and melon remain discovery-only.
- Charge once, deliver once, preserve custody on interruption/full inventory, quarantine ambiguous external inventory transactions. Do not weaken `SurveyCargo`'s real-harvest meaning.
- Existing completed surveys without biome evidence are historical only. No automatic fabricated unlocks. Ownership changes require rediscovery.
- Only development outputs under this worktree. No install, publication, push or live-world editing. Installed hash baseline: `FCD323B1C7E04146BF237684E78D1672B95578D807C95CE1F4550519BCAA3F92`.

## Review focus

1. A crash between native inventory save and journal receipt must not duplicate payment or rewards (Tasks 2, 4).
2. Discovery and gathering competing through two huts must not reserve the same worker or run two expeditions in one colony (Tasks 2, 3, 5).
3. Existing returning surveys, former owners, and reports whose hut was removed must not acquire forged gathering authority or lose cargo (Tasks 1, 4).
4. Healthy/ill, paused/reassigned, disconnected, missing-hut and full-inventory transitions must not strand an untracked native citizen or start another paid trip (Tasks 3, 4).
5. Development tests can pass while packaged registration, menus or native courier extraction fail; exercise real production artifacts and native courier behavior (Tasks 5, 6).

## Commands and test harness contract

Run from this worktree. Before each Java invocation:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='./.setup/gradle-arcology-recovery'
```

All named new suites below must be registered in `src/main/java/dev/colonyhorizons/qa/outpost/FoundationGameTests.java` and the ValidateSet/exact case lists of `tools/Test-OutpostFoundation.ps1`. A suite passes only with exit 0 and every expected unique case present and PASS. Run sequentially; do not rebuild/edit Java while a development-classpath JVM is running. Save RED/GREEN logs and report IDs in this feature's ledger.

The exact command template, substituting the suite listed by each task:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ExplorationDiscovery -ArcologyPath build/surveyor-pack -TimeoutSeconds 900
```

Use the existing isolated native fixtures and `SupplyTests.check`, `ProbeResult`, `ProbeReport`. Assertions below are the required contract, not a request to introduce a mock test framework. Short timers may be reached through validated journal fixtures in unit-like tests; at least one native end-to-end test must execute all 6,000 real eligible ticks with no production timing override.

## File responsibilities and dependency order

All Java paths below are relative to `src/main/java/dev/colonyhorizons/`.

- Task 1: `surveyor/DiscoveryEvidence.java` (new); `SurveySavedData.java`, `SurveyService.java`: capture and publish actual discoveries; remove site selection from active discovery.
- Task 2: new `surveyor/GatheringOffer.java`, `GatheringCatalog.java`, `GatheringRecord.java`, `GatheringSavedData.java`, `GatheringSupplies.java`, `ExpeditionAccess.java`: exact rewards, durable state, payment and shared exclusion.
- Task 3: new `surveyor/GatheringService.java`, `ExpeditionTravel.java`; `SurveyService.java`, `remote/RemoteRuntime.java`, `compat/mixin/FoundingTravelGuardMixin.java`: native travel, ticking and delivery.
- Task 4: recovery tests and targeted corrections in Tasks 1-3 files; no new independent recovery subsystem.
- Task 5: `menu/SurveyMenuService.java`, `SupplyMenuSessions.java`, `network/SupplyMenuPayloads.java`, `SupplyMenuNetworking.java`, `client/supply/WindowSurveys.java`, `WindowSurveyorTab.java`; language and `gui/supply/surveys.xml` resources: native-style controls.
- Task 6: `qa/MenuPreviewClient.java`, `qa/MenuLiveFixture.java`, packaged QA tools, release-result documentation and whole-feature review.

### Task 1: Real discovery evidence, independent of settlement sites

**Interfaces:** `DiscoveryEvidence(UUID survey, int colony, UUID owner, ResourceLocation dimension, ResourceLocation biome, ResourceLocation target, BlockPos source)` is immutable and versioned. Add `SurveySavedData.evidence(UUID): Optional<DiscoveryEvidence>` and `unlocked(UUID owner, int colony, ResourceLocation target): Optional<DiscoveryEvidence>`. Only `unlocked` is gathering authority. A candidate evidence receipt alone is insufficient.

- [ ] Add `qa/outpost/ExplorationDiscoveryTests.java`, suite `ExplorationDiscovery`. Cases: `actual_biome_and_resource`, `no_sample_no_unlock`, `pending_return_no_unlock`, `completed_return_unlocks`, `legacy_report_locked`, `foreign_owner_colony_locked`, `removed_source_still_unlocked`, `no_settlement_site_search`, `legacy_return_preserves_cargo`. Assert biome equals the registry key read from the actual sample position, not the hut or a biome hint.
- [ ] Run `ExplorationDiscovery` RED: new evidence/unlock assertions fail on absent behavior, not fixture setup. Extend the native expedition test to prove the source was harvested, the same worker returns, and no site search is created.
- [ ] Capture the biome key from already loaded terrain before harvest and write it into the same durable sample-intent boundary. Validate it against pending sample identity, source and exact resource. Publish unlock only for COMPLETE with positive committed sample count and no pending custody. Keep old SavedData version readable via optional evidence; reject malformed evidence rather than silently authorizing it. Remove `SurveySiteService.advance` from the expedition path; collected cargo returns directly. Preserve old site records and existing returning cargo.
- [ ] Run GREEN: `ExplorationDiscovery`, updated `SurveyorExpedition`, `SurveyorHarvest`, `SurveyorRecords`, `SurveyorCargo`. Expected: every named check passes; old reports load but cannot gather.
- [ ] Record report IDs and commit only Task 1 files plus relevant test registration, preserving predecessor changes in the baseline snapshot. Suggested message: `feat: record resource discovery without outpost site selection`.

### Task 2: Exact offers, paid custody and bounded gathering records

**Interfaces:** `GatheringOffer(ResourceLocation target,int count,int ticks,int bread,SurveyResource.Tool tool,int wear)`; `GatheringCatalog.find(ResourceLocation): Optional<GatheringOffer>`. `GatheringRecord` stores UUID, revision, colony/owner/hut, worker ID+UUID, discovery UUID, snapshotted offer, remaining ticks, repeat flag and state. States: PREPARING, GATHERING, RETURNING, COMPLETE, RECOVERY_REQUIRED. `GatheringSavedData.get(ServerLevel)`, `find(UUID)`, `active(int colony)`, compare-and-replace transitions, custody access and verified checkpoint follow the existing SavedData pattern. `ExpeditionAccess.requireIdle(ServerLevel,int colony)` checks both systems before either reserves anything.

`GatheringSupplies.prepare(IItemHandlerModifiable,GatheringOffer): Departure`; `Departure.commit(HolderLookup.Provider,Consumer<CompoundTag>,Runnable flush)` uses `SupplyInventoryBatch` and `SupplyCheckpoint`. Persist exact post-wear tool, two paid bread, immutable offer and earned output separately from real survey samples.

- [ ] Add `ExplorationRecordsTests.java` and `ExplorationSuppliesTests.java`, suites `ExplorationRecords`, `ExplorationSupplies`. Assert every supported catalog item matches the table, logs have `(16,6000,2,AXE,16)`, kelp `(16,6000,2,HAND,0)`, all 36 supported targets are enumerated, and pumpkin/melon have no offer. Test invalid states/quantities, duplicate IDs, mutable ItemStack isolation, stale revision, a second active expedition from another hut, and discovery/gathering mutual exclusion.
- [ ] Run both suites RED. Payment assertions: two bread removed once; a named enchanted axe retains components and loses exactly 16 durability; 15 remaining durability refuses payment with inventory unchanged; exactly 16 permits one trip and breaks the tool; recall does not refund food/wear; replay cannot charge again. Inject failure at pending-write, apply, native-flush and final-receipt boundaries, preserving or quarantining the exact custody.
- [ ] Implement the records, strict versioned codecs, authoritative immutable offer lookup and staged payment. Persist PREPARING reservation before payment, and paid GATHERING state before hiding the worker. Both `SurveyService.start` and gathering start use shared exclusion, including RECOVERY_REQUIRED reservations. Timer changes mark dirty; checkpoint transitions and at most every 100 eligible ticks. A crash may conservatively lose unsaved progress, never grant early credit. No synchronous disk write every tick.
- [ ] Run GREEN: `ExplorationRecords`, `ExplorationSupplies`, `SurveyorSupplies`, `SurveyorRecords`. Expected: all cases PASS, no vanilla or discovery payment changes.
- [ ] Ledger and scoped commit: `feat: add paid gathering journal and resource offers`.

### Task 3: Native protected trips, timer, return and repeat

**Interfaces:** `GatheringService.start(ServerPlayer actor,int colony,BlockPos hut,ResourceLocation target,boolean repeat): UUID`; `tick(ServerLevel)`; `recall(ServerPlayer,UUID,long revision)`; `status(ServerLevel,UUID): String`; `unload(ServerLevel)`. Start resolves discovery and offer server-side and validates native hut/worker. `ExpeditionTravel.depart(ServerLevel,ICitizenData)`, `blocksEntity(ServerLevel,UUID)`, `blocksSpawn(ICitizenData)`, `release(ServerLevel,ICitizenData,BlockPos)` share identity-safe suppression/release mechanics with discovery; inventory delivery remains journal-specific.

- [ ] Add `ExplorationGatheringTests.java`, suite `ExplorationGathering`. Cases: `native_worker_departure`, `real_6000_tick_trip`, `no_remote_tickets_or_edits`, `exact_output_once`, `same_worker_returns`, `owner_stays_home`, `recall_before_reward`, `recall_after_reward`, `repeat_pays_each_trip`, `shortage_stops_repeat`, `duplicate_tick_no_credit`. Use a real assigned native Explorer, actual hut inventory and online test owner. At tick 5,999 assert zero earned output; tick 6,000 earns exactly the offer once. Native end-to-end test timeout must cover return and at least two repeat trips where needed.
- [ ] Run RED: no simulated trip behavior exists. Keep timer-only assertions separate from the native 6,000-tick proof so fixture shortcuts cannot replace it.
- [ ] Implement payment -> native departure -> GATHERING -> durable reward/RETURNING -> staged hut delivery -> native arrival -> COMPLETE. Earn the fixed reward only on validated timer completion, checkpoint before exposure to inventory. Suppress duplicate level-tick processing. Owner offline/wrong dimension pauses; native worker identity mismatch quarantines. Illness, work pause or reassignment clears repeat and returns custody without a substitute. Full output inventory retains cargo and stops repeat. Recall clears repeat at any active stage; do not erase an already earned reward.
- [ ] Register gathering tick/unload and shared spawn guards in `RemoteRuntime` and `FoundingTravelGuardMixin`. Refactor only the common travel mechanics from SurveyService, keeping its sample transaction unchanged. Gathering opens no remote inspection lease; return leases are limited to the original hut's local 3x3 chunks and are released on offline, completion and unload.
- [ ] Repeat may start only after successful delivery and native return. Recheck discovery ownership, eligibility, bread, wear and simulated inventory room for output plus returning tool. If any check fails, end repeat with a persisted visible reason; require user action to restart. Prevent retries from repeatedly charging provisions. Do not require a warehouse.
- [ ] Run GREEN: `ExplorationGathering`, `ExplorationRecords`, `ExplorationSupplies`, `SurveyorExpedition`, `SurveyorTickets`, `FreeFounderFlow`. Expected: all named assertions pass and outpost spawn guards remain intact.
- [ ] Ledger and scoped commit: `feat: run protected exploration gathering trips`.

### Task 4: Process restart, interrupted ownership and native courier collection

**Files:** add `qa/outpost/ExplorationRestartTests.java`, `ExplorationLifecycleTests.java`, `ExplorationCourierTests.java`; extend only affected runtime and test-runner code.

**Interfaces:** extend runner with `-ExplorationFault` ValidateSet `payment-pending,payment-native,timer,earned,delivery-pending,delivery-native,delivered`. Suite `ExplorationRestart` uses separate `seed` and `reload` JVM phases on the same disposable world, and passes the fault through a QA-only property. Production artifact contains no fault hooks. QA can use dependency-injected checkpoint writers and durable fixture records, never a production reward-shortcut flag.

- [ ] Write failing regression cases first. `ExplorationLifecycle`: `full_hut_holds_reward`, `hut_removed_holds_cargo`, `owner_logout_pauses`, `owner_dimension_pauses`, `ownership_change_locks`, `worker_reassigned_returns`, `worker_ill_returns`, `worker_paused_returns`, `missing_worker_quarantines`, `repeat_requires_manual_restart`. `ExplorationCourier`: real native courier collects generated output from the hut into native storage after a completed trip; payment/tool minimum stock is respected and no Home job is reassigned.
- [ ] Run `ExplorationLifecycle` and `ExplorationCourier` RED against current behavior. For each restart fault run the exact command template with `-Suite ExplorationRestart -ExplorationFault <value>`. Each seed must create the specified persisted boundary and exit; reload verifies balances, timer and citizen identity rather than rebuilding the fixture. Pending/ambiguous inventory operations must report RECOVERY_REQUIRED; clean timer/earned/delivered states resume without duplication or another debit. A pre-return legacy survey also survives a separate-process reload.
- [ ] Make the smallest corrections needed. Recovery retains cargo and identities; no destructive automatic repair. Add native courier/request-module integration only if the real collection test proves existing native behavior insufficient. Avoid synthetic direct transfers labelled as courier testing.
- [ ] Run GREEN: all seven restart invocations, `ExplorationLifecycle`, `ExplorationCourier`, and the complete Task 1-3 suite set. Expected: exact unique PASS reports; no active survey/return tickets left after completion or unload.
- [ ] Ledger and scoped commit: `fix: preserve expedition custody through restart and interruption`.

### Task 5: Simple native Exploration Hut controls

**Interfaces:** append `START_GATHERING`, `RECALL_GATHERING` actions without changing earlier enum ordinals. Bump networking protocol from `supply-menu-2` to `supply-menu-3` because new actions/snapshots require matching peers. Start form accepts only selected target and repeat boolean; server computes discovery, costs and reward. Recall carries expedition ID and revision. Preserve existing session, ownership, proximity and hut validity checks.

Snapshot resource rows add `unlocked`, `gatherable`, `biome`, `yield`, `durationTicks`, `bread`, `wear`, `tool`; active view supplies expedition kind, revision, remaining ticks, repeat and bounded status. Never expose mutable authoritative tags to client input. Retain historical reports, labelled Rediscover where evidence is missing.

- [ ] Add `ExplorationMenuTests.java`, suite `ExplorationMenus`. Cases: `locked_discover_only`, `unlocked_costs_and_yield`, `discovery_only_fruit`, `legacy_rediscover`, `foreign_hut_denied`, `replayed_start_no_charge`, `forged_yield_ignored`, `stale_recall_rejected`, `destroyed_hut_denied`, `outpost_entry_removed`, `native_tabs_preserved`. Verify exact costs match GatheringCatalog and server rejects locked-resource gathering even if the UI is bypassed.
- [ ] Run RED: current UI and handler lack gathering. Update existing `SurveyorMenuTests` expectations only where the approved design changes site-report/outpost presentation; retain its isolation and authorization assertions.
- [ ] Implement one list and details panel with Discover / Gather once / Repeat shown by context and Recall for active trips. Retain findings view. Use exact copy: `Simulated gathering; distant blocks are not harvested.` Show fixed tool wear including enchantments and five minutes at normal tick rate. Bound/wrap status text; fit controls within the existing 400x250 book. Remove `outposts` button/handler from this page only. Keep unrelated route/outpost menus and packets working.
- [ ] Update `assets/colonyhorizons/lang/en_us.json`, `assets/colonyhorizons/gui/supply/surveys.xml`, native tab labels and relevant tests. Do not rename `blockhutsurveyor`, `surveyor`, schematic files or job modules. Add the exploration screens to `qa/MenuPreviewClient.java`/`MenuLiveFixture.java`; update `tools/Test-SupplyMenuAssets.py` and packaged client assertions to exact case IDs, not the existing inconsistent five/seven-case navigation marker.
- [ ] Run GREEN: `ExplorationMenus`, `SurveyorMenus`, `SurveyorHut`, `MenuProtocol`, plus `tools/Test-SupplyMenuAssets.py`. Native tests prove standard upgrade/inventory/worker controls remain available. Perform isolated client visual checks for locked, unlocked, running, full-hut and historical states.
- [ ] Ledger and scoped commit: `feat: add native exploration gathering controls`.

### Task 6: Packaged validation and final handoff

**Files:** extend `tools/Test-SupplyPackagedServer.py`, `tools/Test-SupplyPackagedClient.py`, `tools/Test-SurveyorArtifact.py`, `tools/Test-SupplyDevPackage.py`; add `docs/outposts/EXPLORATION-RESULTS.md`. Keep development artifact separate from installed releases.

- [ ] Extend packaged server suite allowlist/exact expected case IDs for `ExplorationDiscovery`, `ExplorationGathering`, `ExplorationLifecycle`, `ExplorationCourier`, `ExplorationMenus`, `ExplorationRestart`. Add `--exploration-fault` with the seven Task 4 values; `--suite ExplorationRestart` automatically runs seed then reload in separate processes using the same disposable world and passes `colonyhorizons.probePhase` to each process. Copy `build/surveyor-pack` only into disposable packaged instances. Verify required pack/dependencies exist before launch; never fall back to a live world. Exercise one legacy copied-world migration using a backup copied under this worktree.
- [ ] Build and check artifacts using these commands (Python executable may be assigned to `$explorationPython`):

```powershell
./gradlew.bat supplyDevJar supplyQaJar '-Pmod_version=0.1.0-dev.exploration.1' --no-daemon --console=plain
$explorationPython='<user-home>/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
& $explorationPython tools/Test-SurveyorArtifact.py build/supply-dev-libs/colony-horizons-0.1.0-dev.exploration.1.jar
& $explorationPython tools/Test-SupplyDevPackage.py build/supply-dev-libs/colony-horizons-0.1.0-dev.exploration.1.jar --version 0.1.0-dev.exploration.1
& $explorationPython tools/Test-SupplyMenuAssets.py
& $explorationPython tools/Test-SupplyPackagedServer.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.exploration.1.jar --version 0.1.0-dev.exploration.1 --suite ExplorationGathering
& $explorationPython tools/Test-SupplyPackagedClient.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.exploration.1.jar --version 0.1.0-dev.exploration.1 --navigation
```

Expected: build succeeds, production JAR contains exploration runtime/assets and no QA/fault hooks, each artifact check exits 0, packaged server reports the complete named suite, and client reports all exploration navigation cases with no failures. Repeat the packaged-server command for the other five listed suites; restart uses `--exploration-fault payment-pending` and each remaining Task 4 fault value, not an unverified one-process substitute.

- [ ] Run the full eight new suites (`ExplorationDiscovery`, `ExplorationRecords`, `ExplorationSupplies`, `ExplorationGathering`, `ExplorationRestart`, `ExplorationLifecycle`, `ExplorationCourier`, `ExplorationMenus`), plus packaged coverage, and targeted existing regressions already named above. Count unique checks from reports, never estimate a total. Use actual QA timer acceleration via server tick sprint only; preserve 6,000 simulation ticks. Stop on any failure and fix with a reproducing test.
- [ ] Run a normal packaged client without QA fixtures in an isolated instance to check native hut placement, worker assignment, discovery/gathering controls and ordinary tabs. Use manual interaction only for visual/input behavior that automated tests cannot establish. Save/close only the disposable instance. Document copied-world source and migration results without changing the source backup.
- [ ] One independent final review against the feature-start snapshot, approved spec and this plan, covering tracked and initially untracked files. Implement important fixes inline with RED/GREEN regression evidence. No per-task reviewer agents.
- [ ] Hash the installed founders.1 JAR again and compare with the baseline. Document tested behavior, exact result locations, limitations (one colony-wide trip, owner-online time, discovery-only fruit, no efficiency bonuses), and the development JAR location. Report remaining blockers honestly. Do not install, publish or push.
- [ ] Scoped completion commit: `test: verify packaged exploration hut and document results`.

## Plan self-review and handoff

Coverage: native identity/migration and discovery -> Task 1; balance/payment/record bounds -> Task 2; travel/timing/repeat -> Task 3; recovery/couriers -> Task 4; player controls -> Task 5; packaged/copy-world/review/install isolation -> Task 6. All five review risks have explicit tests. Shared interfaces use SurveyResource.Tool, ResourceLocation, UUID and the existing native item handlers; simulated rewards never enter SurveyCargo.

Execution method is preserved from this thread: implement inline, then one independent final review. Await review of this plan before implementation. Initial balance values approved in the spec are defaults for this milestone, not a new upgrade-system commitment.
