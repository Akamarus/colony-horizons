# Recipe catalog and Barracks preservation implementation plan

Execute inline with test-first checks and one independent final review. User approved the catalog design and asked to build and test both updates. Keep installed pack and published releases unchanged.

## Accepted behavior

Crafting huts get a searchable native-style catalog with input/output previews. Learning requires no items in the player's inventory, but consumes normal recipe capacity and obeys native profession, level and research rules. Preserve existing recipes, manual teaching and actual production costs. A server config disables the catalog. Display custom automatic recipes and their restrictions without permitting manual unlock bypass. Fail closed for unsupported dynamic recipes; no fabricated ingredient variants. Barracks upgrades must not dismantle registered, completed child towers, but must still construct their own shell and newly unlocked tower anchors.

## Tasks

- [x] Reproduce Barracks overlap with native placement/upgrade stages; protect only registered completed child towers belonging to the upgraded parent, including rotation. Test main shell/new anchors still change and child inventories/assignments survive.
- [x] Add recipe catalog native tests for listing, inputless teaching, duplicate rejection, capacity, wrong profession, permission/distance, config, custom unlock restrictions, and save/reload. Watch RED before service implementation.
- [x] Implement server-authoritative catalog service and bounded request/reply transport, server config, native crafting-window entry button, searchable paginated catalog and ingredient preview. Revalidate selection at learn time.
- [x] Run focused native suites and existing crafting/Exploration regressions; build isolated development and QA artifacts. Validate menu rendering and packaged artifacts. Record exact scope and limitations.
- [x] Obtain one independent final review, fix findings and rerun affected checks. Produce a distinct development build; do not install or publish.

## Files

New `recipes/RecipeCatalog*` services/config, `network/RecipeCatalog*` transport,
`client/recipes/*` windows and XML, focused crafting-view/parent-upgrade mixins,
and `qa/outpost/RecipeCatalogTests` / `BarracksUpgradeTests`. Extend the existing
foundation test registration/runner and development mixin manifest. Reuse native
RecipeAnalyzer, module.addRecipe and existing blueprint/native fixture helpers.

## Validation

Java 21; `gradlew.bat supplyDevJar supplyQaJar -Pmod_version=0.1.0-dev.catalog.1`.
Run new suites via `tools/Test-OutpostFoundation.ps1` with the isolated Arcology
pack. Existing `Test-SupplyDevPackage.py`, `Test-SurveyorArtifact.py` and menu
asset validation must remain green. All world testing uses disposable copies.

## Execution record

Completed inline. See `docs/outposts/CATALOG-AND-BARRACKS-RESULTS.md` for exact report IDs, reproducible commands, artifact hash and test boundaries. Original catalog and Barracks regressions failed before implementation. Independent review findings were fixed and rechecked; the full native Barracks upgrade and final focused suites passed. Installed pack and published releases were not modified.
