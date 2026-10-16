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

## Next public alpha

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
and save recovery remain; they are not prerequisites for the next Exploration Hut alpha.
Profession progression, building specializations, evolution and magic integrations can
be reconsidered after the core loop is stable. Ars work remains deferred.
