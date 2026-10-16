# Quarry dirt-fill regression — 2026-10-12

Tested both Simple Quarry and Medium Quarry against the regular Mine report: dirt fill below an overhead water leak, with a second wet side and a retained water source above the seal.

Result: **8/8 checks passed**, native GameTest server exited successfully. No additional production changes were required. The regular Mine fix targets `EntityAIStructureMiner`; these tests exercise `EntityAIQuarrier`, whose native mining action already seals all six adjacent directions. The existing Arcology quarry supply guard prevents excavation without sufficient fill.

For each quarry:

- Zero and one dirt cannot seal the two wet neighbors: the dirt barrier, both fluid cells, remaining stock, and shovel durability remain unchanged. A native dirt request exists.
- Supplying two dirt seals both overhead and side cells and consumes exactly two blocks before excavation.
- Native mining removes the dirt plug once, costing one shovel durability.
- The opening remains air and dry for 200 subsequent fluid-update ticks. The overhead source remains water, both seals remain dirt, and shovel wear does not increase.

This is a focused native-action regression. The scheduler is paused, the fixture assigns a native quarry work order and target, and it calls native `doMining` during actual server ticks. It does not simulate block breaking or fluid updates. It does not certify full autonomous quarry completion, courier resupply, or every aquifer geometry.

Evidence: `build/quarry-dirt-inflow/ArcologyQuarryDirtInflow.json` and `quarry-dirt-inflow.log`.

With the candidate pack installed in the disposable `runs/outpost-foundation/blueprints/arcology-horizons` directory, run from the development worktree:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='./.setup/gradle-arcology-recovery'
.\gradlew.bat runOutpostFoundation -PfoundationSuite=ArcologyQuarryDirtInflow "-ParcologyPath=$PWD/build/arcology-placement-candidate-2" "-PprobeOutput=$PWD/build/quarry-dirt-inflow" --offline --no-daemon --console=plain
```

No submitted artifact or live game was modified.
