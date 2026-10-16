# Multi-building Arcology outpost foundation

This phase implements internal settlement membership and tests real MineColonies registration and persistence. It does not expose a playable founding menu or automate construction, resource production or transport.

## Implemented

- A dimension-local schema-1 saved index groups several native buildings and citizens under one outpost UUID and home colony ID.
- Records preserve approved claim chunks, development and forestry volumes, blueprint references, workers/residents, an optional terminal, blockers and PLANNED/PAUSED/RECOVERY_NEEDED state.
- Invalid memberships, overlapping building volumes/claims and duplicate citizen membership across districts of the same colony are rejected before mutation. Different colonies can use the same citizen number.
- Read-only reconciliation checks native colony, claim ownership, loaded hut identity, citizen existence and workplace association. It does not claim land, force-load chunks, recreate citizens or assign jobs.
- Unknown schemas and malformed NBT fail closed. A failed native saved-data read cannot silently replace an existing file with an empty writable index.
- A read-only Arcology catalog inspects 25 core level-1 through level-5 blueprints using the pinned Structurize loader, checks their original palettes and records hashes/anchors/bounds. No source assets are rewritten or bundled.

## Fixture and limits

The disposable native fixture registers six real huts 512 blocks from home: two foresters, one builder and three homes. It assigns three real citizens through native work/living modules and uses actual Arcology blueprint references. It checks ownership of 49 satellite claim chunks and a sampled unclaimed gap.

The fixture places hut anchors, prepares test ground and sets level one directly. This is registration evidence, **not proof that a builder has constructed the Arcology structures**. It does not test doors, stairs, connectors, pathfinding, upgrades or harvesting.

Three separate server processes seed, reload, then reload a paused record. The reload phases never recreate the colony or citizens. Assertions cover exact work/home assignments, forester/builder modules, one live entity per citizen, inventories, claims and addon pause state. Primary-load errors or backup-restoration messages in either output stream fail the runner.

Each runner session uses its own disposable game directory and copies the installed pack there, preventing stale blueprint files from satisfying a test. Normal gameplay does not enable the QA profile-cache fixture.

## Verification

Verified 2026-10-10 against the pinned runtime:

| Check | Result |
| --- | --- |
| Clean build | PASS |
| Arcology catalog | 6/6 cases; 25 blueprints |
| Records/native membership | 15/15 cases |
| Native settlement restart | Seed + reload + paused reload PASS |
| Existing cargo transfers | 9/9 cases |
| Existing shipment restart | All 4 process phases PASS |
| Resource audit | 1 GameTest PASS; 1,671 items, 1,592 recipes; zero candidate/inherent errors |
| Primary-load log guard | Three negative fixtures rejected; normal log accepted |
| Normal-startup isolation | No foundation QA/profile-injection marker |
| Pack distribution isolation | No blueprint or throwaway spike entries in the built jar |

Evidence JSON files are retained in this directory's evidence folder. Final foundation logs are archived in ignored .setup/foundation-final-evidence; command summaries and the final build/audit log remain in .setup. A clean build removes earlier build/reports output.

Run IDs: Arcology e8a86c25ec3c421097b346d58d84f942; Records 252f5c74187c48499b3ff1d642cc0e95; native restart 1c2167a156d8404bbc1557301f377347; transfers d118a8799b60421ebbeb9e04f628568b; shipment restart 9d139a7b2aa64e24a91ad1d47771c0b5.

Independent review caught saved-data fallback loss, malformed NBT defaults, missing claim diagnostics, incomplete backup-log rejection and stale pack copies. Each is corrected with regression coverage. The strict live-entity assertion also caught a fixture ordering error; endpoint chunks now load before the delay, matching the proven native travel test. No assertion was weakened and no reload phase reseeds.

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='<user-home>/Documents/ChatGPT/Minecraft Colony Mod/.gradle-local'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-FoundationLogGuard.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite Arcology
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite Records
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite NativeRestart
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostProbe.ps1 -Suite Transfers
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostProbe.ps1 -Suite Restart
./gradlew.bat clean build runGameTestServer --no-daemon --console=plain
```

The foundation runner accepts -ArcologyPath for another installation. Java and Gradle cache paths above describe the verified local environment, not universal installation requirements.

## Next work

1. Prove native builder construction and practical access using the measured Arcology footprints, including a tested connector/foundation layout.
2. Prove the two native foresters harvest and replant within the intended work area, with visible food/tool/sapling needs.
3. Establish local storage/courier boundaries and safe supply shipments between settlement endpoints. Native global colony logistics must not silently become long unsafe walks.
4. Add player-facing founding, development queues, material requests and route status; then Mayor development automation.

The installed pack lacks a Gatehouse-named blueprint. Terminal readiness remains unmet; there is no silent style substitution. Native protected travel also needs persistent safe-arrival validation before gameplay use. Ars integration remains deferred.
