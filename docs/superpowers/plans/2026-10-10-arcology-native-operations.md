# Arcology native construction and forestry implementation plan

> Execute inline with superpowers:executing-plans. Approved continuation of the multi-building outpost design, stages 3 and 4. No new founding UI, automatic Mayor layout or transport policy is included.

**Goal:** prove actual Arcology builder completion and local two-forester production using native MineColonies AI, with retained runnable acceptance evidence.
**Architecture:** extend the isolated foundation GameTest harness. Production MineColonies remains responsible for construction, jobs, inventories and tree work. Test setup prepares bounded supported ground, registers level-zero huts, provides finite real supplies in local inventories and enables native work orders. Never call construction completion or set target levels to manufacture success.
**Runtime:** existing pinned Java 21 / NeoForge / MineColonies / Structurize; installed external Arcology pack.
**Spec:** ../specs/2026-10-10-multibuilding-arcology-outposts.md.

## Task 1: Native builder acceptance

- [x] Add Operations to tools/Test-OutpostFoundation.ps1 and FoundationGameTests.java, with exact required case IDs: supported_layout, native_builder_completion, worker_access, two_foresters_harvest_replant, local_supply_boundary.
- [x] Add failing ArcologyOperationsTests.java with clear unimplemented assertion; run Operations and record RED.
- [x] Implement QA-only ArcologyOperationsFixture.java: isolated satellite claim 512 blocks from home; real Arcology builder, two foresters and housing. Use measured level 1-5 envelopes; separated ground-level placements and explicit walkways, not guessed stacked connectors. Prepare support before orders; do not modify source assets.
- [x] Request native construction through IBuilding.requestUpgrade(owner,builderAnchor). Bootstrap the level-zero builder through native self-construction if supported. Supply native requested component-bearing stacks locally, record quantities and keep supply generation labeled as fixture-only. Native AI alone changes blueprint blocks and building levels after setup.
- [x] Monitor level/work-order/block evidence with bounded timeout and status logs; native completion must cover builder, both foresters and housing. Failed native requirements remain FAIL/BLOCKED, never PASS.

## Task 2: Access and native production

- [x] After construction, require real native navigation from each workplace to its local home and forestry access point, no fixture teleports after start. Record maximum distance from outpost and identity throughout.
- [x] Assign two native foresters, distinct bounded work areas outside all module envelopes; introduce actual trees, finite local food/tools/saplings. Check each worker gets logs, damages tools and replants its harvested tree. Protect a control tree outside work areas.
- [x] Confirm worker IDs/colony unchanged and no trip toward home for normal supplies. Report exactly which local supply behaviors were observed; preloaded inventory alone is not proof of autonomous restocking.
- [x] If native integration fails, diagnose against pinned source/runtime, fix fixture defects only when justified, and preserve real limitations with evidence. Do not replace native AI with a custom test harvester or relax successful completion criteria.

## Task 3: Review and handoff

- [x] Execute Operations with exact manifest and zero process errors; persist result and fixture diagnostics in docs/outposts/evidence. Document any unmet acceptance separately.
- [x] Run clean build, existing Records/NativeRestart where affected, and normal resource audit; verify QA isolation and no bundled Arcology assets.
- [x] Request one fresh-context final review, resolve concrete findings, update README and results with implemented versus unimplemented boundaries. Commit only task-owned changes; retain previous research edits.

Commands from repository root (JAVA_HOME=Java21; existing local Gradle cache):

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite Operations -TimeoutSeconds 900
    ./gradlew.bat build --no-daemon --console=plain
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite Records
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite NativeRestart
    ./gradlew.bat runGameTestServer --no-daemon --console=plain

Ruling: the user explicitly approved proceeding with construction/access then forestry under the already approved design. Implement this verification milestone now without another design approval loop. Full multilevel automatic packing, Mayor placement and local courier isolation remain later tasks.
