# Protected supply ledger implementation plan

> Execute inline with superpowers:executing-plans. Continue the approved resource-outpost design; preserve the current addon branch and unrelated research changes.

**Goal:** implement a production transport core for resource exports and return supplies without exposing cargo to hostile entities or cross-file duplication.
**Spec:** ../specs/2026-10-10-resource-outposts-design.md, sections 7–9; ../specs/2026-10-10-multibuilding-arcology-outposts.md, Routes.
**Architecture:** one dimension-local saved ledger owns transport depot buffers, route policies, cargo and progress. All internal dispatch/arrival/return mutations are saved together. Native hut/player imports are a separate transactional boundary and remain disabled: the existing QA adapter cannot make independent world files atomic. No route corridor loading, citizen movement, native request completion or asset changes.
**Constraints:** pinned Java21/NeoForge/MineColonies; preserve components, real stock quantities, source reserves and incoming-stock accounting. No wall-clock/offline progress. Trusted server API only; no player packet/command bypasses native permissions.

## Task 1 — protected supply core
- [x] Add Supply acceptance registration/manifest and failing tests before implementation.
- [x] Implement supply/SupplyNetwork.java with registered owned depots, component-aware bounded inventories, same-owner/same-colony routes, target/reserve/batch/travel policy, one cargo per route, pause/return and availability-gated delivery.
- [x] Protect shared-source maximum reserves and shared-destination incoming stock; preserve partial cargo and defensive snapshots. Bound depot/route counts and per-tick input.
- [x] Implement strict schema1 NBT and supply/SupplySavedData.java with existing-file fail-closed factory. Buffer contents and cargo are one persistence unit; loading never re-extracts.
- [x] Verify two-way goods, component/tool preservation, full/unavailable endpoints, reserves, shared targets, pause/return, ownership, invalid NBT and conservation across snapshots.

## Task 2 — real process restart and handoff
- [x] Add SupplyRestart seed/reload/paused-reload/final-reload acceptance using actual SavedData and exact manifests. Seed once, never recreate cargo after restart.
- [x] Run Supply, SupplyRestart, clean build/resource audit and existing Records regression. Preserve evidence before clean.
- [x] Request one fresh-context review; fix material findings, document the core-versus-native-adapter boundary, commit task files only.

Review focus: stale/corrupt snapshots must not reset to empty; two routes cannot spend the same reserve or overfill a stock target; full source on cancellation must retain cargo; endpoint authority changes/unavailability must prevent transfer; externally returned ItemStacks cannot mutate custody. These each receive acceptance assertions.

Commands: tools/Test-OutpostFoundation.ps1 -Suite Supply; -Suite SupplyRestart; -Suite Records; gradlew.bat clean build runGameTestServer --no-daemon --console=plain.

Ruling: user explicitly authorized this next delivery increment under the approved design and requested inline development earlier. Proceed without another feature permission loop. Single-ledger custody is the first safe retained component; arbitrary native-hut insertion and autonomous couriers are not enabled until their independent-save boundary is addressed.

Execution note: resource audit and clean build were run as separate successful commands so live report output was preserved before clean. Final results are recorded in docs/outposts/PROTECTED-SUPPLY-LEDGER.md. Keep the existing branch; no push/merge requested. Native handoff remains a follow-up increment.
