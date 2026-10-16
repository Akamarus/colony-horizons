# Free Outpost Founders Implementation Plan

## Execution record — 2026-10-15

Implementation performed inline as requested, with an independent final review. Tasks 1–6 are implemented and verified; installed build unchanged. The original authoring checklist below is retained as the approved plan. The authoritative executed test list, actual run IDs, artifact hash and limitations are in `docs/outposts/FREE-FOUNDERS-RESULTS.md`.

- [x] Versioned intent, native identity receipts and legacy record compatibility.
- [x] Explicit role consumers and eight-building free-founder layout.
- [x] One-time grant, capacity reservations and eight separate-process recovery boundaries.
- [x] Protected arrival, cancellation, local care, construction and legacy restart/upgrade regression tests.
- [x] Four-settler menus, protocol tests, seven packaged navigation cases and 28 rendered states.
- [x] Final packaged grant/flow/endurance; fresh copied-colony construction and reload; unchanged installed mod hashes.
- [x] Independent review: three Important findings reproduced, corrected and retested.
- [x] Durable local checkpoint and results documentation.

Execution adjustments: reused the existing protected native arrival adapter; some planned named assertions were consolidated into shared scenarios. Three distinct harvest/replant operations were tested rather than regrowth of the same tree three times. Production delivery is proven by the packaged endurance world, separately from copied-colony construction/reload. Ambiguous unreceipted native creation remains recovery-required and may leave a survivor at Home instead of quarantining unrelated immigration. Per-task commits were replaced by a baseline-relative recoverable checkpoint because much earlier development was already untracked; no unrelated changes were staged, merged or published.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every new eligible outpost four free native settlers without taking workers from Home or duplicating citizens after interrupted saves.

**Architecture:** Version founding intent independently from the actual native roster. Centralize role resolution, then add a durable one-time grant service and connect it to existing protected arrival, construction, and local logistics. Preserve legacy approved identities and fingerprints.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.250, MineColonies 1.1.1403, Structurize 1.0.832, BlockUI 1.0.199; existing GameTest and isolated packaged-game runners.

**Spec:** `docs/superpowers/specs/2026-10-15-free-outpost-founders-design.md` (approved).

## Global Constraints

- Four free native adults once per new outpost: Mayor, builder, courier, forester.
- Eight founding buildings: builder, warehouse, courier, forester, four homes; builder prerequisite first, warehouse second, courier third.
- Respect research/server population ceilings; completed housing is not a prerequisite for these founders.
- Player supplies materials, food and tools. Paid recruitment and free death replacements are outside this slice.
- Preserve legacy four- and five-person outposts, native menus/jobs, warehouse transport, and local courier boundaries.
- Never write to the installed playtest instance or its worlds. Use isolated copies; do not publish or install artifacts.
- Use the existing `.setup/safe-supply-routes` worktree. Its unrelated modified and untracked files are valuable: never blanket stage, reset, clean, or revert them. Snapshot touched pre-existing changes before execution; stage only attributable task changes. Commit as Akamarus.
- Each acceptance run must have fresh reports with all expected case names present, all cases passing, and process exit 0. Gradle `test` reporting NO-SOURCE is not acceptance evidence.

## Review Focus

- Native numeric citizen IDs are reused: an old receipt must never authorize a different UUID (Tasks 1, 3).
- Immigration between approval and creation can exhaust capacity: wait without creating a partial new cohort (Task 3).
- Cancelled site renamed or shifted by one block must not award a second overlapping-site grant (Task 3).
- Old four-person layout and new four-person layout have different roles: distinguish by persisted policy, never count (Tasks 1, 2).
- Malformed or stale menu payloads must not replace roster identities or consume an entitlement (Task 5).

## File and interface map

All Java paths below are relative to `src/main/java/dev/colonyhorizons/`; QA paths are `qa/outpost/` under that root. Introduce only these focused production units:

- `remote/FoundingRoster.java`: immutable policy/role/identity types and legacy-aware resolution.
- `remote/FoundingGrantJournal.java`: bounded persisted reservation, consumed-site record, slot intents and receipts.
- `remote/FoundingGrantService.java`: capacity, native registration, reconciliation and cancellation coordination.
- `remote/NativeFounderFactory.java`: pinned native creation/arrival adapter; no inventory grants or job assignment.

Extend `RemoteModels`, `RemoteDevelopmentData` and their existing codecs rather than introducing another world save root. Existing `FoundingTravel` remains responsible for protected arrival and return. Test classes and runner registrations are added with the task that uses them.

Pinned runtime inspection confirms `CitizenManager.createAndRegisterCivilianData()`, `spawnOrCreateCivilian(T, Level, List<BlockPos>, boolean)`, and `maxCitizensFromResearch()`, plus native citizen NBT serialization and UUID access. No general citizen-data persistent-marker accessor was found. This plan therefore chooses the spec's conservative fallback: an unreceipted creation intent after restart is recovery-required, not an automatic retry. Do not add an entity-only marker and claim it survives native-record creation crashes.

## Commands and evidence conventions

Run from the worktree in PowerShell. Set these once per shell:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='./.setup/gradle-arcology-recovery'
$founderPython='<user-home>/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
```

Build command: `./gradlew.bat classes --no-daemon --console=plain`.

New suite names below are planned additions, not commands already available today. Register each in `qa/outpost/FoundationGameTests.java`, `tools/Test-OutpostFoundation.ps1` (ValidateSet, exact expected cases, restart phases), and the existing reporting pattern. Missing/empty reports must fail. Use `SupplyTests.check(...)` inside named cases; fixture helper names in assertions below describe helpers added in the owning test class.

### Task 1: Version intent and persist explicit rosters

**Files:** create `remote/FoundingRoster.java`, `qa/outpost/FreeFounderRecordTests.java`; modify `remote/RemoteModels.java`, `remote/RemoteDevelopmentData.java`, `remote/RemoteSitePlanner.java`, and suite registration/runner above.

**Interfaces:** `FoundingRoster.Policy { LEGACY_SELECTED, FREE_FOUR_V1 }`; `Role { MAYOR, BUILDER, COURIER, FORESTER }`; `Member(Role role, int citizenId, UUID entityId)`; `List<Member> resolve(RemoteDevelopmentData data, UUID outpost)`; `Optional<Member> member(RemoteDevelopmentData data, UUID outpost, Role role)`; `List<Integer> citizenIds(RemoteDevelopmentData data, UUID outpost)`. Legacy can have two FORESTER entries; new mode has exactly one of each role.

- [ ] Write `FreeFounderRecords` cases `legacy_four_roundtrip`, `legacy_five_roundtrip`, `free_intent_without_ids`, `free_roster_roundtrip`, `duplicate_identity_rejected`, `unknown_policy_rejected`, `reused_id_wrong_uuid`. Assert legacy NBT retains phase, cargo and approved fingerprint; new intent has zero native IDs but four role slots; `check(freeRoster.size()==4)` and `check(freeRoster.stream().map(Member::entityId).distinct().count()==4)`.
- [ ] Run `powershell.exe -NoProfile -File tools/Test-OutpostFoundation.ps1 -Suite FreeFounderRecords -TimeoutSeconds 900`; establish failure on missing new policy/codec behavior, not a broken runner.
- [ ] Add a policy component to `SiteRequest` with a legacy-compatible old-signature constructor. Only FREE_FOUR_V1 accepts empty IDs and mayor ID 0; LEGACY_SELECTED keeps existing validation. Version the fingerprint input only for new mode. Persist roster separately; require full unique roles/IDs/UUIDs before operational authority. Legacy resolution reproduces historical ordering without rewriting approved intent.
- [ ] Run the build command, then `FreeFounderRecords` and `RemoteRecords` through the same runner; require all expected cases PASS.
- [ ] Commit only this task's attributable code/tests and runner registration (`feat: version outpost founding rosters`).

### Task 2: Centralize all role consumers and add the eight-building layout

**Files:** modify `remote/ArcologySettlementLayout.java`, `RemoteSitePlanner.java`, `FoundingConstructionSupplies.java`, `FoundingReservation.java`, `FoundingSupplies.java`, `FoundingTravel.java`, `RemoteFoundingService.java`, `RemoteConstruction.java`, `RemoteCare.java`, `RemoteWorkerLocality.java`, `RemoteDevelopmentData.java`; `mayor/MayorService.java`, `MayorLogistics.java`, `MayorProduction.java`, `MayorHousing.java`, `MayorOperatingSupplies.java`, `MayorSupplyController.java`; create `qa/outpost/FreeFounderLayoutTests.java`. Include other production consumers identified by the required search, without unrelated refactoring.

**Interfaces:** consumes Task 1 resolver; produces `boolean FoundingRoster.warehouseFirst(RemoteModels.Development development)` and the existing planner's FREE_FOUR_V1 branch. Resolve citizens through the roster; keep old constructor callers compiling.

- [ ] Write `FreeFounderLayout` cases `eight_huts`, `four_homes_one_forester`, `builder_warehouse_courier_order`, `legacy_four_no_courier`, `legacy_five_two_foresters`, `roster_order_independent`, `incomplete_roster_no_authority`. Assert `check(plans.size()==8)`, four home plans, exactly one of each workplace, and courier membership unchanged after persisted roster order is shuffled.
- [ ] Run `FreeFounderLayout` using the Task 1 command pattern; confirm failure against current five-person layout/count-derived courier behavior.
- [ ] Search production with `rg -n 'citizenIds\(|mayorId\(|approvedParty\(' src/main/java/dev/colonyhorizons -g '*.java' -g '!**/qa/**'`. Replace role/membership assumptions with the resolver. Leave intentional legacy codec accesses documented. Recalculate new layout supply bill and upgrade volumes from its actual eight blueprints; do not alter old layout hashes or put the removed forester slot into automatic expansion authority.
- [ ] Run build and suites `FreeFounderLayout`, `WarehouseSites`, `RemoteLocality`, `MayorRole`, `MayorReservations`, `FoundingFlow`; require PASS. Record the consumer search audit with any intentionally remaining legacy accesses.
- [ ] Commit attributable changes (`refactor: resolve outpost jobs through explicit rosters`).

### Task 3: Grant, capacity, and durable one-time creation

**Files:** create `remote/FoundingGrantJournal.java`, `FoundingGrantService.java`, `NativeFounderFactory.java`, `qa/outpost/FreeFounderGrantTests.java`, `FreeFounderRestartTests.java`; modify `RemoteDevelopmentData.java`, `NativeFoundingCheckpoint.java`, `FoundingReservation.java`, `remote/RemoteRuntime.java`, suite runner/registration.

**Interfaces:** `FoundingGrantService.review(ServerLevel level, UUID outpost): StepResult`; `reserve(ServerPlayer actor, UUID outpost, long revision): StepResult`; `advance(ServerLevel level, UUID outpost): StepResult`; `cancel(ServerPlayer actor, UUID outpost, long revision): StepResult`. `NativeFounderFactory.register(IColony colony): ICitizenData`; `arrive(ServerLevel level, IColony colony, FoundingRoster.Member member, BlockPos target): boolean`. Journal states RESERVED, CREATING, REGISTERED, ARRIVED, RECOVERY_REQUIRED; per-slot states INTENT and RECEIPTED, with native ID/UUID only on a receipt. Consumed-site tombstones persist independently of cancelled development visibility.

- [ ] Write `FreeFounderGrant` cases `no_idle_home_workers`, `preview_no_creation`, `four_native_adults`, `home_identities_untouched`, `research_cap`, `server_cap`, `housing_not_required`, `concurrent_immigration`, `two_pending_reservations`, `second_distinct_site`, `overlap_cancel_rename`, `death_no_replacement`, `reused_native_id`. Pin `check(afterCount-beforeCount==4)`, original job/home/inventory snapshots equal, reservation accounting includes uncreated reserved slots but never double-counts receipted citizens.
- [ ] Run `FreeFounderGrant`; confirm intended failures before implementation.
- [ ] Implement server-thread-only reservation/registration. Recheck ownership, mode, phase, funds escrow, claims, safe positions, full remaining cohort capacity and other reservations before first creation. Persist consumed site plus first slot intent before native registration; after each registration record its ID/UUID and checkpoint native/addon data. Guard unarrived recorded founders from native Home respawn/hiring, using the existing travel/reservation guard boundary. Inspect pinned initialization/event behavior to match native registration without double-emitting arrival events. Do not set global population limits or use public spawn-with-null-data to create a second citizen.
- [ ] Add separate-process `FreeFounderRestart` fault cases for intent persisted before registration, native save before receipt, receipt before native save, every receipted slot, completed registration before arrival, and partial arrival. Use a test-only fault injection seam, excluded from production artifacts. Assert `check(actualCount<=baselineCount+4)`; identity-proven receipts reconcile exactly, unmatched/ambiguous outcomes persist RECOVERY_REQUIRED with no retry registration. Retain consumed tombstones even on failure. Arrival of known native records must not create replacements for missing records.
- [ ] Run build, `FreeFounderGrant`, and `FreeFounderRestart`. Extend restart runner to enumerate every fault case in independent worlds/processes; require seed and reload evidence for each, not an in-memory codec test.
- [ ] Commit attributable changes (`feat: grant four native outpost settlers once`).

### Task 4: Protected arrival, cancellation, and autonomous work

**Files:** modify `remote/FoundingTravel.java`, `RemoteFoundingService.java`, `FoundingSupplies.java`, `FoundingReservation.java`, `FoundingConstructionSupplies.java`, and `compat/mixin/FoundingTravelGuardMixin.java` if needed for the registered-but-unarrived native guard; create `qa/outpost/FreeFounderFlowTests.java`. Reuse existing Mayor systems through Task 2 interfaces.

**Interfaces:** add `FoundingTravel.beginGranted(ServerLevel level, UUID outpost): StepResult`; the existing legacy `begin` contract remains unchanged. Consumes grant receipts and uses native IDs/UUIDs already created; targets must be distinct, dry, supported and authorized. Use the existing Home safe-return selection for cancellation, not a fabricated former Home bed.

- [ ] Write `FreeFounderFlow` cases `unsafe_arrival_waits`, `unloaded_arrival_waits`, `missing_supplies_waits`, `owner_offline_waits`, `four_distinct_arrivals`, `full_starter_inventory_waits`, `cancel_before_grant`, `cancel_after_each_arrival`, `native_courier_local`, `eight_buildings_complete`, `four_housed`, `logs_return_home`. Assert zero new citizens before all founding prerequisites; each arrived entity UUID equals its receipt; survivors retained on cancel; escrow conservation; native JobDeliveryman and JobLumberjack on their approved huts; eight completed native buildings and all four citizens housed.
- [ ] Run `FreeFounderFlow`; confirm missing integration fails.
- [ ] Branch coordinator by explicit policy. Free mode advances the grant and protected arrival; legacy uses original transfer flow. Apply bootstrap kit only once after validated arrival, appoint the roster Mayor, and let the existing builder/warehouse/courier order proceed. Cancellation releases unused reservations, retains consumed receipts, and queues surviving citizens for protected safe return; unavailable return space pauses without deleting anyone or resetting entitlement.
- [ ] Run build and suites `FreeFounderFlow`, `FoundingTravelRestart`, `FoundingCancelRestart`, `WarehouseRestart`, `MayorUpgradeInFlight`. Require both legacy behavior and new eight-building completion to pass.
- [ ] Commit attributable changes (`feat: connect free founders to outpost construction`).

### Task 5: Simple founding menus

**Files:** modify `menu/RemoteMenuInput.java`, `RemoteMenuService.java`, `client/supply/WindowRemoteFounding.java`, `WindowMayor.java`, `src/main/resources/assets/colonyhorizons/gui/supply/founding.xml`, `mayor.xml`; create `qa/outpost/FreeFounderMenuTests.java`; extend `tools/Test-SupplyMenuAssets.py` only for new visible requirements.

**Interfaces:** menu review derives FREE_FOUR_V1 on the server for new proposals. Add summary fields `foundingPolicy`, `plannedPeople`, `arrivedPeople`, `populationBlocker`; retain legacy response fields while old saved proposals remain usable. New clients send no chosen citizen IDs for free mode; reject nonempty IDs or a supplied Mayor identity in that mode.

- [ ] Write `FreeFounderMenu` cases `free_four_copy`, `no_picker_required`, `planned_vs_arrived`, `cap_blocker_readable`, `stale_approval_inert`, `foreign_owner_rejected`, `forged_roster_rejected`, `legacy_proposal_still_readable`. Assert exact copy “4 free settlers: Mayor, Builder, Courier, Forester.”; no creation during review; stale payload leaves revision, roster and grant unchanged.
- [ ] Run `FreeFounderMenu`; confirm expected failures.
- [ ] Update existing pages without adding a separate recruitment screen. Show approved materials and capacity blockers, distinguish planned settlers from arrived settlers, and suppress old “original Mayor” wording for free mode. Keep legacy selected-party review functional. Do not add a paid-recruitment button.
- [ ] Run build, `FreeFounderMenu`, `RemoteMenu`, `MenuProtocol`, and `& $founderPython tools/Test-SupplyMenuAssets.py`; require PASS. Inspect disposable client at GUI scales 2 and 3 for readable review/status and real button behavior; use manual input only when the user is not interacting with the test window.
- [ ] Commit attributable changes (`feat: show free settlers in outpost founding menus`).

### Task 6: Packaged colony and endurance verification

**Files:** extend `tools/Test-SupplyPackagedServer.py` with `FreeFounderFlow`, `FreeFounderGrant`, `FreeFounderEndurance`; modify `tools/Test-CopiedColonyFounding.py` to accept `--source-world` (required existing snapshot) and `--founders free-four|legacy` (default legacy); modify `src/supplyQa/java/dev/colonyhorizons/qa/CopyFoundingProbe.java`; create `qa/outpost/FreeFounderEnduranceTests.java` and `docs/outposts/FREE-FOUNDERS-RESULTS.md`. Add runner registration for the endurance suite.

**Interfaces:** copied-world runner never opens or mutates source; reject a destination inside installed/source trees. Derive snapshot source from verified backup, not the runner's current hardcoded temporary folder. Evidence JSON includes artifact hash, native before/after UUID/job/home maps, eight building levels, route item counts, elapsed ticks, and phase results.

- [ ] Add `FreeFounderEndurance` assertions for 240000 simulated ticks: all four founders alive/fed/housed, illness/recovery and depleted-tool resupply succeed, forester replants across at least three harvest cycles, at least eight produced logs delivered Home, courier never traverses the wilderness gap, and grant remains consumed after reload. Disable fixture immigration only when necessary to make population assertions deterministic and disclose it.
- [ ] Run the new endurance suite and record any failure before fixes; fix only discovered defects in the owning production unit and rerun affected suites.
- [ ] Build a distinct artifact without editing installed version/config: `./gradlew.bat supplyDevJar supplyQaJar -Pmod_version=0.1.0-dev.founders.1 --no-daemon --console=plain`. Verify with `& $founderPython tools/Test-SupplyDevPackage.py build/supply-dev-libs/colony-horizons-0.1.0-dev.founders.1.jar --version 0.1.0-dev.founders.1`. Require production package checks PASS and no QA/fault hooks bundled.
- [ ] Run `& $founderPython tools/Test-SupplyPackagedServer.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.founders.1.jar --version 0.1.0-dev.founders.1 --suite FreeFounderEndurance` and the same command for `FreeFounderGrant` and `FreeFounderFlow`; require complete reports and exit 0 on the exact packaged hash.
- [ ] Run `& $founderPython tools/Test-CopiedColonyFounding.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.founders.1.jar --source-world '<user-home>/Documents/ChatGPT/Minecraft Colony Mod/.setup/playtest-backups/20261015-041237-before-remote-outposts/instance/saves/New World' --founders free-four`. Verify snapshot exists before use; if unavailable, stop this test and request a stable snapshot rather than reading a live world inconsistently. In the copy assign all original adults to legitimate Home work before founding; baseline them after that fixture setup. Assert all original identities/jobs/homes retained, exactly four grants, eight completed outpost huts, production delivery, then save/reload and verify again.
- [ ] Record installed addon SHA256 before/after as read-only proof of no replacement. If user play changes live world files, do not misreport that as our write or try to restore them. Record test-only world preparation, creative supplies, difficulty and accelerated time honestly; no claim of untouched-world survival balance.
- [ ] Write results with actual run IDs, counts, artifact hash, limitations, and remaining paid-recruitment work. Run `git diff --check`, commit attributable test/docs/fixes, and present the verified artifact for later installation without copying it into the user's pack.

## Self-review and handoff

Coverage: schema/legacy compatibility (1–2); population, one-time entitlement and crash recovery (3); arrival, cancellation, care, warehouse-first construction (4); native-style UI/security (5); copied-colony production and survival endurance (6). All five Review Focus conditions have named tests. No new feature code, test harness, dependency installation, game launch, or playtest-pack write is authorized by this planning document alone.

Recommended execution: native execution in this session, with one independent final review after verification. The roster, journal, and coordinator changes share interfaces closely; keeping implementation in one context avoids repeated handoffs. Await the user's plan review and execution choice before implementation.
