# Simple outpost and supply menus

Status: approved by Akamarus on 2026-10-14; implementation plan prepared separately. Implementation has not started.

## Intent and boundaries

Akamarus wants the proven forestry supply loop usable through simple menus resembling current MineColonies menus. Keep the installed alpha.2 and live worlds unchanged. Develop in the existing isolated safe-supply-routes checkout. Existing mixed changes must be preserved; no publication or installation is included.

Use native MineColonies buildings, assignments, inventories and colony ownership. An outpost is a multi-building district of one colony. This interface does not found colonies, build huts, expand claims, assign workers automatically or add mayor behavior.

## Chosen presentation

Add one **Outposts & Supplies** entry to the Town Hall menu, opening a BlockUI window with the familiar MineColonies controls. Use the existing window framework and shared widget styling without replacing the Town Hall layout wholesale. A narrow client integration hook adds the entry; the original Town Hall remains the parent for Back. Provide `/chsupply menu` as a fallback entry into the same interface, subject to the same permissions.

Alternatives considered: a separate management item adds another object to carry; a custom full-screen dashboard diverges from the requested familiar menus. Prefer Town Hall entry plus two pages.

The window has **Outposts** and **Supply Routes** tabs, a short scrolling list, one selected-entry detail area, and Back. Rows show names and one status line. No UUIDs, Java enum names or required coordinate typing. Coordinates appear only as secondary location information. Long names and reasons wrap or truncate with a tooltip. Empty lists explain the next action.

### Outposts

- List each outpost with status and hut count. Home is a distinct fixed entry showing buildings outside outpost boundaries.
- **New Outpost** opens a name field and a checklist of existing buildings in this colony. Display hut name, profession and location. Built huts with complete native blueprint/bounds metadata are eligible; unavailable or incomplete huts explain why they cannot yet be selected.
- **Review** calculates a chunk-aligned rectangle enclosing the selected buildings' native reserved volumes. Show its dimensions, selected huts and any other huts inside. Include all enclosed colony huts or require the selection to change; never silently strand an omitted hut inside a managed district. Include warehouse rack extents when determining its footprint.
- Require every included chunk to be an existing claim of this parent colony, every included hut to be loaded and validated, and no overlap with another outpost or the Town Hall. Reject oversized regions with a clear reason. Initial bound: at most 64 chunks and 32 huts per outpost. No chunk loading or claim creation.
- The review explicitly states that local couriers must keep their workplace, warehouse and work within the district. Derive citizen membership from native home/work assignments and reject cross-boundary assignments with names of the affected workers/buildings. Show how to correct them through ordinary MineColonies assignment menus.
- **Create & Activate** revalidates and registers the record on the server. Initial development/forestry bounds cover the reviewed footprint; these are membership metadata, not an instruction to harvest or build throughout it. Snapshot the actual native blueprint references and bounds; do not invent placeholder paths.
- Existing outposts show their huts and the first actionable blocker. **Pause / Resume** governs local courier work. Explain that protected supply routes have separate pause controls. Do not expose deletion, resizing or automatic reassignment in this first menu.

### Supply Routes

- Rows show item icon/name, source to destination, and plain status such as “Delivering”, “Destination full”, or “Keeping source reserve”. Selection shows cargo, arrival time and delivered total.
- **New Route** asks for source hut, destination hut and the item held in the player's main hand. The server copies the held item's identity/components without consuming it. Picker entries are grouped under Home and named active outposts. Only a home/outpost pair in this colony is valid.
- Bind eligible huts internally on creation; players do not name supply endpoints or copy identifiers. Reuse existing compatible bindings. Revalidate actual district membership instead of trusting the existing endpoint's home/outpost boolean. Existing incompatible bindings produce an actionable error and are never silently changed.
- Show **Keep at destination** and **Leave at source** numeric fields. Defaults: 64 and 0. **Advanced** reveals shipment size, default 8, with the current server limits. No per-route travel-speed control.
- **Create** uses the existing durable supply service. Show the resulting route only after server success. Prevent double-click duplicates through request IDs/session deduplication; preserve entered values on validation errors.
- Selection offers **Pause / Resume** and **Stop Route**. Confirm Stop with “Stop future shipments and return cargo already in transit.” Use existing cancellation/custody behavior; no deletion of cargo or automatic recovery of quarantined saves.
- Show a brief note that supply uses hut inventories. For a forester export route, advise the native “Pickup: Never” setting so local pickups do not empty its export buffer; never change that setting silently.

## Server and client boundary

Introduce a small server-side menu service above OutpostSavedData, NativeCourierDistricts and NativeSupplyService. Expose read-only bounded snapshots plus validated actions. Extend the route read model to include item, target, reserve, batch and persistent pause/cancel flags; never infer pause solely from transient status text. Preserve save compatibility.

Use typed NeoForge payloads with bounded strings/lists and server-thread handlers. Take actor identity from the connection. Resolve dimension, colony, ownership, huts, item and membership on the server for every action. Initially only the native colony owner can manage or inspect these addon controls, matching current supply authorization. Reject foreign owners, stale membership, replaced huts and unknown IDs without mutation. Client-only classes must not load on dedicated servers.

Refresh on open, after actions and at a modest interval while open. Retain selection by ID. Unloaded huts may be listed as unavailable without loading chunks. Bound snapshot size and payload/action rate. Never send filesystem paths or raw exception stacks to players. Display a short recovery message and retain detailed diagnostics server-side.

New-outpost validation must finish before permanent registration marks the colony managed. Native ownership/claim/assignment changes remain authoritative after creation; the interface does not weaken runtime courier guards.

## Verification and completion

1. Automated server tests: authorized creation, multi-hut inclusion, warehouse rack bounds, cross-boundary worker rejection, missing claims, overlapping/oversized footprints, unloaded/replaced huts, foreign owner and malformed/stale actions. Rejections preserve saves and inventories.
2. Route tests: automatic endpoint reuse, genuine home/outpost selection, exact held-item components, limits, duplicate clicks, pause/resume and cancellation with cargo. Verify settings remain visible when cargo is empty and after restart.
3. Protocol tests: payload round trips/limits and dedicated-server startup without client class loading. Build the distinct development JAR and verify packaged hooks/resources.
4. Rerun affected courier, membership, supply/restart and forestry integration suites. Keep existing custody checks intact.
5. Use a disposable client only for visual/input checks: Town Hall entry, Back, tabs, scrolling, small display/GUI scales, long names, tooltips, error layout and disabled controls. No manual repetition of automated inventory proofs.
6. Document actual setup steps and limits. Compare installed alpha.2 hashes again; no installation or publication.

Approval checkpoint: review this screen flow before writing the implementation plan. Scoped commits remain separate from unrelated inherited release changes.
