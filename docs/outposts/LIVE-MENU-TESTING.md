# Disposable live menu testing — 2026-10-14

This test uses the development checkout, never the installed alpha.2 playtest pack. It is a real integrated Minecraft server with native MineColonies buildings, ownership, inventories and the actual menu network messages. The fixture registers small hut platforms rather than constructing full Arcology buildings; this is not another building/navigation test.

## Reproduction

Use Java 21 and the project's existing Gradle cache. Copy an available Arcology Horizons pack into `runs/menu-live/blueprints/arcology-horizons` before starting. Do not point this run at a real colony save.

```powershell
./gradlew.bat runClient -PmenuLive '-Pmod_version=0.1.0-dev.menus.1' --no-daemon --console=plain
```

Create a disposable creative superflat world with commands enabled. The opt-in QA fixture creates a Town Hall and 18 completed native workplaces, enough to exercise paging. It seeds 64 oak logs in B Woods Forester and gives the player a held oak log. Outposts and routes must be created through the UI. The fixture is excluded from distributable jars.

The test save used here is `runs/menu-live/saves/Menu QA Disposablethe new`. The suffix is harmless text entered while naming the disposable world. Reopen it with `'-PmenuLiveWorld=Menu QA Disposablethe new'`. `/chmenuqa report` reports persisted district/route state. Add `-PmenuLiveDelayMillis=5000` for a QA-only five-second delay of action replies; this delays receipt on the client, not server execution.

## Observed live checks

- Native Town Hall Outposts & Supplies button opens the owned colony snapshot. Back returns to the native Town Hall. The `/chsupply menu` fallback opens from the world and Escape returns to the world.
- Entered Northern Woods, selected B Woods Forester, paged forward and back: name and selection stayed intact. Review automatically included C Woods Builder in the same chunk and displayed two included huts.
- A native assignment changed between Review and Activate: activation correctly rejected the stale draft without creating an outpost. Reviewing again and activating succeeded. Home showed 17 huts and Northern Woods two huts.
- Outpost Pause/Resume changed the displayed state and button correctly.
- Route source and destination selection survived paging. Entering a target of zero was rejected with a readable bounds error; selections and input remained. Correcting it to 64 and opening Advanced exposed the default shipment size of eight.
- Created a B Woods Forester → A Home Forester oak-log route. Observed cargo of eight with delivered count eight, then delivery of all 64 seeded logs and Destination stocked. This demonstrates transport of real inventory, not new log production by a working forester.
- Route Pause/Resume and the two-click Stop confirmation worked. After Stop the route showed Stopped and both action buttons were disabled.
- After a full client/server restart, Northern Woods was still present and active.
- With action replies delayed five seconds, Back closed the waiting main menu and it remained closed after the delayed reply arrived.
- At normal speed after restart, the original route still showed Stopped. Back from route setup returned to Supply Routes. Creating a second route in the opposite direction also returned directly to Supply Routes, showing both routes.
- Stopped the second route with eight logs in transit and 16 already delivered. The menu displayed Returning cargo, then Stopped with zero cargo and delivered still 16. Exact inventory-conservation assertions remain in the automated server suite.

## Navigation regression

Live testing found that both successful route creation and Back from route setup opened the Outposts tab, making the newly created route appear missing. Both now explicitly return to Supply Routes.

`tools/Test-SupplyMenuNavigation.ps1` runs the real BlockUI route form's Back and success-reply handlers in an isolated client, then checks which rows the returned window renders. The corrected test lifecycle invokes deferred `onOpened` before reading rows. With the original behavior, both cases failed showing Home/Northern Woods rows (`build/menu-navigation-red.log`). With the fix, both pass (`build/menu-navigation.log`). The wrapper requires the pass marker as well as a successful client exit.

```powershell
./tools/Test-SupplyMenuNavigation.ps1
```

## Scope

This supplements, rather than replaces, the automated server tests in `SIMPLE-MENUS-RESULTS.md`. It does not claim latency/load testing on a remote multiplayer server, every long-name layout, or all outstanding acceptance-inventory edge cases. Tooltip text provides full labels when compact rows clip long names.

Fixture setup initially used an incorrect Town Hall blueprint folder; it was corrected to `fundamentals/townhall1.blueprint`. This was a disposable QA fixture issue, not a shipped Arcology pack change.

[Live outpost menu](evidence/simple-menus/live-outposts.png)

[Live route creation after fix](evidence/simple-menus/live-route-created.png) · [Stopped route after cargo return](evidence/simple-menus/live-route-stopped.png)
