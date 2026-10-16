# Arcology native construction and forestry

This milestone adds a reproducible native acceptance suite, not a player-facing founding feature. MineColonies performs the construction and forestry; the addon does not emulate either job.

## What the fixture proves

- A native builder starts with a level-zero Arcology builder hut and constructs it, two forester huts, and three homes through native work orders. No building-level setters or completion hooks are used.
- Six measured module envelopes do not overlap; the external pack's level 1–5 bounds fit the reserved spaces. The fixture prepares solid superflat ground beneath them. This is a ground-level layout, not proof of stacked connectors or arbitrary-terrain placement.
- Each citizen navigates between an adjacent workplace position and an adjacent position at their actual home bed. Both foresters also walk to their forest approach and back. Native stuck-handler teleporting and terrain alteration are disabled for access and forestry so those recovery actions cannot make an inaccessible route pass.
- Two native foresters each cut a separate five-log oak tree, collect at least five logs, damage their axes, and plant an oak sapling at the harvested base. All five logs of an outside-area control tree remain intact.
- Citizen IDs, entity UUIDs, native manager membership, population, colony and work/home assignments are checked. Movement is sampled every 20 ticks, with a maximum 12-block step and 100-block distance from the satellite.

## Required bootstrap and limits

The builder receives starter tools, bread and finite spare tools. The fixture supplies its real calculated building-material requirements into its local hut, including item components. These are test supplies, not automated resource generation for gameplay.

The foresters receive bread and **one starter oak sapling each**. Their axes and hoes must come from their local huts; each hut also contains 16 reserve saplings and bread. This pinned native version requests a hoe even with its default leaf-clearing setting.

A supply limitation was reproduced: starting with no sapling, a forester harvested five logs and retrieved 16 saplings from its hut, but the first tree's replant attempt timed out before returning. The native replant routine has a short retry budget. Restoring random-tick speed alone did not resolve this in the headless fixture. Starter saplings allow the actual native replant cycle to complete. The suite does not claim automatic food/sapling replenishment or sustained production over many cycles.

Native colony activity requires a nearby subscriber. The isolated fixture supplies a headless ServerPlayer subscriber and force-loads its test chunks. This does not prove unattended/unloaded outpost simulation. Test trees are seeded once; random ticks are disabled to preserve the replanted sapling for observation. The fixture never harvests or replants trees after setup.

No warehouse, courier, cargo route, Mayor, founding UI, upgrades or inter-settlement food delivery is implemented by this milestone. Native local hut pickup is demonstrated; colony-wide courier routing remains a separate integration task. Arcology files remain external and are not bundled or modified.

## Reproduce

From the repository root, using Java 21 and the existing local dependency cache:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='<user-home>/Documents/ChatGPT/Minecraft Colony Mod/.gradle-local'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite Operations -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite Records
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/Test-OutpostFoundation.ps1 -Suite NativeRestart
./gradlew.bat clean build runGameTestServer --no-daemon --console=plain
```

The runner accepts `-ArcologyPath`; its default points to the installed Arcology pack. Each session has its own disposable game directory, copied pack, world and exact five-case result manifest. The normal game does not register this suite unless the existing foundation QA property is enabled.

## Next implementation

Establish safe endpoint inventories and persistent shipments between the home colony and outpost, with reserve policies and visible shortages. Keep native couriers local so shared-colony logistics cannot silently send them walking between distant districts. Then expose the verified construction sequence through player founding/development controls and Mayor automation.

## Verification record

Verified 2026-10-10 against Minecraft 1.21.1, NeoForge 21.1.250 and MineColonies 1.1.1403-1.21.1:

- Final Operations run `b7fe3c7f1d524705938bee0c0a4c3671`: all five required cases passed; process exit 0. [Retained results](evidence/arcology-operations.json).
- Records run `aadc9b7091a4431fb8b8b6c18f0431bd`: all 15 cases passed. [Retained results](evidence/arcology-operations-records.json).
- Clean build and normal resource-audit GameTest passed: 1,671 items, 1,592 recipes, zero candidate/inherent errors. The normal run contains no foundation QA marker; the built jar contains no blueprint assets or throwaway spike classes.
- Final operations logs and detailed checkpoints are retained locally in ignored `.setup/operations-final-evidence`; regression command logs use `.setup/operations-*.log`.

Review corrections include actual per-worker bed/workplace routes, cardinal-adjacent approach cells, disabled native recovery teleporting during access/forestry, stable manager membership and assignments, and checking the full outside-area control trunk. A first attempt deliberately failed before fixture implementation. Subsequent failures exposed native activation, provisioning and starter-sapling requirements; completion is never simulated by the test.

Native restart regression `2c7ec4b797994c36b81f4abad33fbc1e` passed all three separate processes: seed, reload and paused reload, including the primary-load log guard. Phase JSON reports are retained beside the Operations evidence.
