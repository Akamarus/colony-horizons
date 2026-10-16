# Outpost expansion and copied-colony founding

Development verification, 2026-10-15. Installed alpha.2 and submitted/public release artifacts are not output targets.

## Player flow and limits

The Mayor page view button cycles Needs, Supply Allowance, Upgrades, and New Buildings. Review a home or forester hut to see its location and material list; explicitly approve or reject. Rejection frees the plot for another review. An old review cannot approve its replacement.

Three supported, clear positions inside the original 49-chunk approved footprint are available. Expansion does not enlarge claims, flatten terrain, recruit a workforce, or create supplies. Native research, building prerequisites and the existing allowance apply. The native builder constructs the paid hut; the outpost warehouse and local courier handle supplies. Existing approved foresters may be reassigned through normal hut menus; unapproved Home citizens cannot be hired into remote jobs without a separate travel/membership workflow.

Original founding plans remain immutable. Extra huts have separate persisted review/payment records and join the approved building set only after approval. Additional foresters inherit the approved land restriction. Missing ground, obstructed space, changed claims, changed blueprints or stale decisions prevent placement. Interrupted payment remains quarantined for recovery rather than extracting again.

## Review corrections

- Expansion approval and entry into the building phase are validated together. Refused activation leaves both records unchanged and cannot later place a hut.
- Rejected plots can be reviewed again using monotonically increasing review generations. Only three plot records persist; stale decisions stay stale.
- Native hiring checks the destination hut's approved residents as well as the source citizen's restrictions. Ordinary Home jobs retain native behavior.
- Copied-colony reload compares independent pre-founding citizen identities and original Home hut levels. Native births are allowed; disappearance or replacement of an original citizen is not.

## Evidence recorded so far

- Interrupted native upgrade: `ecf4959fddea428dbe861a9ebd2fad69`, four processes: save partially built, reload/pause, paused reload/resume, completed reload. Same order and citizen identities, finite supplies, native completion.
- Home expansion with actual menu approval/replay: `38ca998e0a6347b09c60b18ea0401b42`.
- Forester restriction: failing `88fdc33f84714dc1954d6d90666091c5`, passing `f784139d77ae49a9980ece8aaa67d16b`.
- Review failures reproduced in `79f40ed0d9754cffb4428228691f59f9` and `d47d894df06d4214aea7b076121dbea8`; all guards plus native forester completion passed in `51ebfc9e984c4225b941224d475f648f`.
- Final home expansion `0bf4f5d28e444e06be55dd578a8a61b9`, locality16 `fa99058ad839409197556773e4ddea12`, and menu protocol10 `18434a9c2a8e4c3687bb968751630096` passed.
- Exact packaged forester expansion passed on a normal NeoForge server: `7c14dffd39d84c78bb84468f6a032d9e`.
- Live integrated-client copied-colony founding and separate-process reload passed: `b8b403525f0541df921e8eeace76aadd`. Ten native outpost huts completed; all nine original citizen identities and 18 original Home hut levels persisted. Total population grew naturally to 13 and total buildings to 28. Five original adults founded the outpost; no addon-generated workforce. Both client processes exited cleanly. All 954 files in the immediate installed-pack snapshot remained unchanged; all 942 files in the earlier alpha.2 baseline also retain their hashes.
- Final interrupted-upgrade repeat passed all four separate processes: `2326b48da6894e9082b08c23d59cb29b`.
- Packaged ten-day-equivalent endurance passed: `feb6be29433e4a27ac7e2d55e6e1b8a9`, using the behavior-tested hash below. Both forestry areas replanted six observed positions, 64 logs reached Home, five founders recovered after repeated illness with finite supplies. Daylight is fixed and ticks accelerated; this is not indefinite/raid coverage.
- Final package seven-button navigation passed: `f8cec2fc6b144d6a93c5df81e43d74f1`. All 28 menu states rendered at GUI scales 2 and 3: `0f3a990b75fc4f22a9a558db06bd3a03`. Expansion list/review screenshots were visually checked; corrected label is readable and unclipped.

The requested expansion, interrupted-upgrade and copied-colony gates are complete. The development build is suitable for controlled playtesting with a full backup; no alpha.3 release artifact has been published or installed.

Behavior-tested development JAR SHA-256: `f7db66648a11b7151fa51dc9548b5969c194293668d1039fd7908f23d1085e6a`.

The copied-colony fixture uses a disposable copy with the original owner profile and five original adults released from their jobs. It uses Peaceful difficulty, prepares supported flat ground and a finite preapproval preload in separate QA warehouse racks. It does not prove arbitrary terrain founding, a resource-short survival start, or raids. An earlier fixture run caught a preexisting zombified piglin killing the original child at Home; Peaceful removes that unrelated threat only in the disposable copy. No resources, levels, work orders or planner ticks are injected after approval. It uses offline local authentication, not a claim of Microsoft login verification.

## Reproduce

From this checkout, with Java 21 and the prepared test pack:

```powershell
./tools/Test-OutpostFoundation.ps1 -Suite MayorUpgradeInFlight -ArcologyPath <test-pack-path> -TimeoutSeconds 900
./tools/Test-OutpostFoundation.ps1 -Suite MayorExpansionForester -ArcologyPath <test-pack-path> -TimeoutSeconds 900
./gradlew.bat supplyDevJar supplyQaJar -Pmod_version=0.1.0-dev.remote.1 --no-daemon
python tools/Test-SupplyPackagedServer.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.remote.1.jar --version 0.1.0-dev.remote.1 --suite MayorExpansionForester
python tools/Test-CopiedColonyFounding.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.remote.1.jar
python tools/Test-SupplyPackagedClient.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.remote.1.jar --navigation
python tools/Test-SupplyPackagedClient.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.remote.1.jar
```

The copy runner currently targets the retained local isolated colony copy and prepared launcher dependencies. The original game must be closed before capturing a new source copy. QA remains in a separate jar excluded from production packages. Plain Gradle `test` is NO-SOURCE and is not acceptance evidence.

## Final label correction

Visual inspection found the shared new-building review still said “Approve Upgrade.” Its label is now simply “Approve.” Final development JAR SHA-256: `8768ab91683c431639371d17b6900ece3d5940b8e44850b73e7f4beafcaefccf`.

Entry-by-entry archive comparison (`build/expansion-label-only-delta.json`) confirms the sole changed entry is `assets/colonyhorizons/gui/supply/upgrade.xml`; every production class, configuration and other asset is byte-identical to the behavior-tested package. Final client tests rerun the new label package. The same final jar passed all 12 server-side remote-menu checks in `bb5d8ba8907846eaae9af67b228efbc7`, and the server exited cleanly. Server behavioral evidence above identifies the earlier exact hash rather than implying it tested a different binary.

## Controlled playtest installation and rollback

No installation has been performed. Before installing a candidate, close Minecraft and back up the complete playtest instance, including the world, configuration, blueprints and existing alpha.2 jar. Preserve the backup outside the active instance. Archive alpha.2 outside the mods directory, then install exactly one Colony Horizons jar alongside the existing matching MineColonies dependencies. Do not run both versions together.

Start with a copy of the colony. Review the remote site and supplies through the Town Hall page, use five available original adults, and keep the owner online while the outpost operates. Founding is builder first (native prerequisite), then warehouse and local courier, housing and foresters. Inter-settlement cargo uses warehouses; local hut jobs and menus remain MineColonies-native.

For rollback, close Minecraft and restore the entire pre-upgrade instance/world backup. Swapping only the jar does not undo newly placed huts, moved citizens, claims or recorded work.

Still outside this slice: arbitrary/new land expansion, additional-worker recruitment and protected migration after founding, budget editing, replacement of dead founders/Mayors, and general recovery controls for ambiguous custody. Three new supported plots and two building choices are deliberately bounded. These checks do not certify indefinite operation, raids, all terrains, all modpacks, or a stable release.

Final integrity check: all 942 installed alpha.2 baseline files remain hash-identical. A recoverable 314-file source/tools checkpoint is saved locally. No installation, release upload, commit or push was performed.
