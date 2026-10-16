# Outpost upgrade approvals — 2026-10-15

Development only. Installed alpha.2 and the published release are unchanged. This is not an alpha.3 release-readiness declaration.

This is the earlier upgrade checkpoint. See [expansion and live founding results](EXPANSION-AND-LIVE-FOUNDING-RESULTS.md) for subsequent interrupted-upgrade coverage, additional huts and copied-colony checks.

## Player flow

Open the outpost's Mayor page. The view button cycles Needs, Supply Allowance, and Upgrades. Select an original hut to review its next level. The review identifies the hut, location, target level, and actual materials; Approve Upgrade and Reject save the decision. Closing the page does not approve anything.

In approval mode, the Mayor prepares a proposal but waits for your decision. Automatic mode can approve within the original footprint, level cap and finite allowance. Native research and parent/builder levels still apply. The native builder performs reconstruction; production never sets the building level itself.

Only the original reviewed huts can upgrade. This does not add huts or enlarge the claim. Changed structures, missing supporting ground, liquids, changed pack files, and unmet native prerequisites stop the upgrade. Research refusal is a normal blocker rather than a permanent recovery state.

Approvals survive saving, owner-only decisions carry revisions, repeated menu requests are inert, and malformed approval records fail closed. Rejection persists for that hut/target. There is currently no button to reconsider a rejected upgrade.

## Resource policy

The full reviewed bill is checked against available Home stock, reserves, prior spending and shared reservations before queueing. Actual material shipments use the existing finite reservations. This avoids charging for both a proposal and its deliveries. It is not a separate up-front escrow: another approved settlement can consume available stock before all deliveries, causing the upgrade to wait.

## Verified so far

- Nine proposal persistence/authorization/corruption checks: `8667eef90d504b23a1426a2babb19764`.
- Ten protocol checks: `04519a0269e04d779e2d1e2dbe02856a`.
- Native builder upgrade plus saved completed-upgrade reload and paused reload: `a8f0cce63190428e86af84ca8c9fa323`, three separate server processes. The original five citizens construct ten huts from finite stock, then the builder performs level 2 after real menu approval. The test rejects a forged proposal, repeated decision, player-added gold block, missing support, and native research refusal without losing the operating settlement.
- Exact packaged native upgrade: `e6607b5a91b14b2181275af855f242af`.
- Disposable player-colony offline compatibility: `2da0df1338ee48a68d4ced6bae33b3d1`. Normal NeoForge loaded all hut chunks, saved and restarted: 18 buildings, 9 citizen records, job/home assignments and inventories preserved; 170 block-inventory records unchanged. Original save hashes remained unchanged after both phases. The copy includes Amendments and Moonlight.

Both reviewed production defects were reproduced by failing native tests before fixing them. The restart fixture also needed its save/disconnect moved after completion. The courier observer now samples each tick because a twenty-tick interval could miss a short physical carrying period.

## Remaining release gates

- Separate in-progress upgrade interruption/restart coverage; the completed and paused completed-upgrade checks do not establish that case.
- Owner-login and new outpost founding in the copied player colony. The offline compatibility test does not cover these.
- Additional buildings/new claims, editing established budgets, replacing lost founders/Mayor, and broader recovery controls.
- Review minor deferred: stricter item-count decoding in persisted proposal cost NBT. Current item identity normalizes counts; explicit cost counts are independently validated.

## Reproduce

From the development checkout with Java 21 and the prepared Arcology test pack:

```powershell
./tools/Test-OutpostFoundation.ps1 -Suite MayorUpgradeRestart -ArcologyPath <test-pack-path> -TimeoutSeconds 900
./tools/Test-OutpostFoundation.ps1 -Suite MayorProposals -TimeoutSeconds 180
./tools/Test-OutpostFoundation.ps1 -Suite MenuProtocol -TimeoutSeconds 180
```

`tools/Test-ColonyCopyUpgrade.py --jar <development-jar> --instance <closed-playtest-instance> --world "New World"` creates a unique temporary server and copies the save. Run only with that game closed. It never installs into the source instance; reports identify the retained disposable copy.

## Final packaged verification

Exact development JAR SHA-256: `e5975f8b5d8d68fb67841c6005d1cb71a7a0080df8b6ed72f5e0fe9734218811`.

Packaged ten-day endurance passed in `dc9885aa389c4a5395fc92cac8a07048`: all five original residents healthy/fed after repeated disease and local medicine depletion, six replanted tree positions in each forestry area, 64 logs Home, finite resource conservation. This uses fixed daytime and accelerated ticks and does not certify raids or indefinite operation.

Seven real packaged menu button checks passed in `5d59b186a69a40bbbe5a7479164b371b`. Twenty-four menu states rendered at GUI scales 2 and 3 in `6ab99db624d84d83bde48c9ee0f503e2`; the upgrade review screenshots were visually inspected at both sizes. Both disposable clients exited cleanly. Offline QA authentication produces expected Realms warnings; no real account session was used.

Development build, production-package boundary checker, menu asset checker, scoped diff check and helper syntax checks passed. Plain Gradle `test` is NO-SOURCE, not acceptance evidence. No QA classes are in the development production JAR.

Final installed integrity: all 942 baseline files, including the actual world and alpha.2 JAR, are unchanged. No installation, commit, push, publication or alpha.3 release was performed. Remaining release gates above are still open.

