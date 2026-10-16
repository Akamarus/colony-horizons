# Safe hut supply routes — development build

Implemented in the separate `feat/safe-supply-routes` worktree. Submitted alpha.1, its public source/tag, and CurseForge file are unchanged. New artifact: `build/supply-dev-libs/colony-horizons-0.1.0-supply-dev.1.jar`. Not uploaded or installed in the player's launcher.

## Working behavior

The colony owner can bind existing native huts as home/outpost endpoints and create directed item routes in the same colony and dimension. Several huts can serve an outpost. This does not found colonies, claim land, construct huts or assign workers.

Routes retain source reserves, count incoming cargo toward destination targets, preserve item components, and hold anything that does not fit. Pausing freezes travel; cancellation returns undelivered cargo when possible. Already delivered goods stay delivered. Unloaded, replaced, foreign-claimed or no-longer-owned huts block transfers.

Travel uses saved server ticks. No citizen or courier travels through dangerous terrain, no corridor is loaded, and no progress accrues while the server is offline.

## Try it

Use a disposable test world with existing registered huts you own. Supplies go in the **hut block's own inventory**, not connected warehouse racks. Names are single words, up to 32 characters.

```text
/chsupply bind home false <hut-x> <hut-y> <hut-z>
/chsupply bind woods true <hut-x> <hut-y> <hut-z>
```

`false` designates home; `true` designates an outpost. Hold a spruce log, then:

```text
/chsupply route woods home 64 16 32
```

Target 64 logs at home, retain 16 at woods, ship batches of up to 32. The held template is not consumed. Components match exactly, including names/tool damage. Hold bread for the return route:

```text
/chsupply route home woods 32 16 16
/chsupply status
/chsupply pause <route-uuid>
/chsupply resume <route-uuid>
/chsupply cancel <route-uuid>
```

Creation/status show route UUIDs, cargo, travel remaining, delivered count and blockers. Travel is distance-based, at least 10 seconds and at most one hour, updated every five seconds. Blocked arrivals take longer. The server validates colony ownership; operator privileges do not bypass it.

## Custody and recovery

Transfers stage ledger and inventory changes first, write a prepared record containing before/after ledger snapshots, apply native inventory contents, save chunks, then **read persisted hut NBT back and verify it** before publishing committed custody. Reading back matters because Minecraft can log a failed chunk save without throwing it to the caller.

Exceptions stop the network. A pending handoff found at startup stays quarantined and is never blindly replayed or replaced by an empty network. Preserve the world and dimension-local `data/colonyhorizons_native_supply.dat` if recovery is reported. An operator must reconcile inventory and recorded custody; there is no unsafe automatic reset/cargo-grant command.

Tests cover clean server restarts and injected failures, not universal power-loss or disk-corruption recovery. Synchronous chunk checkpointing can pause large worlds; performance work is needed before release.

## Remaining scope

- Warehouse racks and district-local courier restrictions are not integrated. Existing native couriers retain their existing behavior; this does not make every native courier trip safe.
- Endpoint labels do not create outpost membership records. Founding, Mayor development and a management screen remain separate work.
- The native fixture seeds stock/registers huts. A complete real-forester harvest, delivery and local resupply-consumption loop remains to be tested.
- No automatic chunk tickets; production and receiving endpoints need ordinary loaded chunks.
- Officer delegation, editing/removing routes and a recovery UI remain future work.

## Reproduction

From this worktree, use Java 21 and the pinned dependencies. Supply the installed Arcology Horizons pack folder:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
./tools/Test-OutpostFoundation.ps1 -Suite SupplyBridge -ArcologyPath '<installed-pack-folder>' -TimeoutSeconds 900
./tools/Test-OutpostFoundation.ps1 -Suite SupplyNative -ArcologyPath '<installed-pack-folder>' -TimeoutSeconds 900
./tools/Test-OutpostFoundation.ps1 -Suite Supply -ArcologyPath '<installed-pack-folder>' -TimeoutSeconds 900
./tools/Test-OutpostFoundation.ps1 -Suite SupplyNativeRestart -ArcologyPath '<installed-pack-folder>' -TimeoutSeconds 900
./gradlew.bat supplyDevJar --no-daemon --console=plain
```

JSON and logs are retained under `build/reports/foundation`. Restart testing uses three separate server processes with huts 512 blocks apart. Later phases never recreate IDs, huts or stock. QA-only forced chunks, initial stock and the injected save failure are excluded from the development JAR.
