# Surveyor development checkpoint â€” 2026-10-15

This is an unfinished development build, not a release or an installed update. The installed four-founder build and live colony were not modified.

## Implemented

- A registered MineColonies Surveyor hut and native worker job, with ordinary worker, inventory, minimum-stock, and building controls.
- Five derived Arcology building levels in the isolated `build/surveyor-pack` output. Geometry and connectors match the existing builder modules; the hut identity and blueprint metadata are changed for the Surveyor.
- A native Surveyor tab and a resource-selection menu. The addon outpost/supply entry moves from the Town Hall to this hut. Server requests require the owner to be at a built Surveyor hut.
- Exact resource targets: eight Overworld wood species and their starter saplings/propagules, sixteen ordinary flower types, wheat seeds from grass/ferns, wild pumpkin and melon slices, vines, kelp, and seagrass. Wild fruit is not silently converted into seeds. Rare/archaeology resources are not advertised.
- A bounded, protected expedition using the original native citizen. Departure consumes eight bread and withdraws an appropriate actual tool. The current search examines up to 32 candidate regions within 4,096 blocks; it returns after obtaining a real sample.
- Temporary asynchronous terrain tickets: at most two inspections, 49 requested chunks per inspection, and one new request per server tick. Returning workers receive entity-ticking home tickets. Tickets are released on completion, offline pause, and unload.
- Actual vanilla block drops, shears requirements, water restoration, finite tool wear and cargo, ownership checks, write-ahead sample receipts, and inventory handoff barriers. Ambiguous interrupted custody is held for recovery rather than replayed.
- Hut recipe, loot table, item/block models, and development packaging checks. QA classes and test structures remain outside the installable jar.

## Verified at this checkpoint

All ten sequential server suites passed, totaling **66 named cases**:

| Suite | Cases | Report run |
|---|---:|---|
| SurveyorHut | 7 | `ac8e4b563e48411480301eecc52de4df` |
| SurveyorRecords | 10 | `345c076dc7c2421390b5a717fc8e65dc` |
| SurveyorHarvest | 6 | `eec76df83a9b4652a559e35074015c6c` |
| SurveyorTickets | 4 | `25df9db48eba4e9db8c7e783d89991cf` |
| SurveyorCargo | 5 | `0eaeea5163534c14b56d9ab1668fbec3` |
| SurveyorSupplies | 5 | `5214f3526a594c38a1633a93b31ae266` |
| SurveyorExpedition | 5 | `6112370edc054449a4ac8054a1e6f93f` |
| SurveyorMenus | 6 | `7603c1add27c442cac7956273d594e1c` |
| MenuProtocol | 10 | `79d527350e384373abcd389849ca6a34` |
| FreeFounderRecords | 8 | `008d71e6d33d41078c864353d0d6be0c` |

Reports are under `build/reports/foundation/<run>/seed/`. The real round-trip fixture uses a native colony, assigned worker, finite hut stock, and a distant actual flower block; the player remains home. The separate ticket fixture generates previously unexplored terrain. These do not yet constitute a test of every catalog item or every natural biome.

The development jar and QA jar built successfully. `Test-SurveyorArtifact.py`, `Test-SupplyDevPackage.py`, and `Test-SupplyMenuAssets.py` passed. Arcology asset comparison passed for all five levels.

The installed jar hash still matches the pre-development value:
`FCD323B1C7E04146BF237684E78D1672B95578D807C95CE1F4550519BCAA3F92`.

## Still required before installation

1. Process-restart, cancellation, full-hut, reassignment, demolition, two-colony, and offline/return integration tests. Current tests cover record serialization and interrupted supply primitives, not every complete restart sequence.
2. Packaged client rendering and live native-tab navigation, plus actual construction and upgrade tests for the five Surveyor blueprints.
3. Durable surveyed-site results suitable for founding review, and the survey-to-founding menu handoff. The existing founding pages still use their prior coordinate-based flow.
4. Integrate terrain preparation into approved founding: safe temporary builder staging, durable coordinator storage, material resupply, water containment/aquatic preservation and complete crash recovery. The bounded estimate, paid block-work transaction and native-builder execution primitives now exist (see below). Existing remote-site validation deliberately still requires clear, supported terrain until that integration is safe.
5. Versioned resource-specific outposts with native forestry/florist/farmer/Plantation workers, research/field limits, starter samples, renewable production, and exact-item warehouse exports. Existing outpost founding remains the prior forestry implementation.
6. Dedicated travel timing, operator recovery UX, complete bounds/schema hardening, and sustained multi-settlement testing.
7. Independent final review, copied-colony migration test, and a separate user-authorized installation.

The development jar is `build/supply-dev-libs/colony-horizons-0.1.0-dev.surveyor.1.jar`. It is a checkpoint artifact; it must not be treated as the completed next alpha.

## Reproduction

With Java 21 and the configured dependency cache:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite SurveyorExpedition -ArcologyPath build/surveyor-pack -TimeoutSeconds 900
./gradlew.bat supplyDevJar supplyQaJar '-Pmod_version=0.1.0-dev.surveyor.1' --no-daemon --console=plain
python tools/Test-SurveyorArtifact.py build/supply-dev-libs/colony-horizons-0.1.0-dev.surveyor.1.jar
python tools/Test-SupplyDevPackage.py build/supply-dev-libs/colony-horizons-0.1.0-dev.surveyor.1.jar --version 0.1.0-dev.surveyor.1
python tools/Test-SupplyMenuAssets.py
```

Run server suites sequentially; do not rebuild their development classpath during a running test.

## Terrain preparation checkpoint â€” October 15

Implemented `SitePreparationPlan`, `SitePreparationJournal`, `SitePreparationWork` and `SitePreparationService` in the isolated checkout. These are not yet invoked by the outpost founding coordinator or menus.

- A read-only estimate records exact original states, clearing operations, bottom-up dirt fill and material quantities. Limits are eight blocks cut/fill per column, 32,768 operations and 262,144 inspected positions. Overlapping footprints are deduplicated; conflicting work is rejected.
- Dry solid support is required within the fill limit. Current checks reject unloaded/claimed land, containers, liquids, constructed block types and excessive work. Natural-looking player-placed blocks and generated structures need additional protection before enabling autonomous site preparation. Water containment is not implemented; wet sites are conservatively rejected.
- Approved estimates and sequential operation receipts survive NBT serialization. A pending operation remains pending after reload; cancellation retains completed work. This is codec testing, not yet a full process-restart workflow.
- Paid work stages actual dirt, tool durability and vanilla drops in the supplied inventory. Missing tools, missing dirt, full output space, changed blocks, occupied fill positions and protection events stop the operation. An intent is written before mutation; native saving precedes a completion receipt. Uncertain operations cannot replay.
- Native-builder execution checks ownership, job, hut, health, travel, existing work orders, proximity and line of sight. It uses native navigation, the builder's real inventory and the verified native colony/world save. The test uses an in-memory journal writer; durable coordinator storage still must be wired in.

Fresh verification:

| Suite | Cases | Report run |
|---|---:|---|
| SurveyorTerrain | 11 | `78406e4657db40a5b5a0d72b5bd87e94` |
| SurveyorPreparationRecords | 6 | `0f03ee970b8141c1ba250d4aa52ba2fa` |
| SurveyorPreparationWork | 9 | `67e6c233c3324d569e7b2e24334d9950` |
| SurveyorBuilderPreparation | 3 | `8d09f8f320484c3e98107d1e7e833610` |
| FreeFounderRecords (regression) | 8 | `5614b46fc5ee4e7cae64849c02a32f96` |

All 29 new cases and eight legacy-record regressions pass. Development and isolated QA jars rebuilt successfully; Surveyor artifact, development-package and menu asset checks pass. Behavioral RED evidence and fixture corrections are recorded in the execution ledger. No player save or installed mod was modified.

Next integration work: persist the approved survey/site/worker relationship; secure a supported temporary builder camp and finite inventory before arrivals; run preparation through the founding coordinator; construct the permanent builder hut, warehouse and courier hut; safely transfer custody and remove the empty temporary camp. Then complete resource-specific producer profiles, menus, end-to-end restart/production tests and the one final independent review.

## Prepared founding integration â€” October 15

New founding reviews now attach a versioned terrain estimate and supported temporary camp. Previously saved drafts retain their original founding behavior.

The coordinator reserves one extra native builder hut and the exact terrain dirt requirement, places the paid temporary hut, brings the four original founders to supported arrival positions, assigns their builder to the camp, and executes the approved terrain journal with that worker's finite inventory. Permanent anchors follow terrain completion. The same builder is reassigned to the permanent builder hut; after it is marked built by MineColonies, camp contents and the paid temporary hut item move into its inventory before camp removal. A full destination preserves the camp and its contents.

Pause/resume retains STAGING until the preparation handoff. Cancelling after paying for the camp but before settlers are created retains site supplies without issuing a duplicate refund. Supply withdrawals for preparation are limited to remaining terrain fills, preserving dirt reserved for permanent anchors.

Completed receipts resume after an actual server process restart. Ambiguous writes (camp placement, terrain mutation, assignment or cleanup intent without a receipt) load into RECOVERY_NEEDED and cannot replay automatically. This is safe persistence, not an automatic repair tool for every interrupted transaction.

The flow tests use an actual native builder to clear terrain, after which the coordinator places all eight permanent anchors. The cleanup fixture explicitly simulates completion of the permanent builder hut through MineColonies' completion callback; it does not prove a complete autonomous settlement build. Large/higher terrain navigation, automatic unloading during long preparation jobs, water containment, resource-specific producer profiles and the complete survey-result-to-founding user journey still need coverage and implementation before release. Natural-looking player construction remains a limitation of the natural-block allowlist and protection-event approach. The full feature's independent final review is still pending.

Fresh integration regression batch: 49 named checks passed across nine suites (restart runs in two separate server processes).

| Suite | Cases | Report run |
|---|---:|---|
| PreparedFounding | 3 | `145f43318ce84aaab3bed11e1d689feb` |
| PreparedFoundingFlow | 4 | `adbc771be7ea494ea636494b1c817a6f` |
| PreparedFoundingCancel | 1 | `1906f5679be04874a14318526b1c4e3d` |
| PreparedFoundingRestart | 2 | `b0dee4a09bf947fb9acedb7803d58639` |
| FreeFounderMenu | 8 | `f330e2f3f4c1436a9bda75b782053337` |
| SurveyorTerrain | 11 | `d8e90ebe6daf4094a159cdc47bd077df` |
| SurveyorPreparationWork | 9 | `1f1e914efc114202a45ff4f460d25e6d` |
| SurveyorBuilderPreparation | 3 | `2efeb03c2fd041a09fd7aa65b2500063` |
| FreeFounderRecords | 8 | `b51aab727c3f404bb2d7c992782faf10` |

Final pause fix verification: PreparedFoundingFlow `02dfe5fd3b174462a649a40ab922104b` and PreparedFoundingRestart seed/reload `38771fbbc490464fac3290b61920f29c` pass. Pausing now prevents cleanup, and resuming restores it. These reruns also include full destination preservation and ambiguous-operation load checks.

Development and isolated QA packaging succeeded. Surveyor artifact, development-package and menu asset checks passed. Installed founders.1 SHA256 remains unchanged; no installation or publication was performed.

## Builder camp unloading â€” October 15

Added automatic unloading during terrain preparation. When fewer than three inventory slots remain, the original healthy builder walks back to the temporary hut and transfers gathered materials into its actual finite inventory. Tools and food remain with the worker. While fill work remains, one stack of dirt is retained and excess dirt can be stored. Each transfer writes a saved intent, flushes the native inventories, then records completion. A pending transfer loads into recovery instead of being replayed.

Full camps retain all items and report that space is needed. This prevents silent loss but does not yet make very large excavation sites fully self-sufficient: extra temporary storage capacity and high-terrain access remain future work. The new native test fills the builder's inventory during a three-block clearing job and checks unloading, resumed preparation, permanent-hut handoff and pending-transfer recovery. It is not a long-duration terrain endurance test.

| Unloading verification | Cases | Report run |
|---|---:|---|
| CampInventory | 6 | `d9c18b01bb86476f975f7f87a279476b` |
| CampPreparation | 4 | `c6a844fc75304496a65729941fb1b74d` |
| CampPreparationRestart | 2 | `85ccf94c545042ba859f87584abf55fb` |
| PreparedFoundingRestart | 2 | `4bd5ef5c5cef4d5e8d7938fb1e89f736` |
| FreeFounderRecords | 8 | `120c7c48d9574dd3beafb868d3d3f552` |

All 22 named checks passed. The camp restart stops after an actual unloading transfer, starts a new server process with the same world, then completes preparation and handoff. No installed files or live colony saves were changed.
Development/QA builds and the three packaged-artifact/menu checks passed. This remains an isolated development checkpoint, not a release-ready completion of the surveyor feature.

## Physical overflow storage and preparation tools â€” October 15

New prepared-site reviews reserve paid vanilla barrels on dry supported ground beside the temporary hut, outside arrival cells and permanent building envelopes. The estimate allocates one per 256 clearing operations, capped at eight. Storage positions are included in the saved plan/fingerprint and approved-area validation. Older v1 camp records continue without retroactively adding containers or costs.

Founding places and marks the paid barrels before settlers arrive. Missing or replaced barrels block arrivals and inventory access. Builders can unload into them when the hut is full. Cleanup includes every barrel's contents and returns the paid barrel items, preferring the completed outpost warehouse (and registered racks) when available. If the destination cannot fit everything, the camp remains intact. Barrels do not become cross-settlement transport endpoints.

The founding bill now also includes preparation tools calculated from actual per-block tool requirements and item durability, separately from the existing starter/construction reserve. A 301-dirt estimate requests two barrels and three stone shovels. This is finite staging, not unlimited storage: exceptionally large sites can still exhaust the bounded capacity. High-terrain/scaffolding and aquatic preparation remain incomplete.

Storage/tool regression batch: 43 named checks passed.

| Suite | Cases | Report run |
|---|---:|---|
| PreparedFounding | 5 | `eacd0b971a694bdf9be9e046e5b4cac4` |
| CampPreparation | 4 | `755532e91553426fa73df288e76ab2b4` |
| CampPreparationRestart | 2 | `e7cbb1058efe4f3abcf909085161b1b9` |
| PreparedFoundingCancel | 1 | `bf2a4f23ba9a482ab5e87ddf5848bf7c` |
| FreeFounderMenu | 8 | `7ae23c82593446bebca4ade43918a64e` |
| FreeFounderRecords | 8 | `f1b9e21035384798838028ba6e4b1b7a` |
| CampInventory | 6 | `8151ab60835146f0bbfbb97b5c1d7edb` |
| SurveyorPreparationWork | 9 | `4fac62d984e44ca1aff45db73434054a` |

## Larger excavation and movement recovery â€” October 15

The 303-block native excavation test uncovered two movement problems. Builders left camp after freeing only the minimum inventory space, so the next dirt drop could trigger another entire return trip. They now finish unloading eligible cargo while within camp reach, preserving food, tools and the required dirt reserve. Separately, an obstructed nearby target could leave a builder waiting indefinitely. The service now requests a closer native safe-destination path and still requires actual reach and line of sight before modifying the block.

The endurance scenario starts with a full worker inventory and full temporary hut, uses the paid overflow barrels, clears all 303 dirt blocks with finite tools, and hands the original worker to the permanent builder hut. It also checks that a full destination preserves camp custody, then provides a completed native warehouse with a registered rack and verifies exact item counts/components and returned hut/barrel items during cleanup. Permanent building completion is simulated in this fixture; this test does not prove autonomous construction of the complete settlement.

Focused approach regression: `3b63bbe662c34f4caefaabb84837cb67` (4 checks). Full endurance: `4cc554c460714e5c8e535b5542a225f9` (4 checks). Both passed. Save/reload regression and development-package verification follow before this checkpoint is considered verified.

Final movement regression: **22 named checks passed**, including fresh server processes for each restart phase.

| Suite | Checks | Report run |
|---|---:|---|
| SurveyorBuilderPreparation | 4 | `3b63bbe662c34f4caefaabb84837cb67` |
| CampPreparationEndurance | 4 | `4cc554c460714e5c8e535b5542a225f9` |
| CampPreparation | 4 | `df749b4938f64b18b3691df2dc4d7939` |
| CampPreparationRestart | 2 | `b3b78ab1511b46e6ab90d6f706fc75d7` |
| PreparedFoundingRestart | 2 | `ab1618dfd7ce4f52852fc6d25ea93314` |
| CampInventory | 6 | `36c92b55aee643848087560a59e0b623` |

Reproduce from this worktree with Java 21 and the existing isolated Arcology pack:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='./.setup/gradle-arcology-recovery'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite CampPreparationEndurance -ArcologyPath build/surveyor-pack -TimeoutSeconds 900
```

This is a terrain-preparation checkpoint. High-terrain/scaffolding, aquatic preparation, the complete survey-result-to-founding menu flow, renewable producer profiles and whole-feature review remain unfinished.
Development and QA jar builds succeeded after the movement changes. Packaged Surveyor assets, development-package boundary and native menu-asset checks all passed. Installed founders.1 SHA256 remains `FCD323B1C7E04146BF237684E78D1672B95578D807C95CE1F4550519BCAA3F92`. No install, live-world changes, publication or push.

## Hills and supported gaps â€” October 15

A five-layer stepped-hill integration test exposed incomplete shared-path inspection: access areas were checked only three blocks above floor level, so the plan could omit the top of a hill and direct work underneath it. New surveys now inspect through the existing eight-block cut limit, including the next layer needed to reject excessive excavation. No previously saved terrain plan is silently rewritten.

Focused RED: `646d7b63068d4dc4b03e681d18b6ec2f` failed full-hill costing and shared-area excavation-limit checks. GREEN: `7c1109c9d0de471c94ae6881f8afedd9` passed all seven prepared-founding checks.

- Native five-layer hill: `d564da3079dd4990866b98984ad16ac6`, four checks. The original builder clears 165 hill blocks plus three approach obstacles, with finite tools and real navigation, then completes permanent-anchor handoff and camp-to-warehouse custody.
- Mid-hill process restart: `b395fd6cec88481da423be6ec0a0db3a`, seed/reload checks. Seed stops after at least 40 actual clearing operations; the next server process resumes the saved original builder and finishes the same 168-operation plan.
- Supported gap: `c8436b937580488f91b99539b9771478`, four checks. A 3x3 gap three blocks deep is filled with 27 paid dirt blocks, bottom-up from solid support, followed by normal handoff/cleanup.

These tests cover ordinary stepped hills and shallow dry gaps. They do not establish arbitrary cliff/scaffolding access, shallow-water containment or the full renewable-resource outpost feature. As in the earlier tests, permanent building completion is supplied by the fixture to check cleanup, not constructed by a native builder during this test.

Hill/gap checkpoint: **48 named automated checks passed** across the following fresh runs.

| Suite | Checks | Report run |
|---|---:|---|
| PreparedFounding | 7 | `7c1109c9d0de471c94ae6881f8afedd9` |
| CampPreparationHill | 4 | `d564da3079dd4990866b98984ad16ac6` |
| CampPreparationHillRestart | 2 | `b395fd6cec88481da423be6ec0a0db3a` |
| CampPreparationGap | 4 | `c8436b937580488f91b99539b9771478` |
| SurveyorTerrain | 11 | `8e0a7d5534f941a883f634fd598fe1ca` |
| FreeFounderMenu | 8 | `faea365841924ffdb51075e5f9be1893` |
| FreeFounderRecords | 8 | `bb56b73707cc426b8938ebe54d664213` |
| CampPreparationEndurance | 4 | `727e0f6487ad456dbf75760ff49ee73c` |

Use the same test command above with `-Suite CampPreparationHill`, `CampPreparationHillRestart` or `CampPreparationGap` to reproduce the new scenarios.
Hill/gap development and isolated QA builds succeeded. All three package/asset checks passed. Installed founders.1 hash remains unchanged; no live-world access, installation, publication or push occurred. Remaining work includes steep/cliff access, aquatic containment, complete survey-to-founding menus and native renewable-resource profiles.

## Steep terrain and fill-space access â€” October 15

The native vertical-cliff test exposed two issues: directing the builder toward the solid work block, and clearing entire horizontal layers while lower shelves obstructed the next target. New plans clear each column from top to bottom. The builder selects a nearby dry, supported standing position with headroom and line of sight, then uses native navigation. Actual reach and visibility are still checked before every edit. Existing saved operation order remains unchanged.

Foundation regression then exposed the builder standing inside, or partly over the edge of, its next fill block. The builder now requests a different standing cell before placing dirt, including when its body overlaps from an adjacent cell. Collision protection in the paid block transaction remains intact. No teleportation, free blocks or tool bypass were added.

Evidence so far: column-order RED `d54e4cd9545f42edb3ac507537873d33`; terrain GREEN `af2c524b363f483ba220b85d3a8e1330` (12). In-fill RED `66497121aa494c0c8042b0ca11bbbd31`; adjacent-edge RED `56ef6514fe4c456287529a7dbc682e33`; both movement checks GREEN `a04a0e2baaee46559bb5e5762446166b` (six focused checks). Full integration verification follows below.

The steep fixture is a three-block-high vertical dirt face, not arbitrary cliff or scaffold support. Aquatic containment and the complete survey-result/renewable-production flow remain unfinished. The installed build and live world were not modified.

Final terrain verification: 41 named checks passed across these suites (one latest successful report per suite).

| Suite | Checks | Report run |
|---|---:|---|
| SurveyorTerrain | 12 | `af2c524b363f483ba220b85d3a8e1330` |
| CampPreparationHill | 4 | `95e7265fabce41c9a36616be3e6d585d` |
| SurveyorBuilderPreparation | 6 | `a04a0e2baaee46559bb5e5762446166b` |
| CampPreparationGap | 4 | `75c6f05cf6dd411a82b09ec4099bc197` |
| CampPreparationCliff | 4 | `c9cea697198b4471bee536a09feaeb45` |
| CampPreparationHillRestart | 2 | `24f86bc29c60451eb0e09039d7ce90ae` |
| SurveyorPreparationWork | 9 | `f69fc7f7a2e84539b639e6d7afb4e319` |

The restart test stops after at least 40 actual hill-clearing operations, restarts the process, then completes the original saved plan and handoff. As in earlier preparation fixtures, completed permanent buildings are supplied by the test for inventory cleanup assertions; this is not proof of autonomous full-settlement construction.
Development and QA jar builds succeeded; packaged Surveyor assets, development-package isolation and native menu assets passed all three checks. Installed founders.1 SHA256 remains FCD323B1C7E04146BF237684E78D1672B95578D807C95CE1F4550519BCAA3F92. No install, live-world modification, publish or push.

## Enclosed shallow-water preparation â€” October 15

New prepared forestry sites can include enclosed source-water pools up to two blocks deep (maximum 512 wet cells). Each filled cell costs one real dirt block. The estimate records natural solid walls, the bottom and clear surface; it rejects open/flowing water, lava, deeper pools, missing walls and water plants. An explicit preserved-cell set rejects overlapping preparation. Aquatic production profiles still need to supply their field footprints when integrated.

Wet plans use version 2, while unchanged dry plans continue to save version 1. A wet operation must carry its approved containment plan: arbitrary water-fill operations are rejected. Before spending inventory or changing a block, the builder rechecks the recorded boundary and previously completed fills. A breached or changed boundary stops work. Existing journal rules preserve uncertain transactions instead of replaying them.

| Suite | Checks | Report run |
|---|---:|---|
| SurveyorWater | 14 | `b095522662834f70969d73dafdf6c41c` |
| SurveyorTerrain | 12 | `4c75f87e795a410fa4baaa04f8878292` |
| SurveyorPreparationWork | 9 | `3cca583c1cf5499c8c6b89343ea8a938` |
| CampPreparationWater | 4 | `399873b0b86946c5905b634c6bd81fa9` |
| CampPreparationWaterRestart | 2 | `a0181c2d328c4c3eb3810f1436b7bb75` |
| PreparedFounding | 7 | `36643c84f0304acf9789787b1059e1ec` |
| SurveyorPreparationRecords | 6 | `7381fa4e7a814d40916fe762c18e4baa` |
| FreeFounderRecords | 8 | `6fe28ef058a847328fe83a5ccc4be13a` |

All 62 named checks passed. The native fixture fills a 3x3x2 pool with 18 paid dirt blocks and clears three approach obstacles. Restart stops after at least one wet fill and resumes the same 21-operation plan in a fresh server process. Permanent construction completion is still supplied by the fixture for cleanup assertions. These tests do not establish open-lake retaining-wall construction or complete autonomous renewable production.

Surveyor menu now includes a **Survey Findings** toggle showing the latest 32 completed surveys for the owned colony. Reports survive removal of the original hut because they are derived from the existing persisted colony-level records. They show exact samples/source coordinates, distinguish no-sample results, and explicitly mark renewable production and buildable-site approval as unverified. Foreign-colony and foreign-owner records are excluded. This is the reports step of the survey-to-founding flow, not automatic site selection or production activation.

Menu RED `a2c205c966e34dfba224f7a7d651e1a7`; GREEN `35542ec8f9ab4b6d9fb1def5eba335dd` (nine checks). The interactive layout has been compiled; packaged asset checks follow. No manual game/menu session has been run for this new toggle.

Final callback-hardening and report regression: **82 unique named checks passed** across the ten suites above plus SurveyorMenus and SurveyorRecords. Updated latest runs supersede their earlier entries:

- SurveyorWater: 15 checks, `692f8d908080444385b29383847f294e`.
- CampPreparationWaterRestart: seed/reload, `b0339264bb3c46db8b8a88e0f54a29e7`.
- SurveyorPreparationWork: 9 checks, `ca5614f999054e16aa0d3ca9942003c1`.
- SurveyorRecords: 10 checks, `e49b56900c7c4dcab78a68bbbbee910b`.

The additional water regression proved that a native placement-event callback could breach the wall after validation (`8741d2b8bbc14c6fb76fb976ea567b90`, RED). The transaction now rechecks containment after that final callback and before world/inventory mutation. If interrupted after durable intent, the existing recovery journal remains pending.

Remaining survey-to-production work: derive and retain suitable building-site candidates, reopen bounded inspection tickets, bind approved survey identity and exact target to the founding draft, add versioned native producer/field profiles, verify research and sample custody, and exercise actual production/export. Reports deliberately do not enable founding from an unreviewed sample coordinate. High-cliff scaffolding and open-water retaining-wall work are also outside this completed slice.
Development and QA builds succeeded after the final changes. All three package/asset checks passed. Installed founders.1 SHA256 remains FCD323B1C7E04146BF237684E78D1672B95578D807C95CE1F4550519BCAA3F92. No install, live-world change, publish or push.


## Resource founding profiles — October 15

Implemented an immutable exact-target `ProductionProfile`, a versioned saved `SurveyOrigin`, and the separate `RESOURCE_FOUR_V2` founding policy. New resource grants use Mayor, Builder, Courier and PRODUCER. Old FREE_FOUR_V1 grants retain FORESTER and their original saved format; mismatched policy/role records are rejected. Survey evidence can be revalidated against the owner, colony, completed report, exact target and sample source.

The actual Arcology catalog/layout now selects native lumberjack, florist, farmer or Plantation huts. All six supported profile families fit the existing eight-hut layout without upgrade-envelope overlap. Field paths are identified separately; field construction is **not yet integrated**. Starter bills now carry the exact profile and finite tools/seed stock, including birch saplings rather than oak, florist compost, and seagrass bonemeal. Receipt decoding verifies that the paid bill and profile agree.

Native producer configuration tests verify sapling and flower exclusion filters, native flower building-level requirements, wrong-hut rejection and the Plantation's actual `minecolonies:planter` job. These are configuration tests, **not full grow/harvest/export evidence**. The Mayor runtime and automatic field setup still need integration.

| Suite | Checks | Latest report |
|---|---:|---|
| ProductionProfiles | 13 | `654316415d6942229776eea43d1fd222` |
| ProductionLayouts | 6 | `30f98d5a011a489598ba3d1deb09ad40` |
| ProductionWorkers | 5 | `684f7dd16a184bce905d2861cdaa4b41` |
| FreeFounderRecords | 15 | `fbb3645292c94953a15c0e204abe7527` |

Observed RED runs: profiles `93d02da869a645818f7534d1027f0927`; binding `87287dfe4a14457b85972e0ad7837320`; versioned policy `933c90bc9e994c89a99a4f44017e8b50`; layouts `6f8de2c02a6a4994bfd115f1a832faa9`; starter supplies `053b981c031148bc866b3217973f4306`; native worker configuration `4186a651666f4e26ba0a8949a0e89674`.

New survey-bound reviews remain deliberately blocked from founding until automatic site selection, finite sample handoff, field construction, producer startup and the full production/delivery tests are complete. Wheat seeds are supported in the profile model; pumpkin/melon conversion remains explicitly rejected until its real recipe/custody path is implemented. This is not a release-ready full feature, and no new profile is advertised as operational.

Regression and package verification for this implementation: **78 named checks passed**, plus all three packaged artifact/menu checks. Additional runs: FreeFounderPolicy 12 (`5b8d7051721c43c981676f901e210f31`), FreeFounderLayout 7 (`3ebc9f0bd5764567a356609c1bfcd86c`), PreparedFounding 7 (`9890fa16ded8482c8e505ce5847f1a43`), FreeFounderFlow 13 (`5f265eb8ec4e467395577533c3c97288`). Both development and QA builds succeeded. Installed founders.1 SHA256 verified unchanged: `FCD323B1C7E04146BF237684E78D1672B95578D807C95CE1F4550519BCAA3F92`.

Outstanding completion gates: automatic suitable-site selection; survey menu-to-founding connection; actual starter-sample custody and fruit-to-seed recipe conversion; native farm/Plantation field construction and aquatic preservation; Mayor producer startup, research/capacity checks and operating consumable resupply; exact-resource production/export endurance; copied-colony migration, packaged live menu checks, and the one whole-feature independent final review. None of those gates is represented as complete by this checkpoint. No live world, installation, public release or Git remote was changed.
