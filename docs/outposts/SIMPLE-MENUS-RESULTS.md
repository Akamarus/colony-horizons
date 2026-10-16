# Simple Outposts & Supplies menus — development verification

Implemented in the isolated `feat/safe-supply-routes` checkout, October 14, 2026. This is a development build, not an update to installed alpha.2. No publication, installation or remote push is included.

## Implemented

- Native Town Hall entry and `/chsupply menu` fallback; two BlockUI pages.
- Owner-authorized snapshots with paged hut, outpost and route lists.
- Review and activate a district from existing native huts, including enclosed huts and warehouse rack bounds. Revalidate claims, buildings and assignments before activation.
- Source/destination hut selection with server-resolved held item; destination target, source reserve and advanced shipment size.
- Route settings and cargo status, pause/resume, confirmed stop with cargo return.
- Bounded messages, actor-bound sessions, request ordering, duplicate-request replay, throttling, close cleanup and late-open correlation.

## Automated evidence

Reports are relative to `build/reports/foundation/` in this checkout. Each listed report contains named cases and outcomes.

| Suite | Passing cases/phases | Report ID |
|---|---:|---|
| MenuRead | 6 | a7ac274006014b56b1e0ba5b1d2c842c |
| MenuDistricts | 14 | 894618249a2f41eba9c014261cb7060d |
| MenuRoutes | 11 | ac2dde3e76e546a29afc039453a08883 |
| MenuProtocol | 10 | 4f2a739cd941445b99059a1aecc44026 |
| CourierMembership | 11 | 65c47974a9dd4061ba9a348220322eea |
| CourierNative | 22 | f759dcc88e8945f5a5e9826147ac758c |
| CourierTravel | 3 | 14a7a1521ff34b21b3e95843bc9b75f7 |
| CourierRestart | 5 restart phases | 2b783de5a3344d66977f2d9cfd11627c |
| SupplyNativeRestart | 3 restart phases | 9ba34903e35c4d0b8a2ab0a8c4d23291 |
| ForesterWithCouriers | 13 | 05692349b4684114a869c58eb197c957 |
| Supply | 12 | 0f651f2e00144259adbcffdd0d84d51b |
| Records | 15 | fd75a77815614e55a5205e98327672cd |

Review found and corrected failure-after-commit reporting, an overflowing draft list, pending-request Back behavior, late opening replies, and closing blocked by stale views. The close regression failed in `3263f654ea834883889234a67ac90948` before the fix and passed in the final protocol report. A final district rerun also exposed a chunk-edge assumption in the enclosed-hut fixture; the second hut is now explicitly placed inside the first hut's chunk.

## Scope of evidence and remaining checks

The server tests use real native colony/building objects and separate disposable worlds. The original rendering runs used synthetic display data. A subsequent [live integrated-server click-through](LIVE-MENU-TESTING.md) verified Town Hall entry, typing, paging, review/activation, route creation, pause/resume/stop, restart persistence and Back during a five-second delayed reply. It found and fixed route setup returning to the wrong tab. A two-case BlockUI regression failed against the original behavior and passes with the fix. Live testing also observed 64 logs delivered and a second route returning eight in-flight logs after Stop. This is not remote multiplayer latency/load testing.

The plan's detailed scenario checklist is broader than the current named tests. Explicit 33-hut rejection, partial endpoint-binding retry, incompatible endpoint reuse, every stale claim/assignment combination, and large component-bearing reply payloads are not each independently exercised by the new menu suite. Existing service validation remains in place; do not describe these as individually verified menu cases.

No automatic colony creation, claims, hut construction, mayor, district resizing or deletion is included. Supply routes stay within one native colony, and unloaded endpoints pause progress. Local courier confinement is work/navigation protection, not immunity to combat.

## Build, package and rendering

`supplyDevJar supplyQaJar -Pmod_version=0.1.0-dev.menus.1` passed. Artifact: `build/supply-dev-libs/colony-horizons-0.1.0-dev.menus.1.jar`.

SHA-256: `767BA326E4147F869059638D3A360E3A05E4C98379EA6E7E8DA5086CE5E5DA4E`.

Package assertions passed, including production menu classes/resources, client-only Town Hall hook, version separation and exclusion of QA code/assets. The separate QA harness initially required the old forester build; its dependency now follows the selected build version. The subsequent normal installed NeoForge server smoke passed all three autonomous courier cases and shut down cleanly: `build/reports/courier-districts/packaged-server/44e5e00c3bfc4a19b58a277de816d153`. This server lives under this checkout's `build/` directory, not the user's installed pack.

Two disposable-client runs completed eight render states each at 1280x720 and 1920x1080, requesting GUI scales 2 and 3. BlockUI fits its window to available space; scale 3 at 1280x720 remains effectively scale 2. Inspected title placement, two tabs, the scrolling reviewed-hut list and advanced route fields. The native Town Hall class was loaded to exercise mixin application. This does not verify clicking the actual Town Hall button.

- [Outposts at 1280x720](evidence/simple-menus/outposts-1280.png)
- [Route setup at 1920x1080, scale 3](evidence/simple-menus/route-1920-scale3.png)

Final read-only installed manifest comparison: 341 monitored mod/blueprint files, zero changed. Scoped tracked-file whitespace check passed. An unrelated inherited trailing blank line in `ArcologyUpgradeTests.java` was left alone. No commit bundled the checkout's inherited changes.

## Live-menu follow-up checkpoint

The [live test record](LIVE-MENU-TESTING.md) contains the completed click-through, screenshots, fixture limitations and exact rerun commands. Route creation and Back now return to Supply Routes. Both corrected paths were verified again with live mouse input after restarting the disposable colony. The client saved and shut down cleanly.

Fresh follow-up verification:

- BlockUI navigation regression: 2/2, with both original failures reproduced before the fix. `build/menu-navigation-red.log` and `build/menu-navigation.log`.
- MenuProtocol: 10/10, report `10d622eed81f40e59e4c5476f762e7ea`.
- MenuRoutes: 11/11, report `8945375392b44e178f3b348165c3b1e9`.
- `test supplyDevJar supplyQaJar` succeeded. Gradle's plain `test` task has no test sources; the counts above are the actual client and GameTest suites, not JVM unit tests. Existing Gradle deprecation warnings remain.
- Menu asset checks and development-package inspection passed, including QA-code exclusion.
- Fresh installed comparison: 341 files checked, zero changed. Scoped whitespace check passed.

Latest rebuilt development JAR: `build/supply-dev-libs/colony-horizons-0.1.0-dev.menus.1.jar`, SHA-256 `F17AAC9057A49118B93FA620CB2627ACE66AD13EA42AA70D9DF5D4A2BB1C8804`. The older hash and packaged-server result above describe the earlier checkpoint; the normal packaged-server run was not repeated for this client-only navigation correction. No installation, publication or push was performed.
