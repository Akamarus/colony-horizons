# Four free outpost founders — development verification

Implementation date: 2026-10-15. Local development only; not installed or published.

## Player behavior

Every genuinely new, nonoverlapping outpost gets four native adult citizens once: Mayor, Builder, Courier and Forester. The player no longer needs idle Home workers. The founding plan has eight buildings: builder hut, warehouse, courier hut, forester hut and four homes. Construction preserves the native builder prerequisite, then establishes the warehouse and courier before subsequent work. Native jobs, inventories, requests and hut menus remain in use.

Four population slots must be available under the parent colony's research/server ceiling. Completed housing is not required in advance. Materials, starter food/tools, medicines and ongoing supplies are finite and still paid. The menu shows the four planned settlers before arrival, their actual arrival count afterward, and capacity blockers.

Legacy selected-party outposts keep their previous identities, layout and roles. Existing outposts do not receive a retrospective grant. Death, cancellation, reload, renaming or overlap with a previously granted site cannot refresh the entitlement. Paid recruitment and automatic replacements are not implemented.

## Persistence and review

The grant records per-role creation intent and native ID/UUID receipts. Native IDs alone are never sufficient identity evidence. Completed receipts resume with the same people; an ambiguous registration stops for recovery rather than attempting another creation. Cancellation safely returns known survivors and retains consumed entitlement and site/cargo custody.

An independent read-only final review found three Important issues, all reproduced by failing tests and corrected:

1. An ambiguous grant could quarantine unrelated immigrants. Only known receipted identities are protected now.
2. Cancelling after placing huts but before creating citizens could strand the reservation. Unconsumed founding now cancels while retaining placed buildings and cargo custody.
3. Reused native IDs with different UUIDs could incorrectly conflict across outposts. Resolved identity comparisons now use UUIDs, while actual shared identities remain rejected.

Recovery limitation: if a crash occurs after native registration but before its identity receipt, that unidentified citizen may appear at Home. The grant stays consumed and recovery-required. The system does not guess, delete, adopt, replace or retry that person. This avoids blocking unrelated immigration and other outposts.

Subsequent validation exposed two additional defects. A completed arrival could respawn at Home before its destination chunk was entity-ready after restart; it now waits for the destination. A hungry courier with a bread-delivery task could reject all bread and stall. It can now eat one separate, existing local warehouse portion, without changing its delivery task or carried cargo. Native food quality rules still apply.

## Evidence

Reports are in `build/reports/foundation/<run>/<phase>/` unless otherwise stated. These are observed runs, not a count of all hypothetical scenarios in the design.

| Checks | Result | Run |
| --- | --- | --- |
| Free records, including reused IDs and actual shared identity rejection | 8 passed | `0fe31d8d723d422394323ee02dd64e93` |
| Free layout and role mapping | 7 passed | `c68e9b0d55f0430881b7a9ec7c2a256d` |
| Capacity, malformed saves and unrelated immigration | 12 passed | `a719da208ca6411986cfa47846cba8c6` |
| Cancellation before any grant, including placed anchors | 4 passed | `b13e2fbf4552452aa226ac7cf9e94a5c` |
| Grant/arrival cancellation scenarios | 17 passed | `8ff92418823943a083a7985a96ae8d6b` |
| Free menu protocol/copy | 8 passed | `62df5adc0ef94175a1f992b58d859085` |
| Existing remote menu / general protocol | 12 / 10 passed | `e8ef024d77a64ce6b44396cd26904a56`, `6faa399e530942a99d8757591c3b5601` |
| Legacy records | 11 passed | `2125ddb9fbc74f8da2667f8d6385fe91` |
| Legacy arrival restart | Three phases passed | `0a82497d8ff0403686b6d58aaba64545` |
| Legacy cancellation restart | Three phases passed | `d19f1a0596ee47c6b878f1b4169b4e2c` |
| Warehouse restart | Three phases passed | `345814fc8f6e4921bc96f8a785e01fec` |
| Interrupted native upgrade | Four phases passed | `ca0eaa3790fb4472b8038aaa2ed009e4` |

Free founder restart tests each run seed and reload in separate processes:

| Durable boundary | Passing run |
| --- | --- |
| Intent before creation | `90759aa0c3034902a1432911bd18d7bd` |
| Native registration without receipt | `59b03eeac1904400844d934a7ec4b543` |
| Receipt missing its original native citizen | `6f19b64012b84bf69fe8d921f8ae43e6` |
| One receipt | `2f7a77fe15aa4389911db0a29bfd3018` |
| Two receipts | `f7e6fe35821e42e89bca32870443b8f8` |
| Three receipts | `db1b66d286774b3d9501bffb906ebebe` |
| Four receipts | `87ca2e2a304444fa8a766c9f7f2caf72` |
| Interrupted survivor return | `34921a5ecec849dba46c555e05955ebc` |

## Final packaged artifact

`build/supply-dev-libs/colony-horizons-0.1.0-dev.founders.1.jar`

SHA256: `FCD323B1C7E04146BF237684E78D1672B95578D807C95CE1F4550519BCAA3F92`.

The separate QA jar is never part of the production artifact. Package inspection passed: expected production hooks and notices are present, development version is distinct, and QA classes/assets/references are excluded.

Packaged-server results (`build/reports/courier-districts/packaged-server/`):

- Grant: 6 passed, `9c74e142ebec4675a85b21ce66ad0c98`.
- Founding flow: 13 passed, `78bda41d57714d32a6f5a0eabadb07e5`.
- Courier food recovery: 1 passed, `e356388706834cc9bc8d7760bf3d448e`. This regression resumes the retained failed endurance world and verifies unchanged task/cargo plus restored hunger. It depends on that retained fixture, unlike the fresh endurance test.
- Final endurance: passed, `385462ee5d1a4c2c805d315860397d05`, 240,000 simulated ticks, all eight native buildings completed, six distinct tree sites replanted and 64 logs returned Home. Repeated illness recovered using imported medicine; all four settlers alive, fed and housed at completion.

Fresh copied-colony founding and separate-process reload passed in `build/copied-colony-client/3f892fc42cfe45d69949a0e28b811c3e`. The exact artifact hash above is recorded in its result JSON. Nine original citizens remain, with four new founders (13 total), eight completed outpost buildings, original adult jobs/housing retained, and all 14 installed mod files unchanged. No Home adult was selected for founding. This is a copy of the verified backup, not the current live colony.

Final packaged client navigation: seven automated interaction checks passed in `build/packaged-client/919129ee32584576ae4cd6bbb87c52f3`. Menu asset checks passed. Final render run `c167b781832b40e28fff8c8443bf2039` completed all 28 states across GUI scales 2 and 3; the review and Mayor screenshots were visually inspected for readable four-settler/eight-building copy.

## Test conditions and limits

The endurance scenario runs 240,000 simulated ticks, tests repeat illness after exhausting local medicine, requires imported cures and at least three distinct harvest/replant operations, and requires at least eight produced oak logs delivered Home. It verifies all four founders remain alive, fed, housed and within the settlement. This is not three full regrowth cycles of the same tree.

Copied-colony testing uses a stable backup in a disposable directory. Original adults are assigned native Home jobs and paused before taking the baseline. Existing citizens' identities, adult jobs, housing and Home building levels are checked. A pre-existing child may naturally grow up and acquire a job. The fixture uses prepared supported terrain, peaceful difficulty, accelerated time and finite supplies preloaded before approval. It does not establish untouched-world survival balance. Production delivery is checked in the endurance world, separately from copied-colony construction/reload.

No installed world is opened for these tests. Only the installed addon hash and mod files are read for comparison. The installed addon hash before and after is identical: `8768AB91683C431639371D17B6900ECE3D5940B8E44850B73E7F4BEAFCAEFCCF`.

## Workspace preservation

The branch already contained substantial uncommitted and untracked development before this feature. Its original files were snapshotted before editing. The feature delta, before/after hashes and both file versions are preserved under `.superpowers/sdd/2026-10-15-free-outpost-founders/feature-checkpoint.zip`; `review.diff` compares against that baseline rather than HEAD. No blanket staging, merge, push or installation was performed.

The feature delta adds no trailing whitespace. Whole-worktree `git diff --check` reports a pre-existing blank line at EOF in `ArcologyUpgradeTests.java`; the same bytes exist in the baseline and that file was not modified for this feature. The approved plan's per-task commits were replaced with a recoverable local checkpoint to avoid presenting earlier untracked development as new feature work.

## Reproduction

Use Java 21 and the existing Gradle dependency cache. Run commands from this development checkout. PowerShell scripts require the child-process `-ExecutionPolicy Bypass` option on this machine; no machine policy change is needed.

```powershell
./gradlew.bat supplyDevJar supplyQaJar '-Pmod_version=0.1.0-dev.founders.1' --no-daemon --console=plain
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite FreeFounderPolicy -ArcologyPath '<Arcology Horizons pack folder>' -TimeoutSeconds 900
python tools/Test-SupplyPackagedServer.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.founders.1.jar --version 0.1.0-dev.founders.1 --suite FreeFounderEndurance
python tools/Test-CopiedColonyFounding.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.founders.1.jar --source-world '<verified backup world>' --founders free-four
python tools/Test-SupplyPackagedClient.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.founders.1.jar --version 0.1.0-dev.founders.1 --navigation
```

Build/server/client runners require local Java networking and cache access. Runners fail on missing reports, failed cases or absent completion markers. The copied-world runner rejects the live installed world as its source.
