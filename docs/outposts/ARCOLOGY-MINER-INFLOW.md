# Regular Mine dirt-fill loop

The reported worker is a regular Mine worker, with dirt selected as its fill material.

MineColonies 1.1.1403's `getNextBlockInShaftToMine()` fills fluids in one horizontal plane, then selects solid blocks in that same plane for excavation. An overhead water source survives that pass. Mining its dirt plug allows water to return and repeats the cycle.

The isolated native-action reproduction recorded 240 removals, 241 placements, and 240 shovel durability consumed in 1,200 test ticks. With the fix, the overhead cell is sealed first; the plug is removed once, costing one shovel durability, and the opening stays dry.

## Development fix

`ArcologyMinerFluidSealMixin` runs immediately before the native shaft action mines its selected block. For an Arcology Horizons Mine only, it checks all six neighboring cells, requests enough of the configured fill material, and uses the native inventory-consuming placement method to seal fluid before excavation. Missing supplies leave the barrier intact. Waterlogged structures and block entities are preserved; these require manual leak removal. No change to Quarry, branch/tunnel construction, other styles, or the submitted alpha is claimed.

## Verification

`ArcologyMinerInflowTests` has six checks: overhead water with dirt fill, zero/insufficient fill, resupply, preservation of waterlogged chest contents, unrelated-style boundary, and ordinary dry stone excavation. These are focused native shaft-action tests with real world fluid ticks, inventory, and tools; the citizen scheduler is paused and the native action is invoked by the fixture. This does not certify every aquifer layout or autonomous travel to a flooded shaft.

Evidence: `build/miner-inflow-red-5/ArcologyMinerInflow.json`, `build/miner-inflow-verified/ArcologyMinerInflow.json` and matching logs.

Run from this worktree in PowerShell after copying the candidate pack into the disposable run's `blueprints/arcology-horizons` directory:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='./.setup/gradle-arcology-recovery'
.\gradlew.bat runOutpostFoundation -PfoundationSuite=ArcologyMinerInflow "-ParcologyPath=$PWD/build/arcology-placement-candidate-2" "-PprobeOutput=$PWD/build/miner-inflow-verified" --offline --no-daemon --console=plain
```

`tools/Package-MinerFix.py` produces a separate private development JAR by copying the exact submitted alpha and changing only its version, mixin registration, and adding the tested mixin class. It verifies the baseline SHA256 and all unchanged entries, so unrelated safe-supply development is not included. Shut down Minecraft and back up the world before replacing the alpha JAR in a private test instance. Keep only one Colony Horizons JAR installed. Nothing is automatically installed or published.
