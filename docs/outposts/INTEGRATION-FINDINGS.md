# Outpost integration findings

Status: **GATE_BLOCKED**. The native remote-workplace prerequisite failed; this is not a playable outpost release.

## Native result

The test creates a real MineColonies colony owned by a unique QA fake player, registers and spawns one native citizen, and calls the actual build-tool placement path with a minimal logger blueprint. It places the candidate site 352 blocks from the center: configured maximum radius 20 chunks plus a two-chunk margin. The site is outside claimed colony land. The owner lookup still returns the original colony.

`AbstractBlockHut.setup(...)` returns false. Its `canPaste` path rejects ordinary huts when `IColonyManager.getIColony(world, pos)` returns null. Evidence: exact-release source `com/minecolonies/api/blocks/AbstractBlockHut.java:214-235,306-337` and [native test report](evidence/native.json).

We did not use `addNewBuilding` as an unchecked back door, change maximum claim radius, create a second owned colony, or fake a successful build. Remote builder construction, logger harvest/replant, and worker return are explicitly BLOCKED by the missing authorized workplace. They were not executed. This is a finding about the normal native placement path, not proof that every possible addon integration is impossible.

The fixture uses an authentic registered citizen, but it is not a connected-client multiplayer test. Native fake-player subscriber warnings are expected in this fixture. Its test structure covers a 32x4x32 area and starts assertions after 20 ticks so positions crossing a chunk edge are entity-ticking; an earlier 1x1 fixture exposed a nondeterministic spawn failure.

## Independent cargo prototype

The QA-only transfer engine moves actual handler inventory, retains partial remainders, preserves components and prevents replay of completed loading/delivery in the current custody state. The endpoint adapter validates registered UUID, owner and dimension against a live block entity; replacing a chest cannot inherit its registration. These are QA-owned endpoints, not a production MineColonies warehouse permission adapter.

Nine cases cover loading 20 of 64 logs once, inserting seven of twenty into a constrained destination, retries, named stacks and differently damaged axes, restricted slots, unauthorized/replaced endpoints, NBT roundtrip and quarantine of interrupted transfers. [Transfer report](evidence/transfers.json).

The transfer tests were observed failing before their implementations. Serialization and interrupted-transfer checks likewise failed before implementation. Reports are checked for exact expected case IDs, duplicates, missing files, malformed JSON, FAIL/BLOCKED outcomes and nonzero process exit. Nine PowerShell runner checks exercise those cases.


Independent review identified missing coverage at actual interrupted inventory mutations. Two additional tests now throw immediately after source extraction and after partial destination insertion. They check the mutated endpoint, intermediate state, persisted pending evidence, recovery quarantine and inert retries (including retry with freed destination capacity). Both pass. The extraction test deliberately exposes twenty unacknowledged items; the insertion test exposes a stale cargo record after seven items arrived. Quarantine prevents automatic replay; it does not reconcile either ambiguity.

Final verification: all nine transfer cases and nine report-runner checks passed. The build and original resource-audit GameTest also passed, exporting 1,671 items, 1,592 recipes and 2,630 candidate rows with zero candidate or inherent-recipe errors. The four saved-world restart phases passed as recorded below. Review corrections changed only tests and their expected-case manifest; production transfer/persistence behavior was unchanged.

## Persistence boundary

The restart probe uses four launches of the same isolated GameTest world and real chest block entities outside the reset test structure. It seeds 64 source logs and 57 destination logs, loads twenty, delivers seven, restarts with thirteen retained, consumes 64 destination logs, delivers only the remaining thirteen, and restarts to check no duplicate delivery. Initial total is 121; final source 44 + destination 13 + consumed 64 remains 121.

The deliberately incomplete SavedData implementation was observed failing on the next server launch with `Shipment record must survive actual server restart`. The corrected four-phase runner completed with exit 0. Each phase passed after loading the actual saved world. [Restart evidence](evidence/restart.json).

Versioned shipment data preserves cargo, pending operation evidence, identity, policy, delivered count and saved travel ticks. LOADING/UNLOADING records reload as RECOVERY_NEEDED and cannot automatically unload. This is conservative quarantine, not demonstrated exactly-once recovery across arbitrary operating-system crashes. Separate chest/world-data files can be saved at different times. No claim of atomic cross-file transactions, automatic resolution of uncertain losses, or robust hard-crash recovery is made.

No actual caravan/citizen travel representation, damage immunity, route pathfinding, native job reservation, live chunk-unload transition, corridor ticket budget or unsafe-arrival recovery has been implemented or validated. Saving a travel-tick field proves record persistence only; it does not prove a working travel simulator. Those dependent native lifecycle tests remain blocked and must not be advertised as passing.

## Scope decisions

- Continued in the existing clean `codex/minecolonies-addon` checkout, consistent with the user's earlier working-here preference; no separate worktree was created.
- Used Windows-native bookkeeping rather than POSIX skill scripts. The execution ledger records test evidence and decisions.
- Stopped the remote-native and protected-personnel path at its actual placement prerequisite instead of bypassing claims. Completed independent cargo and persistence probes.
- Reused an isolated GameTest world across restart processes rather than a production dedicated server. The test explicitly checks persisted world data rather than trusting a serialization-only roundtrip. No player save or server EULA was changed.

## Next integration to investigate

A candidate is a normal **home-colony expedition workplace** linked to **addon-owned remote camps**, with narrowly scoped remote work/construction adapters. That would preserve native ownership while avoiding the assumption that a remote camp must be registered as an ordinary native hut. It is a proposed architecture adjustment, not yet proven API compatibility.

Before any playable outpost UI or Mayor planner, validate citizen job/home lifecycle, permissions on remote land, physical harvesting/replanting, construction and safe return with that approach. Public addon job/building registries exist, but their existence does not prove these behaviors. Do not fork MineColonies or start a standalone rewrite based on this placement finding alone.

Mayor/scouts, blueprints, stock targets/resupply automation and transport gameplay remain unimplemented. This probe code is activated only by the explicit development `colonyhorizons.outpostProbe` property; normal gameplay does not register these tests.

## Reproduction

Use Java 21, Minecraft 1.21.1, NeoForge 21.1.250 and MineColonies 1.1.1403-1.21.1 as pinned in this project. From the addon root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-OutpostRunner.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-OutpostProbe.ps1 -Suite Native
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-OutpostProbe.ps1 -Suite Transfers
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Test-OutpostProbe.ps1 -Suite Restart
.\gradlew.bat build runGameTestServer --no-daemon --console=plain
```

Native intentionally returns nonzero while the gate is blocked. Do not weaken assertions or treat nonzero as a successful integration. Every probe invocation has unique reports under `build/reports/outpost-probe`; native/transfers use fresh worlds, restart phases share only their generated world. The runner times out after 240 seconds per process and stops only its launched process tree. Raw logs remain in the ignored build directory.

## Follow-up: alternative workplace feasibility

The [Expedition Lodge spike](EXPEDITION-SPIKE.md) subsequently proved a narrow alternative: a native citizen with an addon job assigned to a home workplace physically harvested a remote log and returned with identity intact. The original ordinary-hut placement gate remains blocked. Persistent Lodge modules, protected transport and production camp permissions remain unproven.
