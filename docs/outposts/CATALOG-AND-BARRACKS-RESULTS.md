# Recipe catalog and Barracks upgrade development build

Version: `0.1.0-dev.catalog.1`. Minecraft 1.21.1, NeoForge 21.1.250, MineColonies 1.1.1403. Installed playtest pack and published releases remain unchanged.

## Using the catalog

Open a crafting hut's normal recipe list, then choose **Recipe catalog**. Search by output name or recipe ID, select a result, inspect its output and inputs, and press **Learn recipe**. The `+` buttons cycle supported ingredient variants. Teaching requires no ingredients in the player's inventory; actual production still consumes normal ingredients and fuel.

The normal recipe list retains removal, disabling and priorities. **Manual teaching** opens the existing teaching screen. Native profession rules and active recipe capacity are enforced on the server. Automatic MineColonies recipes remain read-only with their existing level/research restrictions; this does not unlock them early. Other mods' crafting/furnace recipes are eligible where supported by the native hut. Domum, brewing and other special recipe systems retain native teaching controls.

World server config: `serverconfig/colony-horizons-recipes-server.toml`, `recipeCatalog.enabled` (default true). Disabling it restores the normal teaching entry.

The server resolves the current recipe, validates selected ingredients, assembles the actual result and obtains actual returned containers before saving a taught recipe. It never accepts client-provided outputs. Permissions, distance, duplicate and capacity checks apply to learning requests. Rejected/timed-out requests clear stale previews.

## Barracks repair

Arcology parent Barracks construction handlers protect registered, completed child towers belonging to that parent. A private work-order blueprint preserves their blocks and block-entity data, retaining its original anchor. Shared blueprints remain unchanged. Unbuilt tower anchors and the main shell can still be constructed; independent tower upgrades still work. The protection refreshes when linked child levels/bounds change during the parent's upgrade.

This prevents further upgrade damage; it does not reconstruct a tower already dismantled in an existing save.

## Verified server results

Reports are in `build/reports/foundation/<report>/seed/`.

| Suite | Result | Report |
| --- | --- | --- |
| RecipeCatalog | 12 checks passed | 76d9e8076e1e486fb83286a3f7f7121f |
| BarracksUpgrade | 32 level/rotation/mirror combinations, plus concurrent completion and unbuilt/independent tower controls | 7957e6095c814968a6d53191f0f35543 |
| BarracksNativeUpgrade | Full native L1 to L2 builder upgrade passed | c544feaa10fa4ce486364f6c7a11a2f6 |
| RecipeCatalogCrafting | 4 native worker/courier checks passed | ed29e2d691834d139d044c026b0c9790 |
| RecipeCatalogSmelting | 2 native worker checks passed | d638921d44904c769eae85eddbc01877 |
| ExplorationMenus | 10 regression checks passed | ce9dd006d8e84670a928c952223bf78e |

Catalog checks cover empty-inventory teaching, invalid/duplicate/forged selections, capacity, variants, canonical output and bucket returns, profession filtering, automatic-recipe protection, module save round trip, config, permissions/distance, bounded packet round trip and throttling. Crafting checks exercise sawmill, stonemason and fletcher production and normal courier delivery. Smelting checks exercise normal inputs/fuel.

During the full Barracks upgrade all 282 expected main structural blocks matched, completed tower blocks stayed unchanged throughout, the guard's home/work assignments survived, and seven stored emeralds remained. The real builder completed in 2,900 test ticks with recovery teleports disabled. Support tools/materials/research were supplied as test fixtures.

Independent review identified three risks: canonical assembly/remainders, stale preview, and concurrent tower completion cache invalidation. All were corrected and passed the reviewer's targeted source recheck.

Scope: full builder endurance covered L1 to L2. Other parent levels/rotations use native placement tests, not full repeated worker playthroughs. Recipe serialization is an in-process round trip, not a separate server restart. This is not exhaustive compatibility testing of third-party recipe implementations. Packaged menu results and artifact hash are recorded below when completed.

## Reproduce

Use Java 21 and the configured Gradle cache, from this development checkout:

```powershell
./gradlew.bat supplyDevJar supplyQaJar -Pmod_version=0.1.0-dev.catalog.1 --no-daemon --console=plain
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite RecipeCatalog -ArcologyPath build/surveyor-pack -TimeoutSeconds 900
```

Repeat the second command with `BarracksUpgrade`, `BarracksNativeUpgrade`, `RecipeCatalogCrafting`, `RecipeCatalogSmelting`, and `ExplorationMenus`. All create disposable worlds. Package checks: `tools/Test-SupplyDevPackage.py` (also pass `--version 0.1.0-dev.catalog.1`), `tools/Test-SurveyorArtifact.py`, and `tools/Test-RecipeCatalogArtifact.py`, each taking the development jar path.

## Packaged client and artifact

Packaged-client report: `build/packaged-client/6cefc042445f4cf2a39805b0e37f37f0`. All eight ready/full/automatic/already-learned menu states passed at GUI scales 2 and 3. Rejected-selection handling was exercised; the Learn button became disabled. Screenshots at both scales were visually reviewed. The client loaded a copy of the disposable native Barracks test world, then saved and closed itself. This checks rendering and control state with fixture data, not an end-to-end multiplayer click/packet interaction.

All package-boundary, Exploration survival-asset, recipe/Barracks hook and menu-asset checks passed. QA classes and blueprint assets are excluded from the installable addon jar.

Artifact: `build/supply-dev-libs/colony-horizons-0.1.0-dev.catalog.1.jar`

SHA-256: `D9F48C439F857BE02B5A1100763D17366C205928CF9AC0649B2C1ABC747D1495`

The focused server suites report 30 passing cases in total, with the 32 transform combinations nested in the Barracks preservation case. Build completed successfully with dependency/API deprecation warnings. No install or publication was performed.

## GitHub source and local installation

The tested snapshot was installed into the playtest pack after a verified full backup. The source update retains Akamarus attribution and leaves published alpha releases unchanged. Machine-specific backup locations are retained locally, not published. The compact [server test reports](evidence/catalog/) accompany this source snapshot.
