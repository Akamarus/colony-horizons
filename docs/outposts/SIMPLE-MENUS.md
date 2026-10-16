# Outposts & Supplies — development menu guide

This guide describes the separate development build. It does not change or install alpha.2.

## Open the menu

Open the native Town Hall and choose **Outposts & Supplies**. The colony owner can also use `/chsupply menu` while standing in that colony. The addon uses MineColonies' BlockUI controls and a two-page book layout.

## Group an existing outpost

1. On **Outposts**, choose **New Outpost** and enter its name.
2. Select existing completed huts from the Home list. Use the list scrollbar and page arrows for longer lists.
3. Choose **Review**. The list changes to show every hut included in the boundary, including huts automatically enclosed by your selection. Scroll through that complete list.
4. Choose **Activate**. The server checks the buildings, claims and assignments again before creating the district.

All buildings must belong to your existing colony. The chunk-aligned boundary includes native building bounds and warehouse racks. It must fit inside existing claims, exclude the Town Hall, avoid other outposts, and contain no more than 32 huts across 64 chunks. This does not build huts or claim more land.

Keep a citizen's assigned home and workplace on the same side of the boundary. Keep each courier's workplace and assigned warehouse together. If the review reports a split, correct the assignment using the native MineColonies building menu, then review again. Homeless workers and unemployed residents can belong through their existing workplace or home respectively.

Outpost **Pause** stops local courier work in that district. Protected supply routes have separate pause buttons.

## Set up a supply route

1. Hold the exact item you want shipped. Its name and components are preserved; the held item is not consumed.
2. Open **Supply Routes → New Route**.
3. Select a **Source** and **Destination**. Entries show their district, hut name and location. Select one Home hut and one active outpost hut of this same colony.
4. Set **Keep at destination** (default 64) and **Leave at source** (default 0). **Advanced** exposes shipment size (default 8).
5. Choose **Create Route**. The server binds and reuses hut endpoints internally; no endpoint names or identifiers are needed.

Routes use **hut inventories**, not warehouse racks. Put initial supplies into the source hut. For a forester export, use the forester's native **Pickup: Never** setting if local couriers would otherwise empty its export buffer. The addon does not change that setting for you.

Select a route to see its item, stock limits, shipment size, current cargo, remaining transit time, delivered total and waiting reason. **Pause/Resume** retains cargo. **Stop Route** asks for confirmation, stops future dispatches and returns outstanding cargo when the source becomes available. A stopped route cannot resume; create a new route if needed.

## What to expect

- Unloaded, replaced or newly foreign huts block work instead of losing cargo. Visit the location or correct ownership/assignments before retrying.
- Supply transport does not walk a courier through the wilderness or force-load the corridor. Native local couriers are restricted to their district during managed work; this is not general combat protection.
- Refreshes preserve selection. Validation errors retain entered values. Back remains available if the server is slow.
- Recovery warnings require server-operator review. The menu never deletes cargo, replaces damaged save files, or repairs uncertain inventory handoffs automatically.
- District deletion, resizing, automatic building, a mayor and independent colonies are outside this version.

## Verification status

See [the verification report](SIMPLE-MENUS-RESULTS.md) for the precise suites, visual evidence and remaining checks. The rendering fixture uses synthetic display data in a disposable client and never opens a world; it is not proof of end-to-end mouse interaction with a live colony. Native district, route and custody behavior is tested separately through automated server tests.
