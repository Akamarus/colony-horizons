# Simple Outpost Menus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Preserve inline execution in this session; do not delegate implementation.

**Goal:** Let the colony owner configure existing outpost huts and protected supply routes through two familiar MineColonies-style pages.

**Architecture:** A server-owned menu session provides bounded snapshots and validates all actions over typed payloads. A native-building district planner and a route adapter reuse existing membership/custody services. BlockUI renders the result, with a narrow client-only Town Hall hook and a command fallback.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.250, MineColonies 1.1.1403, BlockUI, Mixin, GameTests, PowerShell.

**Spec:** `docs/superpowers/specs/2026-10-14-simple-outpost-menu-design.md` — approved 2026-10-14.

## Global Constraints

- Keep the installed alpha.2 and live worlds unchanged. Develop in the existing isolated safe-supply-routes checkout.
- Existing mixed changes must be preserved; no publication or installation is included.
- Use native MineColonies buildings, assignments, inventories and colony ownership.
- An outpost is a multi-building district of one colony. Do not found colonies, build huts, expand claims, assign workers automatically or add mayor behavior.
- Initially only the native colony owner can manage or inspect these addon controls, matching current supply authorization.
- Initial bound: at most 64 chunks and 32 huts per outpost. No chunk loading or claim creation.
- No UUIDs, Java enum names or required coordinate typing in the player menu.
- Show **Keep at destination** and **Leave at source** numeric fields. Defaults: 64 and 0. **Advanced** reveals shipment size, default 8.
- Confirm Stop with “Stop future shipments and return cargo already in transit.”
- Develop/package as `0.1.0-dev.menus.1`; leave release resources and existing public tags alone.
- Commits must include only audited task-owned files/hunks. If inherited changes cannot safely be separated, document deferred integration instead of staging the whole tree.

## Review Focus

1. A hut upgraded/replaced or a claim/assignment changed after Review must invalidate Create without setting the permanent managed marker (Task 2).
2. A timed-out or double-clicked Create must not duplicate a route; retry after partial endpoint binding must reuse compatible endpoints (Tasks 3–4).
3. An empty-cargo route still needs its item/settings and correct Pause/Resume control; temporary delivery reasons must not erase persistent state (Tasks 1, 5).
4. A large colony, long labels, high GUI scale or invalid text must remain usable without oversized packets or lost form input (Tasks 4–5).
5. Dedicated-server class loading and a failed Town Hall integration must not break the server or remove the command fallback (Tasks 4, 6).

## Execution environment and commands

Workdir: `.`.

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21'
$env:GRADLE_USER_HOME='./.setup/gradle-arcology-recovery'
$arcologyPack='<user-home>/Documents/ChatGPT/Minecraft Colony Mod/.setup/alpha2-launcher-smoke/blueprints/arcology-horizons'
& ./tools/Test-OutpostFoundation.ps1 -Suite MenuRead -ArcologyPath $arcologyPack -TimeoutSeconds 900
```

Tasks add suites to `FoundationGameTests.java` and `tools/Test-OutpostFoundation.ps1` with exact case IDs; use the same command replacing MenuRead with the named suite. Record RED/GREEN reports. Run Gradle sequentially. The existing external Gradle cache may need sandbox escalation. Read pinned local dependency sources before choosing injection points or payload codec signatures.

Shared Java paths below are relative to `src/main/java/dev/colonyhorizons/`. QA uses `qa/outpost/`. New client code is under `client/supply/`; server DTOs/services under `menu/`; networking under `network/`.

### Task 1: Read models and owned-colony snapshots

**Files:** modify `supply/SupplyNetwork.java`; create `menu/SupplyMenuModels.java`, `menu/SupplyMenuService.java`, `qa/outpost/SupplyMenuReadTests.java`; register `MenuRead` suite.

**Interfaces:**
- Add `SupplyNetwork.RouteSettings settings(UUID id)` returning immutable `RouteSettings(ItemStack item, int target, int reserve, int batch, boolean paused, boolean disabled)` with defensive item copies. Keep RouteView/save format compatible.
- `SupplyMenuModels` contains immutable DTOs for HutRow, DistrictRow, RouteRow and Page<T>(List<T> rows, int page, boolean hasNext). Positions/UUIDs are machine identifiers; display strings are separately bounded.
- `SupplyMenuService.snapshot(ServerPlayer actor, int colonyId, int hutPage, int districtPage, int routePage): MenuSnapshot`. The snapshot contains pages, colony label and a sanitized problem string. Page size 16; negative/out-of-range requests return validation errors. Owner and dimension are server-resolved; no force loading.

- [ ] Write `MenuRead` failing cases: `empty_route_settings` asserts item/settings survive zero cargo; `settings_copy` asserts mutating returned ItemStack changes no route; `settings_reload` asserts saved/reloaded pause/cancel flags persist; `owner_only_snapshot` rejects a foreign actor; `paged_snapshot` returns 16 stable sorted rows and hasNext for 17 huts; `unloaded_snapshot` labels unavailable without loading a chunk.
- [ ] Run MenuRead and record expected missing-interface/test assertion failure.
- [ ] Implement DTOs, route accessor and snapshot service. Filter routes by current colony, not merely endpoint owner. Stable ordering by display name then ID; no raw path/exception text. Reuse live availability checks.
- [ ] Run MenuRead and existing Supply suite; require every named case PASS and exit 0.
- [ ] Review and checkpoint only these changes; defer mixed-file hunks if necessary.

### Task 2: Review and activate a real multi-hut district

**Files:** create `menu/OutpostMenuPlanner.java`, `menu/NativeMenuBuildings.java`, `qa/outpost/OutpostMenuTests.java`; modify `menu/SupplyMenuService.java`; register `MenuDistricts`.

**Interfaces:**
- `NativeMenuBuildings.inspect(ServerLevel level, int colonyId, BlockPos anchor): BuildingFacts`, with actual native bounds, block type, pack/path, warehouse rack positions, assigned residents/workers and display name. Missing metadata is a blocker, never a fabricated reference.
- `OutpostMenuPlanner.review(ServerPlayer actor, int colonyId, String name, Set<BlockPos> selected): DistrictDraft`. Draft contains normalized name, complete included huts, chunk footprint, citizen memberships, blockers and a fingerprint of relevant native facts.
- `OutpostMenuPlanner.activate(ServerPlayer actor, int colonyId, DistrictDraft reviewed): UUID`; recompute review and compare fingerprint, then register ACTIVE only after all checks succeed. `SupplyMenuService.pauseOutpost(ServerPlayer actor, int colonyId, UUID id, boolean paused): void`; resume invokes existing native activation validation.

- [ ] Write MenuDistricts failing cases: `multi_hut_draft`, `enclosed_hut_included`, `rack_extends_boundary`, `missing_claim_rejected`, `overlap_rejected`, `townhall_rejected`, `limits_rejected`, `unloaded_rejected`, `missing_blueprint_rejected`, `cross_assignment_rejected`, `stale_review_rejected`, `activate_and_pause`, `foreign_owner_rejected`. Rejected activation asserts unchanged records AND native managed marker. Limits exercise 65 chunks and 33 huts. Stale cases separately replace a hut, change ownership/claim and worker assignment.
- [ ] Run MenuDistricts RED.
- [ ] Implement rectangle closure: expand to complete building/rack extents and include all enclosed huts until stable, applying bounds throughout to prevent unbounded scanning. Reject overlaps between building reserved volumes and missing/foreign claims. Names trim to 1–32 characters and are unique within the colony.
- [ ] Derive membership from native home/work assignments. If one assigned endpoint lies inside and the other outside, reject; a homeless worker may belong by workplace, and a jobless resident by residence. Reject courier workplace/assigned-warehouse splits. No citizen reassignment. Keep the draft in server session state for later Create.
- [ ] Run MenuDistricts plus CourierMembership, Records and CourierNative. Require all PASS.
- [ ] Checkpoint scoped changes with review evidence.

### Task 3: Route creation through hut selection

**Files:** create `menu/SupplyRouteActions.java`, `qa/outpost/SupplyMenuRouteTests.java`; modify `supply/NativeSupplyService.java` only for safe endpoint lookup/reuse and read access needed by this adapter; register `MenuRoutes`.

**Interfaces:**
- `SupplyRouteActions.create(ServerPlayer actor, int colonyId, BlockPos source, BlockPos destination, int target, int reserve, int batch): UUID`.
- `SupplyRouteActions.change(ServerPlayer actor, int colonyId, UUID route, RouteAction action): void`, enum PAUSE, RESUME, STOP.
- Add `NativeSupplyService.endpointAt(UUID actor, BlockPos anchor): Optional<Endpoint>`; never replace a marker or reinterpret an existing endpoint's role.

- [ ] Write MenuRoutes failing cases: `home_outpost_pair`, `same_district_rejected`, `foreign_colony_rejected`, `held_components_preserved`, `empty_hand_rejected`, `limits_rejected`, `compatible_binding_reused`, `incompatible_binding_rejected`, `partial_bind_retry`, `pause_resume`, `stop_with_cargo`, `stale_hut_rejected`. Assert no item consumption, no cargo deletion and no new route on rejected actions.
- [ ] Run MenuRoutes RED.
- [ ] Validate all pair/item/policy/native facts before binding. Source/destination must resolve as ACTIVE home and ACTIVE named outpost of this colony. Generate collision-safe internal endpoint names within existing 32-character limit; player sees native hut names. Reuse valid pre-existing bindings, including bindings persisted before a later failure. Invoke existing link/pause/cancel custody methods. Surface recovery mode without attempting automatic repair.
- [ ] Run MenuRoutes, SupplyBridge, SupplyNative and SupplyNativeRestart; verify receipt/custody assertions unchanged.
- [ ] Checkpoint scoped changes.

### Task 4: Bounded menu protocol and server sessions

**Files:** create `menu/SupplyMenuSessions.java`, `network/SupplyMenuPayloads.java`, `network/SupplyMenuNetworking.java`, `qa/outpost/SupplyMenuProtocolTests.java`; modify `supply/SupplyCommands.java`; register `MenuProtocol`.

**Interfaces:**
- `SupplyMenuSessions.open(ServerPlayer actor, int colonyId): MenuReply`; `handle(ServerPlayer actor, MenuRequest request): MenuReply`; `close(UUID actor): void`.
- `MenuRequest` carries session UUID, monotonic request number, action enum and bounded action-specific data. Actions: REFRESH, REVIEW_OUTPOST, CREATE_OUTPOST, PAUSE_OUTPOST, RESUME_OUTPOST, CREATE_ROUTE, PAUSE_ROUTE, RESUME_ROUTE, STOP_ROUTE, CLOSE. CREATE_OUTPOST references stored reviewed draft; it accepts no serialized authoritative draft from the client.
- `MenuReply` carries session/request IDs, MenuSnapshot, optional DistrictDraft display data and an outcome code plus safe message. Item selection for CREATE_ROUTE always comes from the current server main hand.
- `SupplyMenuNetworking.register(RegisterPayloadHandlersEvent event): void` registers typed request/reply and open payloads. Common/server registration must never reference client screen classes directly.

- [ ] Write MenuProtocol failing cases: `round_trip`, `oversized_decode_rejected`, `unknown_session_rejected`, `session_actor_bound`, `duplicate_create_once`, `reordered_request_rejected`, `expired_session_rejected`, `changed_owner_rejected`, `rate_limit`, `draft_invalidated`, `server_only_classloading`. Exercise exact components in snapshots and bounded UTF-8 text.
- [ ] Run MenuProtocol RED.
- [ ] Implement actor/colony/dimension-bound sessions, 10-minute idle expiry, one session per player, cached last successful reply for exact duplicate request; same number with different content is rejected. At most 4 actions per second/player, REFRESH no more than once/second. Decode limits: selection32, page16, names32 chars, display text256 chars; aggregate snapshot cap64 KiB with graceful “narrow selection” error. Never loop unbounded client counts. Server-thread execution and disconnect/server-stop cleanup.
- [ ] Add `/chsupply menu` resolving the player's current claimed colony; require owner. Town Hall entry sends colony ID for server validation. Refresh rechecks current ownership; replies carry no unowned data. Return only sanitized errors while logging full failures server-side.
- [ ] Run MenuProtocol and compilePublicJava. Confirm a dedicated server starts with networking registration and no client-class errors.
- [ ] Checkpoint scoped changes.

### Task 5: Two simple BlockUI pages and native Town Hall entry

**Files:** create `client/supply/SupplyMenuClient.java`, `client/supply/WindowOutpostsSupplies.java`, `client/supply/WindowOutpostSetup.java`, `client/supply/WindowSupplyRouteSetup.java`, `compat/mixin/TownHallSupplyMenuMixin.java`; create `assets/colonyhorizons/gui/supply/{main,outpost,route}.xml` and localization keys under `src/main/resources`; modify main and supplyDev mixin configs with client-only hook; create `tools/Test-SupplyMenuAssets.py`.

**Interfaces:** `SupplyMenuClient.receive(MenuReply reply): void` owns client snapshot/session and safely schedules UI updates; `open(int colonyId, @Nullable BOWindow parent): void` requests authorized opening. Window constructors consume snapshots/client callbacks only, never mutate server services. Ignore late replies from a closed/replaced session.

- [ ] Add asset/protocol presentation checks: every referenced pane/translation key exists; all statuses map to player text; exact stop confirmation; numeric defaults64/0/8; advanced section starts hidden; main window has only two tabs; no production QA resources. Run `python tools/Test-SupplyMenuAssets.py` RED.
- [ ] Implement main lists/detail panes with selection retained by ID, page controls and actionable empty states. Outpost setup is name/checklist then server Review then Create & Activate. Display included-hut changes and blockers before confirmation. Route setup has grouped hut pickers, server-confirmed held item, two visible number fields and Advanced shipment size. Preserve text on failure; disable pending action buttons; refresh at most every40 ticks while visible, without replacing edits. Pause text derives from persistent flags, not current reason.
- [ ] Add Town Hall button through pinned native window lifecycle/layout API; avoid overwriting native buttons or layouts. Keep Back parent and Escape behavior consistent with native menus. A command-opened window closes to game. Register client payload handling only on client distribution.
- [ ] Run asset checks and MenuRead/MenuProtocol. Build development resources with client hook only under mixin `client`, absent from server mixin list and unchanged release config.
- [ ] Verify disposable-client layout/input: 1280×720 and 1920×1080, GUI scales2/3, long names/statuses, empty and multi-page lists, keyboard entry, disabled controls, Back and delayed responses. Use manual checks only for rendering/input; record screenshots/results and any untestable condition honestly.
- [ ] Checkpoint scoped changes.

### Task 6: Packaged integration, regression and user instructions

**Files:** modify `tools/Test-SupplyDevPackage.py`, `tools/Test-SupplyPackagedServer.py` to accept an explicit development JAR/version instead of the previous hard-coded forester version; create `docs/outposts/SIMPLE-MENUS.md` and final verification evidence. No changes to installed pack.

- [ ] Extend package assertions for menu classes/resources/client hook, no QA assets/references, expected distinct version. Verify test script rejects a wrong/stale JAR before server start.
- [ ] Build `./gradlew.bat supplyDevJar supplyQaJar '-Pmod_version=0.1.0-dev.menus.1' --no-daemon --console=plain`. Run package inspection with the exact built path, then the isolated installed-server smoke using that JAR and separate QA harness. Require exit0 and all case IDs PASS; capture SHA-256.
- [ ] Run MenuRead, MenuDistricts, MenuRoutes, MenuProtocol, CourierMembership, CourierNative, CourierTravel, CourierRestart, SupplyNativeRestart and ForesterWithCouriers sequentially. No broadened unrelated Arcology testing unless changed behavior/failure warrants it.
- [ ] Write user instructions: Town Hall entry; create multi-hut district; adjust native assignments when blocked; create supply route; forester Pickup: Never tip; difference between district pause and route pause; loaded-chunk/current-colony limits; stop returns cargo.
- [ ] Record regression IDs and visual results, review complete diff, run scoped `git diff --check`, verify starting installed mod/blueprint hashes still match. Mark any blocked visual test as incomplete rather than passed. Summarize tested features and limitations. No installation, publication or remote push.

## Self-review and handoff

Spec coverage: presentation/entry Task5; boundaries/native metadata/assignments Task2; route creation/custody Task3; readable settings Task1; authorization/payload/lifecycle Task4; packaging/verification/docs Task6. Review Focus entries each have named tests above. No independent claim creation, district resizing or mayor work is included.

Approved by the user and implemented inline. Implementation and executable evidence are recorded in `docs/outposts/SIMPLE-MENUS-RESULTS.md` and `.superpowers/sdd/2026-10-14-simple-outpost-menus/progress.md`. The detailed checklist above is retained as the original acceptance inventory; unexercised cases and live input checks are explicitly listed in the results report rather than marked passed. Integration remains deferred because this checkout contains inherited mixed changes. This plan does not authorize updating alpha.2.
