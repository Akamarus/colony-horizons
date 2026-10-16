# Arcology Multi-Building Outpost Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Establish verified Arcology building metadata and persistent multi-building outpost membership, demonstrated against real native buildings and citizens across a server restart.

**Architecture:** MineColonies owns buildings, citizens and jobs. A dimension-local addon SavedData index groups their stable native references into outposts; a separate read-only pack inspector supplies blueprint evidence. Native claims and assignments are exercised by QA fixtures before player-facing founding is exposed.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.250, MineColonies 1.1.1403-1.21.1, Structurize 1.0.832-1.21.1-snapshot, Gradle wrapper and native GameTests.

**Spec:** [Approved multi-building Arcology design](../specs/2026-10-10-multibuilding-arcology-outposts.md), with the parent resource-outposts design for the longer-term direction.

## Global Constraints

- Each outpost has a stable UUID, home colony ID, dimension, name, approved claim chunks, a three-dimensional development envelope, separate forestry work area, building memberships, resident/worker IDs, terminal position and lifecycle state.
- MineColonies remains authoritative for actual buildings, jobs, citizens, inventories, levels and construction orders.
- Workers can belong to only one local work district at a time.
- Arcology is the preferred style for this milestone.
- Keep the pack installed separately; no asset redistribution is part of this change.
- Do not forcibly change chunk ownership or silently enlarge the global colony radius.
- No Ars integration, separate colony creation, unrelated prototype changes or player-save edits.
- Use the existing Colony Horizons checkout. Preserve current uncommitted findings; commit only the task's own files.
- Execution method: implement directly in this session, preserving the user's earlier request to continue development ourselves.

## Phase boundary

This plan implements design delivery stage 1 and the native registration/persistence part of stage 2. It produces a working internal settlement registry and reproducible integration evidence, not a playable autonomous outpost.

Actual builder construction/upgrade, terrain/access validation, harvesting, courier isolation, protected shipments and the management screen remain explicitly queued stages 3-6. Their implementation plans follow the evidence from this phase; do not invent an untested courier API or Arcology stacking scheme now.

Missing Gatehouse assets block route readiness, not inspection, membership records or a QA settlement fixture. No production founding command/screen is exposed by this phase.

## Review Focus

- Legacy blueprint content that silently converts missing blocks to air: audit original palette names as well as decoded output (Task 1).
- Repeated hut types and stacked modules: membership is keyed by anchor, with three-dimensional envelopes; two foresters are distinct (Task 2).
- Identical citizen numbers in different colonies/dimensions: uniqueness uses the full native identity (Task 2).
- Removed native huts or missing citizens after reload: retain references and report unresolved membership, never recreate citizens (Task 3).
- A restart apparently passing through a backup restore or fixture reseed: require clean primary load and unchanged native identity (Task 4).

## Commands and environment

Working directory: .

Set in PowerShell:

    $env:JAVA_HOME='C:/Program Files/Java/jdk-21'
    $env:GRADLE_USER_HOME='<user-home>/Documents/ChatGPT/Minecraft Colony Mod/.gradle-local'

Existing baseline:

    ./gradlew.bat clean build --no-daemon --console=plain

The foundation runner created in Task 1:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite Arcology
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite Records
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite NativeRestart

Every suite requires exit 0, nonempty expected-case reports, no missing/duplicate/unexpected case IDs and all cases PASS. Report missing optional Gatehouse as catalog data, not as a claimed successful route. Tests must not treat BLOCKED as PASS.

## Task 1: Inspect real Arcology blueprints through the pinned runtime

**Files**
- Create: tools/Test-OutpostFoundation.ps1
- Create: src/main/java/dev/colonyhorizons/qa/outpost/FoundationGameTests.java
- Create: src/main/java/dev/colonyhorizons/qa/outpost/ArcologyPackTests.java
- Create: src/main/java/dev/colonyhorizons/integration/ArcologyCatalog.java
- Modify: build.gradle (add isolated outpostFoundation run), ColonyHorizons.java (guarded QA registration)
- Evidence: docs/outposts/ARCOLOGY-COMPATIBILITY.md

**Interfaces**
- ArcologyCatalog.inspect(Path packRoot, HolderLookup.Provider registries): Inspection.
- Nested immutable records: Entry(String relativePath, String sha256, String hutId, int level, BlockPos size, BlockPos anchor, List<String> unresolvedBlocks); Inspection(String packName, List<Entry> entries, List<String> diagnostics).
- A missing or malformed required blueprint yields diagnostics and fails the corresponding compatibility case. No silent fallback pack.
- Runner parameters: Suite in Arcology/Records/NativeRestart; ArcologyPath defaults to the discovered Dimensional Adventure copy; TimeoutSeconds defaults 300, range 30-900.

- [x] Add a minimal isolated run/runner and failing tests: required_files_present, native_blueprint_decode, required_hut_anchors, missing_pack_reported, invalid_blueprint_reported, missing_gatehouse_reported. Require builder, lumberjack, citizen, warehouse and deliveryman levels 1-5 (25 files). Missing Gatehouse must be explicit in inspection diagnostics.
- [x] Run Arcology suite; capture expected absent-inspector failure separately from launcher failures.
- [x] Implement inspection using Structurize's actual runtime blueprint loader after inspecting its signatures. Resolve files relative to the supplied root; reject traversal. Read original compressed NBT palette names to detect registry misses that a decoder might hide. No custom blueprint conversion or source-pack rewriting.
- [x] Run Arcology suite. Record exact pack hashes, dimensions, anchors, native hut IDs, and unresolved blocks. If decoder compatibility fails, preserve the diagnostics and stop layout-dependent work; record tasks can proceed independently.
- [x] Document that successful decode is not preview, construction, navigation or upgrade proof. Original files remain unchanged. Commit only these files and reports.

Runner implementation requirements: fresh report UUID and disposable world per normal suite; stable world across restart phases; bounded process timeout; hidden helper processes; exact phase manifests; use the existing Assert-OutpostReport.ps1 through a child PowerShell process so its exit cannot terminate the enclosing runner. Do not change existing Native/Transfers/Restart expectations to manufacture a pass.

## Task 2: Persist multiple building and citizen memberships

**Files**
- Create: src/main/java/dev/colonyhorizons/outpost/OutpostRecord.java
- Create: src/main/java/dev/colonyhorizons/outpost/OutpostSavedData.java
- Create: src/main/java/dev/colonyhorizons/qa/outpost/OutpostRecordTests.java

**Interfaces**
- OutpostRecord: UUID id; int colonyId; ResourceKey<Level> dimension; String name; Set<ChunkPos> approvedChunks; BoundingBox development; BoundingBox forestry; Map<BlockPos, BuildingRef> buildings; Set<Integer> residentIds; Set<Integer> workerIds; Optional<BlockPos> terminal; State state; List<String> blockers.
- BuildingRef(BlockPos anchor, ResourceLocation type, String packName, String blueprintPath, BoundingBox reservedVolume).
- State: PLANNED, PAUSED, RECOVERY_NEEDED. Do not expose an OPERATIONAL state before operation is implemented.
- OutpostSavedData.get(ServerLevel): OutpostSavedData; register(OutpostRecord): void; find(UUID): Optional<OutpostRecord>; all(): List<OutpostRecord>.
- register validates the entire proposed record before atomically replacing/inserting it; throws IllegalArgumentException without mutation for malformed inputs or conflicts. No mutable collection escapes.
- SavedData key colonyhorizons_outposts, schema 1. Dimension-local storage; persist and verify dimension identity. Unknown schema fails closed without rewriting the saved file.

- [x] Add failing Records cases: multiple_huts_roundtrip, repeated_roles_distinct, worker_membership_exclusive, different_colonies_same_citizen_id, dimension_mismatch_rejected, stacked_nonoverlap, conflicting_volume_rejected, invalid_record_atomic, unknown_schema_rejected.
- [x] Run Records suite and capture failures.
- [x] Implement immutable/copy-safe records and schema-1 NBT persistence. Full boxes use integer inclusive coordinates; reject inverted/empty invalid domains. Repeated worker/resident membership inside one outpost is valid; membership across two outposts of one colony is not. Worker IDs must be positive and workerIds a subset of residentIds. Overlapping approved chunks between outposts are rejected. Distinct vertically stacked building boxes within one outpost may be allowed only when nonintersecting; connectors are not guessed.
- [x] Run Records suite, including two forester anchors and at least four citizens. Confirm rejecting a record does not dirty or partially alter the existing index.
- [x] Commit record implementation and tests.

## Task 3: Reconcile records against native colony state without side effects

**Files**
- Create: src/main/java/dev/colonyhorizons/integration/NativeOutpostMembership.java
- Create: src/main/java/dev/colonyhorizons/qa/outpost/NativeMembershipTests.java
- Modify: FoundationGameTests.java (Records-suite test registration)

**Interfaces**
- NativeOutpostMembership.inspect(ServerLevel level, OutpostRecord record): MembershipReport.
- MembershipReport(List<BlockPos> missingBuildings, List<Integer> missingCitizens, List<String> blockers); ready(): boolean.
- inspect never creates claims, buildings, jobs or citizens and never loads arbitrary chunks. Return a distinct unloaded/unresolved blocker rather than treating unloaded state as confirmed deletion.

- [x] Add failures: native_identity_matches, foreign_colony_rejected, removed_hut_reported, missing_citizen_reported, unloaded_reference_deferred. Assert native citizen counts and inventories unchanged before/after every inspection.
- [x] Run Records suite to confirm failures.
- [x] Resolve the home colony through IColonyManager, then native building/citizen managers; validate work-building association and approved district membership. Report missing work assignments; do not auto-reassign.
- [x] Run Records suite and commit.

## Task 4: Prove a multi-building satellite survives real restarts

**Files**
- Create: src/main/java/dev/colonyhorizons/qa/outpost/OutpostFoundationRestartTests.java
- Create: src/main/java/dev/colonyhorizons/qa/outpost/NativeProfileFixture.java
- Modify: tools/Test-OutpostFoundation.ps1, FoundationGameTests.java
- Evidence: docs/outposts/MULTIBUILDING-FOUNDATION-RESULTS.md

**Interfaces**
- NativeRestart phases: seed, verify, verify_paused; all use one new world ID. Each process writes exactly one required case: foundation_seed, foundation_verify, foundation_verify_paused.
- Test-only profile fixture runs before level loading when and only when the foundation QA property is enabled; adapt the proven ServerAboutToStartEvent fixture from .setup/travel-spike. No reflection/profile injection in normal gameplay.
- Durable fixture identity stores only expected IDs/positions/hashes, not replacement native inventories or colonies.

- [x] Write failing restart tests. Seed a real colony, claim a bounded satellite area with forceOwnerChange=false, register two native forester huts, a builder hut and enough native housing. Determine housing capacity through native modules; do not assume one house holds everyone. Assign existing citizens using native modules and record memberships with Task 2.
- [x] Run NativeRestart and establish failure before implementing runner phases/fixture support.
- [x] Add the QA setup and phase runner. Use real inspected Arcology blueprint references where compatible. Fixture setup may place native anchors and set building levels, but must label this as registration evidence, never builder construction. Missing compatible assets must fail clearly; no placeholder Medieval Oak path.
- [x] In verify, reload native primary data and the addon record without reseeding: assert identical colony/citizen IDs, two distinct forester jobs/anchors, housing assignments, original owner, unclaimed sampled gap, satellite ownership, and no duplicate live citizens across loaded endpoints. Inspect membership. Set PAUSED and save.
- [x] In verify_paused, confirm preserved PAUSED state, intact native citizens/buildings/inventories and inert repeated inspection. Deliberately missing primary fixture data must fail, not recreate the settlement.
- [x] Run all three real processes. Check logs for primary colony load errors/backup restoration as well as case reports. Preserve a failure if satellite claims disappear; do not silently reapply claims before assertions. Record native recovery limitations separately.
- [x] Commit fixture/tests and truthful results.

## Final verification and handoff

- [x] Run clean build, then Arcology, Records and NativeRestart suites.
- [x] Run existing Transfers and Restart suites using tools/Test-OutpostProbe.ps1 and the resource audit using ./gradlew.bat runGameTestServer --no-daemon --console=plain.
- [x] Check normal startup does not register foundation QA or inject a profile cache. Confirm no Arcology assets were bundled into the jar.
- [x] Review the complete diff against the approved spec; inspect data loss, implicit ownership mutation, native assignment changes and misleading success claims.
- [x] Update README with completed phase, exact passing evidence and explicit remaining construction/production/logistics/UI work.
- [x] Do not call the outpost playable or self-sustaining. Next plan is Arcology construction/access and native production; use actual measured blueprint data.

## Self-review

All stage-1 requirements and the deliberately limited registration/restart part of stage 2 map to Tasks 1-4. Five review-focus cases are assigned above. All new interfaces are defined here; the Structurize loader is intentionally accessed through the adapter after verifying its pinned signature, not an invented external API. Production claiming, automatic layout, local courier isolation and delivery safety remain explicit future work, not hidden TODOs inside a supposedly complete feature.

Execution complete: see ../../outposts/MULTIBUILDING-FOUNDATION-RESULTS.md. The interdependent QA registry and fixtures were committed together after integrated verification; task-local RED/GREEN evidence is retained in the execution ledger. Records has 15 cases after review added malformed-data coverage.
