# Release candidate verification — 2026-10-16

Result: PASS for the tested catalog.2 candidate. Ready to prepare the next alpha, not a claim of exhaustive or stable-release validation. No publication or installation changes were made.

Candidate: `colony-horizons-0.1.0-dev.catalog.2.jar`

SHA-256: `2A52FD52048E5362C9A291B11216116E318B95EAFB2DE5A9C9C88553E6100BB3`

The installed playtest artifact still matches this hash. Production sources and artifact were unchanged. This run added QA code, a disposable-copy runner, and documentation.

## Regression results

All suites returned exit code 0: 53 checks total. Report IDs refer to `build/reports/foundation/<ID>/`.

| Suite | Checks | Report ID |
|---|---:|---|
| RecipeCatalog | 14 | df2b66620389465ba933d1a3635958a6 |
| RecipeCatalogCrafting | 4 | 90228bf6e772494dab0fa31853635cef |
| RecipeCatalogSmelting | 2 | bb0dd2c396054b37af83d620800a919c |
| BarracksUpgrade | 1, including 32 transforms | a8da3fc993c046c5b21c594e50e03d32 |
| BarracksNativeUpgrade | 1, native L1 to L2 | debd0c128f7248bfb3975f1900edd09f |
| ExplorationGathering | 8 | 3db08e66e3594919936e7347203d78be |
| ExplorationCourier | 3 | c9656cbd482d4382b202f008a80bd31c |
| ExplorationMenus | 10 | 12eca22275cb4c5f88c1823c9590e22c |
| ExplorationRestart | 1 seed + 1 reload | e5f653388b074a0582543e1371257529 |
| ArcologyMinerInflow | 8 | 20a47a245bfa4e0994406ae9220d306d |

Run through `tools/Test-OutpostFoundation.ps1 -Suite <suite>`. Crafting and smelting suites verify actual worker production.

## Copied flooded colony

`build/miner-copy/report-652f9b23/result.json`: PASS; source unchanged.

The original miner completed a shaft level (0 to 1) at tick 12100 and continued in native `MINER_MINING_SHAFT`. The fixture used a fresh copy of the saved colony with its original worker and terrain. It supplied one diamond shovel, one diamond pickaxe, and 256 dirt, kept local chunks active and daylight fixed, and accelerated server ticks. It did not teleport or heal the worker, inject output or AI progress, alter terrain, or replenish supplies continuously.

This verifies recovery and completion of the reported shaft section with finite supplies. It does not establish indefinite endurance or drainage of every underground water layout.

## Recipes across process restart

`build/recipe-copy/report-0c476b18/result.json`: PASS; source unchanged.

Separate seed and reload server processes preserved exact native learned-recipe tokens/count, including a disabled manually learned recipe and a catalog-taught recipe. Both remained recognized, and learning them again was rejected. Only the copied hut's recipe list was cleared for the fixture.

An initial QA run failed because it persisted a temporary catalog selection key. The corrected test persists recipe IDs with unambiguous ingredients and resolves fresh keys after reload. This was a test defect; no production change was needed. Initial evidence is retained in `build/recipe-copy/report-253501f4/`.

Reproduce with `tools/Test-ReleaseColonyCopy.py --kind miner` or `--kind recipes`, supplying `--instance` pointing to a stable backup and `--jar` pointing to the candidate. The runner copies the world and verifies source hashes remain unchanged.

## Exact packaged artifact

- Fresh server: eight Exploration gathering checks PASS. Report: `build/reports/courier-districts/packaged-server/e40431c8527e4252b59f30d85aa80ef5/seed/`.
- Public alpha.2 upgrade: load, save, and separate reload PASS, preserving 18 buildings, 9 citizens, and 170 block inventories. Report: `build/copy/07a0fa80/result.json`. Scope: offline server compatibility, without owner login or new founding.
- Packaged client: exit code 0, `CATALOG_PREVIEW_COMPLETE`, eight render states PASS at GUI scales 2 and 3. Evidence: `build/packaged-client/1c976ecb65054b80b2bbe8ebf598c2c3/`. Screenshots 0, 4, and 7 visually inspected: controls/text legible and known-recipe Learn disabled. These are fixture-driven render/state checks, not a full mouse-click/network teaching test.
- Package checks: `Test-SupplyDevPackage.py`, `Test-SurveyorArtifact.py`, and `Test-RecipeCatalogArtifact.py` PASS. QA code is excluded from the candidate.

## Independent review and next release

Independent read-only review found no additional concrete production regressions in recipe equivalence or miner sealing. Its actionable finding was the restart test's temporary-key assumption, corrected and rerun successfully.

Next: assign the public alpha version, update changelog and source, and verify the final versioned package before uploading. Existing duplicates are not automatically deleted. Outpost development remains paused. Original worlds, installed mods, and the current public release were unchanged by these tests.

## Beta promotion — 2026-10-16

The agreed public version is now `0.1.0-beta.1`, with supported scope frozen around
Exploration, recipe teaching and documented Arcology repairs. Outposts are not part
of the beta's supported feature set.

- `supplyDevJar` build: PASS.
- Beta SHA-256: `df5347ae58d320924fec76d11f27943aa489e40e8427d950728695f609ea7691`.
- Comparison with tested catalog.2: every JAR entry identical except the version string
  in `META-INF/neoforge.mods.toml`; all compiled classes and runtime assets identical.
- Package, Exploration assets and recipe catalog/QA exclusion checks: PASS.
- Five Exploration blueprints preserve Arcology module geometry: PASS.
- Exact beta artifact on isolated normal server: eight named Exploration gathering
  checks PASS, report `build/reports/courier-districts/packaged-server/00cfd76bd749495a96b78dbe72ced86c/seed/`
  in the development test workspace.
- The first beta server attempt was rejected by the separate QA harness's exact
  catalog.2 dependency. Rebuilding the harness for beta.1 resolved that test setup
  mismatch; no production change was required.
- The package checker now accepts explicit beta versions and verifies exact parsed
  mod ID/version, retaining all QA exclusion checks.

The installed playtest pack remains catalog.2. It was not upgraded during promotion.
