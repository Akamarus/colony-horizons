# Roadmap toward stable 1.0

Updated 2026-10-14. These milestones describe planned work, not current functionality or promised dates.

## Available: compatibility foundation (alpha.2)

MineColonies compatibility fixes and the separate 305-blueprint Arcology Horizons pack, including Stable, Gatehouse, quarry and plantation modules. Alpha.2 adds Mine shaft fluid sealing, supply-raft waterline metadata and Builder's Hut recall safety rails. It does not expose automated outpost founding, a Mayor UI or protected deliveries.

## 1. Stabilize the foundation

Collect reproducible alpha reports; maintain upgrade and regression tests. Add dedicated-server/two-player coverage for building, recall, mining, upgrades and reload. Test install/update/rollback instructions. Exit: no unresolved critical data-loss, duplication or progression-blocking bugs in supported scenarios.

## 2. Safe supply routes — next feature milestone

Connect the internal route design to native inventories and requests. Support main colony/outpost links, import/export targets, local reserves, priorities, capacity and delivery status. Keep cargo and couriers safe during long-distance or unloaded travel; define visible departure/arrival behavior without requiring dangerous continuous pathfinding. Prevent loss/duplication across full inventories, cancellation, unload, restart and interrupted handoffs. Exit: a real two-settlement route delivers requested goods repeatedly and survives those failure cases.

## 3. Useful multi-building outposts

Use existing MineColonies huts, workers and building upgrades. Establish coherent small settlements with housing, production and supplies. Support both player-selected sites and assisted suitable-site selection. Integrate Arcology layouts with terrain and entrances; reject floating or inaccessible placements. Exit: an outpost produces a biome-specific resource and supplies the main colony without repeated manual hauling.

## 4. Mayor-assisted development

Give each settlement a dedicated manager. Let the player specify resource goals, budgets, reserves and construction priorities; explain shortages and planned actions. Allow approvals, pause and manual override. Found, build and expand outposts through native builders and requests. Exit: a Mayor can carry out a bounded development plan without starving the colony or repeatedly scheduling blocked work. Conversational AI is optional later, not a stable-release requirement.

## 5. Beta: complete the cross-biome loop

Validate resource acquisition, crafting and transport end to end. Reconcile the vanilla resource coverage map with actual supported production; document exceptions rather than promise every item. Improve onboarding and a shared needs/delivery dashboard. Test multiple colonies, route cycles, prolonged sessions, server restarts and sensible performance limits. Exit: players can establish and maintain a working cross-biome network with understandable controls.

## 6. Stable 1.0 release gate

Freeze features. Run regression, dedicated-server, migration and normal-launcher checks on the exact release artifacts. Resolve critical/high-severity issues, document known limitations and pin supported dependencies. Publish reproducible source, checksums, installation/backup/rollback instructions and a compatibility matrix. Stable means this defined core works reliably; it does not mean every long-term idea is complete.

## After the core is stable

- Deeper progression using native building levels, worker unlocks/efficiency and optional level-five specializations.
- Colonist ambitions, varied appearances and possible evolution, subject to MineColonies integration feasibility.
- Rescue/recovery, more transport choices and settlement personality.
- Ars and other magic integrations only after the main addon is ready.

Keep experimental features separate from bug-fix releases. Prioritize by player impact and milestone exit criteria, not elapsed time or number of tests.
