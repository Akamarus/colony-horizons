# Roadmap toward stable 1.0

Updated 2026-10-16. No promised release dates.

## Published: alpha.2

Arcology compatibility, lantern-upgrade protection, Mine fluid sealing, supply-raft
placement metadata and Builder recall fixes. Published files and tag are unchanged.

## Implemented in development: Exploration Hut

Real biome/resource discovery, 36 simulated gathering outputs, protected Explorer
travel, native local courier pickup, minimum-stock integration, opt-in repeat trips,
simple native hut menus and five hut blueprints. Persistent payment/delivery custody,
restart recovery, full-inventory handling and legacy discovery compatibility are covered
by automated checks. Packaged client controls and copied-world migration were tested.
See [verification and limitations](docs/outposts/EXPLORATION-RESULTS.md).

## Next: playtest and stabilize the gathering loop

Collect real-colony reports on discovery reach, bread/tool demand, courier pickup,
repeat behavior and recovery. Fix progression blockers and duplication/loss risks.
Expand dedicated-server and two-player coverage, long-session performance tests and
compatibility testing. Do not call controlled fixtures universal modpack certification.

## Next public release: beta.1

Freeze a candidate, repeat artifact and migration checks, validate a normal launcher,
publish matching source/checksums/blueprints and backup/rollback instructions. The
current development snapshot does not replace the published alpha.2 artifacts.

## Beta and stable gate

Validate supported resource coverage end to end, document exceptions, improve clear
needs/status messages, and resolve critical/high-severity issues. Test dedicated-server
permissions, multiple colonies, interrupted sessions and supported dependency combinations.
Stable 1.0 requires reliable documented behavior, not every long-term feature idea.

## Paused and future work

Multi-building outposts, autonomous founding, Mayor development and inter-settlement
supply routes are paused in favor of the simpler Exploration Hut. Their existing code
and save recovery remain; they are not prerequisites for the supported beta.
Profession progression, building specializations, evolution and magic integrations can
be reconsidered after the core loop is stable. Ars work remains deferred.

## Development update: 2026-10-16

- Implemented and tested a searchable crafting-hut recipe catalog with ingredient-free teaching, native capacity/profession restrictions and protected automatic recipes.
- Implemented and tested Arcology Barracks upgrade preservation for completed child towers.
- Development snapshot: `0.1.0-beta.1`; see [test results and limitations](docs/outposts/CATALOG-AND-BARRACKS-RESULTS.md).
- Completed: copied flooded-mine recovery, recipe restart/duplicate checks, Barracks upgrades, packaged game checks and alpha.2 save compatibility. Next: publish the versioned beta after package verification, then gather broader colony reports. The multi-building outpost system remains paused.

## Beta scope freeze

Beta.1 supports Exploration, recipe teaching and the documented Arcology repairs.
No new outpost or Mayor features are required for stable. Stable readiness depends on
reliability of these supported features, dedicated-server/multiple-player coverage and
resolution of severe reported issues.
