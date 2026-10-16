# Arcology placement fixes — development only, 2026-10-12

The submitted alpha, public release artifacts and live playtest instance were not edited. These fixes are blueprint-only; no new addon JAR or supply-route code is included.

## Supply raft

The 7×6×7 raft had a primary offset on a non-tile block and no groundlevel marker. MineColonies defaulted to three submerged layers. Use the existing supply barrel at (1,0,1) as primary anchor and give its blueprintDataProvider a groundlevel marker at relative (0,0,0). Native BlueprintTagUtils now computes one submerged layer. The raft geometry, barrel inventory and bed remain unchanged. Changing the anchor changes the preview's reference point; use the build-tool arrows to position its bottom layer at the water surface.

The native placement checker allows some missing water and exempts fluid-placeholder cells. This patch preserves that rule; it does not promise to reject every floating placement. Solid obstructions above the raft still fail validation.

## Builder Recall

Recall uses EntityUtils.getSpawnPoint through TeleportHelper. It checks standing space and an adjacent standing space but not an exit route. An elevated builder module has two edge pockets behind the hut that satisfy the spawn checks but trap pathfinding behind the hut/stair. Ground-level fixtures hide the bug by allowing workers to walk around outside.

At each builder level 1–5, replace air at local (0,1,1) and (1,1,0) with spruce-fence rails. This excludes the two pockets from Recall selection while preserving the full staircase, hut position/orientation, inventories, upper floors, dimensions and module connection geometry. These coordinates are in the unrotated blueprint, not world coordinates.

## Evidence

- `build/placement-red/ArcologyShipPlacement.json`: original failed all eight transformations, waterLayers=3, surface=false.
- `build/recall-elevated-red/ArcologyBuilderRecall.json`: original failed 15 of 40 elevated recall/exit cases.
- `build/recall-verified/ArcologyBuilderRecall.json`: candidate passed 40/40 (five levels × eight rotations/mirrors). Native Recall followed by real citizen navigation to an external connecting corridor, recovery disabled.
- `build/ship-verified/ArcologyShipPlacement.json`: candidate passed 8/8 native waterline/surface/solid-obstruction scenarios.
- `tools/Test-ArcologyPlacement.py`: exactly six files changed; builder differences limited to ten rails; all stair geometry, tile contents and other files preserved.

The measured leg is native Recall → outside corridor. The fixture uses a native citizen with controlled walk-command AI, not an entire active construction job. It does not prove arbitrary player-altered structures are reachable.

## Reproduce

From this worktree, use Java 21 and the configured Gradle cache:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='./.setup/gradle-arcology-recovery'
.\gradlew.bat runOutpostFoundation -PfoundationSuite=ArcologyBuilderRecall "-ParcologyPath=$PWD/build/arcology-placement-candidate-2" "-PprobeOutput=$PWD/build/recall-repeat" --offline --no-daemon --console=plain
.\gradlew.bat runOutpostFoundation -PfoundationSuite=ArcologyShipPlacement "-ParcologyPath=$PWD/build/arcology-placement-candidate-2" "-PprobeOutput=$PWD/build/ship-repeat" --offline --no-daemon --console=plain
```

Use `Repair-ArcologyPlacement.py ORIGINAL_PACK NEW_OUTPUT` to reproduce the six-file asset transformation; it refuses an existing output. Run `Test-ArcologyPlacement.py ORIGINAL_PACK CANDIDATE_PACK` for preservation checks. The two new suites are registered in FoundationGameTests; use the direct commands above, not the older PS runner's fixed suite whitelist.

## Install after the game closes

Back up the instance's existing `blueprints/arcology-horizons` folder. Extract the development blueprint ZIP into `blueprints`, replacing that pack folder. The final path is `blueprints/arcology-horizons/pack.json`. Restart Minecraft to refresh loaded blueprints.

This does not modify already-built huts. Existing huts need the two safety rails added physically or a repair/rebuild with the updated blueprint. A trapped worker must first be released or recalled after the rails are in place. Do not remove the spiral stair treads: they are needed for access to the upper levels. A fresh full playtest ZIP is also supplied for a separate CurseForge profile; it contains no existing worlds.
