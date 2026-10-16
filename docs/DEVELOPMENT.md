# Exploration Hut development snapshot

Version: 0.1.0-beta.1. Java 21, Python 3, Minecraft 1.21.1,
NeoForge 21.1.250 and the dependency versions in README are required.
Run commands from the repository root. Initial Gradle dependency resolution needs network access.

## Build and lightweight checks

```powershell
.\gradlew.bat supplyDevJar supplyQaJar --no-daemon --console=plain
python tools/Test-SupplyDevPackage.py build/supply-dev-libs/colony-horizons-0.1.0-beta.1.jar --version 0.1.0-beta.1
python tools/Test-SurveyorArtifact.py build/supply-dev-libs/colony-horizons-0.1.0-beta.1.jar
python tools/Test-SupplyMenuAssets.py
```

The five `surveyor*.blueprint` files in `blueprints/arcology-horizons/fundamentals`
are displayed as Exploration Huts. The historical Surveyor registry ID is retained
for saved-world compatibility. Copy the complete pack to an isolated game's
`blueprints/arcology-horizons` folder. Back up worlds before installing the development
addon; keep only one Colony Horizons JAR. QA artifacts are only for disposable tests.

## Native automated checks (Windows)

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ExplorationRecords -ArcologyPath blueprints/arcology-horizons -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ExplorationGathering -ArcologyPath blueprints/arcology-horizons -TimeoutSeconds 900
```

Additional suites include ExplorationDiscovery, ExplorationSupplies,
ExplorationLifecycle, ExplorationCourier and ExplorationMenus. See the runner's
parameters and the [verification record](outposts/EXPLORATION-RESULTS.md). Historical
report IDs document previous runs; reports themselves and private worlds are not committed.

Packaged client harnesses require a separately installed test launcher with the pinned
Minecraft/NeoForge versions. Set `COLONY_HORIZONS_TEST_LAUNCHER` to its root directory.
The optional copied-colony founding harness also requires
`COLONY_HORIZONS_PLAYTEST_INSTANCE`; use a verified backup as its source world.
These are specialist Windows harnesses, not one-command portable tests.

## Scope and limits

Real discovery unlocks 36 simulated gathering outputs. Gathering costs two bread
and 6,000 eligible ticks. One expedition per colony; owner online in the Overworld;
no offline catch-up. Repeating trips stop on shortages, illness or full inventory.
Ambiguous interrupted inventory transactions are held rather than replayed; no
automatic repair button is included. Pumpkin and melon are discovery-only.

Outpost founding, Mayor and remote construction code remain for existing saves and
regression coverage, but that feature direction is paused. Multiplayer certification,
wider modpack testing and the next public release gate remain outstanding.
The alpha.2 release can be rebuilt from its unchanged tag. Legacy publicJar tasks
in this development build must not be treated as a new alpha.2 release.

