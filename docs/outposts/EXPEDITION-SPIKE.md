# Expedition Lodge feasibility result

Status: **PROMISING — LIMITED RUNTIME PROOF**, not a playable Lodge or outpost release.

The home-workplace approach succeeds where ordinary remote hut placement failed. A registered addon job can keep a native citizen assigned to a legitimate home-colony workplace while directing physical work outside colony territory. No MineColonies fork was needed for this experiment.

## What ran

The isolated GameTest created one native colony and citizen, then placed a logger hut inside its territory through the normal build-tool setup API. That existing anchor stood in for a future Expedition Lodge. A temporary native WorkerBuildingModule assigned a registered addon job to that building.

MineColonies' normal CitizenAI scheduler ticked the addon AI. The worker followed native navigation in 16-block waypoints to a site 352 blocks away, outside every colony claim. It removed one real spruce-log block, obtained its normal block drops, stored the log in its own inventory, recorded one use on its supplied iron axe, and walked home.

Assertions verified:

- Native home-hut placement and addon-job assignment succeeded.
- The remote block was gone and exactly one spruce log returned in the citizen's inventory.
- The original entity UUID, citizen ID, colony ownership and home workplace anchor remained unchanged.
- There was exactly one registered citizen and one live citizen in the tested corridor.
- Native module unassignment released the job.
- The second successful run rejected movement jumps larger than eight blocks between AI callbacks; it used no explicit teleport or manual position changes after spawning.

[Five-case report](evidence/expedition-spike.json).

An inert-AI negative control failed with a travel timeout despite receiving native AI ticks. Enabling waypoint navigation and harvesting passed. A second fresh-world run with the additional movement, axe and home-anchor assertions also passed. Both successful Gradle processes exited zero; the negative control exited one.

## Limits of this proof

This is a throwaway feasibility experiment, not production implementation:

- The home building was an existing logger anchor, not a newly registered Lodge with menus, blueprint, upgrade tiers or recipes. The minimal in-memory blueprint passed placement, but its placeholder saved blueprint path was missing; Structurize logged lookup errors. This does not validate building construction or a usable structure pack. Native fake-player subscriber warnings were also present.
- The assignment module was transient. It was linked through the native API but was not registered as a persistent building module. Job/module restoration after a server restart is not proven.
- The entire flat test corridor was force-loaded in a disposable world. This is not an acceptable production chunk-loading policy, and it proves nothing about unloaded terrain or bounded route tickets.
- The test held daytime constant, disabled mob spawning and supplied the tool. It did not prove hostile-mob protection, recovery, hunger management, weather behavior or overnight operation.
- The minimal AI inherited AbstractEntityAIBasic so the native scheduler would run it, but used a custom tick body. Its inherited work-state safety machinery was not exercised. A production job needs explicit site permissions, claim-change checks, reach/tool checks and failure handling.
- One log is finite harvesting, not a renewable forestry loop. Replanting, tree selection, growth, resource requests and delivery to colony storage remain to implement.
- Axe wear was a simple test increment, not a validated enchantment/durability implementation.
- No travel-state save/reload, entity unload/respawn or multiplayer lifecycle was tested.
- The generic cargo/restart tests from the previous stage do not establish native worker persistence.

The experiment sources, initialization script and raw logs are local ignored scratch under .setup/expedition-spike. They are not included in the normal addon source set or release jar. Only findings and result evidence are retained in version control.

## Source findings that affect the next step

These references are to the pinned MineColonies 1.1.1403-1.21.1 source, cached in .setup/minecolonies-audit/source:

- CitizenColonyHandler.registerWithColony resolves a citizen's colony by stored ID, allowing location and ownership to differ.
- WorkerBuildingModule.assignCitizen and AbstractJob.assignTo provide the assignment path used here.
- CitizenAI.calculateNextState requires the normal non-guard work AI to derive from AbstractEntityAIBasic; simply subclassing AbstractAISkeleton does not meet that scheduling branch.
- MinecoloniesAdvancedPathNavigate.setPathJob discards a citizen after repeated navigation attempts into unloaded terrain. Requests exceeding 900 blocks may reposition a citizen at home. The experiment avoided both with loaded short waypoints.
- ITravellingManager already exposes startTravellingTo and finishTravellingFor. Starting travel does not discard the entity automatically.
- CitizenData.updateEntityIfNecessary suppresses respawn while the native travel manager considers the citizen in transit, then uses its destination when travel completes.
- TravellingManager persists destination and remaining travel time. Its suitability for protected outpost transport is a source-based candidate, not a runtime result from this spike.

## Recommendation

Continue as a MineColonies addon. Use a persistent Expedition Lodge inside the home colony to own remote camp records and assign expedition workers. Remote camps should not masquerade as ordinary native huts.

Before building the full interface, probe the native travelling manager with an assigned worker and real inventory: departure, suppressed duplicate respawn, restart during transit, safe arrival, blocked arrival and return. If that succeeds, implement one persistent Lodge and one renewable wood camp, using native travel for protected long journeys and visible local navigation at each end. Exact use of visible versus simulated travel remains subject to that lifecycle test.

The original ordinary-remote-hut test remains correctly BLOCKED. This experiment establishes a viable alternative work path; it does not turn the original failure into a passing native-hut test.

## Verification record

- Baseline repository: aab5ee0.
- Local experiment source SHA-256: 77C7EDB0C49F5ED72DC93023F9C8CFCCD474A60AF64D2CAD04AD14506B266132.
- Inert negative control: expedition-red-01, failed on travel timeout with native AI callbacks.
- First successful run: expedition-green-01, Gradle exit 0.
- Strengthened successful run: expedition-verified-01, Gradle exit 0, five expected report cases passed.
- After restoring the original entry point: clean build and the normal resource-audit GameTest passed, with 1,671 items, 1,592 recipes, 2,630 candidate rows and zero audit errors. The normal jar contained no expedition-spike classes.
- All experiment worlds were generated separately. No existing player world, MineColonies source or normal gameplay implementation was changed.

## Follow-up: protected travel

The [native travel experiment](NATIVE-TRAVEL-SPIKE.md) now demonstrates citizen/job/inventory persistence through departure, restart, arrival and return. It also reproduces unsafe arrival inside an obstruction. A temporary arrival preflight held the citizen in transit and safely retried after clearance; a persistent production coordinator remains to implement.
