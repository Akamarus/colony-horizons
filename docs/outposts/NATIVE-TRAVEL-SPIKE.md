# Native protected-travel feasibility

Status: **LIFECYCLE PASSES; UNGUARDED ARRIVAL IS UNSAFE**.

MineColonies' travel manager is a usable candidate for preserving a worker during protected journeys. It is not safe to use its arrival behavior unchanged. This was a throwaway experiment; no playable transport feature was added.

## Runtime findings

Seven lifecycle assertions passed using a real native colony, native logger job, native workplace assignment and actual citizen inventory:

1. Departure removed the live entity while retaining the citizen, job and inventory.
2. A new server process restored the native colony, assignment, inventory and active travel reservation from disk.
3. The native timer advanced by its documented 20-tick update.
4. A worker appeared at a clear remote endpoint with exactly one live representation; repeating the update did not duplicate it.
5. The same logical citizen departed on the return journey.
6. A restart during the return preserved the citizen and produced a safe home arrival.
7. Reloading the completed return retained one citizen and the exact original inventory; repeated updates were inert.

Inventory checks compared twenty named spruce logs and one named iron axe with seventeen damage, including exact stack components and counts. The native job, home workplace module, citizen name, citizen ID, colony ID and owner were also checked. Entity counts covered loaded entities with the fixture colony ID; both endpoints were loaded.

## Obstructed arrival: native failure and experimental guard

The test filled the destination with stone, completed native travel, and asked MineColonies to restore the citizen. The entity appeared at (352, 100, 0), inside collision geometry. The native travel record was already removed.

This matches CitizenData.updateEntityIfNecessary: it finishes the travel record before spawning, then sets the entity to the requested coordinates. The earlier native spawn search does not make that final position safe.

Two additional experimental checks passed:

- A preflight check rejected the obstructed destination and kept the citizen under an active native travel reservation, with no live entity and unchanged inventory.
- After clearing the obstruction, the same logical citizen arrived safely once. Retrying the release did not create another entity or duplicate inventory.

The preflight checked loaded nearby chunks, empty fluid space, body collision and solid support. It was deliberately small and is not a production arrival coordinator. It used a finite native reservation; it did not prove indefinite waiting, guard persistence across restart, claim-change protection or atomic recovery if the process stops during release.

[Raw combined report](evidence/native-travel-spike.json): nine PASS rows, one BLOCKED native-arrival row and its terminal failure diagnostic. The obstruction launch intentionally exited 1. Do not present this as an all-green native transport integration.

## Test controls and limitations

- Pinned MineColonies 1.1.1403-1.21.1 on Minecraft 1.21.1 / NeoForge 21.1.250.
- Each phase ran in a separate server process against the same disposable world. No native colony, citizen, job or inventory was reconstructed from a test-owned snapshot. Test SavedData held only expected identities, phase and timer metadata.
- Travel duration was advanced by calling the native manager's onTick method. These checks establish timer behavior and persistence, not natural scheduling, unloaded-colony progress or offline time advancement.
- Only endpoints were explicitly force-loaded, not a walking corridor. This does not establish a production chunk-ticket budget.
- Normal worker AI was paused when the probe controlled an entity. This was not a hunger, disease, combat, weather or multiplayer test.
- A native logger anchor and job were used. This does not validate persistence of the future custom Lodge/module.
- As in the earlier expedition spike, the minimal placeholder blueprint path produced Structurize lookup errors. Native fake-player subscriber warnings were present. Neither building construction nor connected-player behavior was being tested.
- Clean server saves were tested. Arbitrary process crashes, power loss, duplicate network commands, cargo handoff to storage and interrupted arrival transactions were not.
- The original ordinary-remote-hut placement restriction still applies.

## Fixture corrections and evidence

The negative control intentionally omitted the travel reservation. A respawn check recreated a live worker and the test failed, demonstrating that the absence assertion detects missing transit protection.

The GameTest server lacks the player-profile cache MineColonies requires to load colony permissions. An initial restart failed before travel evaluation. A first fixture hook at level load was too late relative to MineColonies' equally high-priority listener and could trigger backup recovery. The final fixture installs a cache at ServerAboutToStartEvent, before levels load, and caches only its synthetic owner. This uses reflection on the test server's Services field; it changes neither MineColonies code nor production behavior. Final-run phase logs contain no colony-load failure or backup-recovery message.

A completed-return check initially required the citizen to remain less than three blocks from the exact landing coordinate. Native reload left it at (0, 100, 3), beside the home workplace. The corrected check permits a safe position within ten blocks, requires original colony territory, collision clearance and support, and still checks all identity/inventory invariants. Immediate travel arrivals retain the stricter endpoint check.

Final evidence world: travel-a7a02b578a68415d897c1c8f567a1769. Seed, mid_transit, arrive_remote and return_home passed; verify_home passed after the location-check correction and another real restart. The final blocked_arrival launch demonstrated the native failure and both successful guard checks.

Local throwaway source, runner and detailed logs remain under .setup/travel-spike and are excluded from the normal source set. Source SHA-256: FAC28C0312255DBCD0F9753AFE0EBB8BC85937892D8B2892972AC6A982564AEA. Baseline commit: 3e8ea32.

## Development recommendation

Proceed with the persistent Expedition Lodge and one renewable wood camp, using native citizen data and native travel reservations as integration points.

The addon must own arrival validation and a persistent waiting-for-safe-arrival state. An expired native timer must not release a worker into an unchecked destination. Validate that integration across restart and interrupted release before advertising safe outpost transport. Then connect physical local harvesting/replanting and resource delivery to the previously tested inventory-transfer logic.

A MineColonies fork is not justified by these findings.

## Final normal-build verification

After restoring the original addon entry point, clean build and the original resource-audit GameTest completed with exit 0. The audit exported 1,671 items, 1,592 recipes and 2,630 candidate rows with zero candidate or inherent-recipe errors. Jar inspection confirmed no dev/colonyhorizons/spike classes were included. The saved evidence manifest was checked for exactly nine PASS checks, the native obstruction BLOCKED result and its expected terminal failure diagnostic.
