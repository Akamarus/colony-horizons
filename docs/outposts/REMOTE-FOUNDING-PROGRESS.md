# Remote settlement implementation checkpoint

The approved remote-outpost/Mayor plan is **in progress, not release-ready**. Development version: `0.1.0-dev.remote.1`. The installed alpha.2 and real playtest saves remain unchanged: all942 recorded file hashes still match. This work has not installed or published an update.

## Implemented and exercised

- Separate strict remote authority, current-owner limits, revision checks and component-preserving budgets. Existing local districts gain no implicit remote authority.
- Measured Arcology warehouse-first forestry layout: builder, warehouse, courier hut, two foresters and five level-one homes (legacy four-founder layout retained), with reserved upgrade envelopes and unsafe terrain/access rejection.
- Native satellite claims beyond Home without expanding Home or claiming a corridor; interrupted claims reconcile across server restarts.
- Bounded production chunk tickets and actual native activity/natural growth while the real owner is at Home. Owner-offline/pause releases tickets. Home route endpoints have a shared16-chunk cap.
- Server-derived finite founding bill and verified withdrawal from the original completed Home warehouse. New templates prepay initial logistics construction and operating reserves; later buildings use finite warehouse shipments. Four-founder legacy records retain their original bill.
- Paid native level-zero hut placement with stable receipts, blueprint/terrain/permission rechecks, and interruption quarantine. No completed-building paste or production level setter.
- Protected original-citizen travel, safe supported arrival, blocked-arrival waiting and identity preservation. Restart tests retain original citizens and item components, not replacements.
- All-or-wait starter handoff to the original four or five native inventories, with persisted receipts and no duplicate refill. Native low-level tool checks exposed and corrected an unsuitable iron starter kit.
- Empty-site cancellation returns unused custody only to the original source with room; partial construction/citizen effects remain intact for recovery.
- A registered native Mayor job with its own manual-only management module at the paid staging builder hut. It does not consume the ordinary builder role. Native load-order restoration survives separate process restarts. Live availability requires the approved original citizen and actual paid station.
- Founder hiring stays within approved local roles. Routine non-null navigation destinations outside the remote footprint are rejected before MineColonies' long-distance teleport fallback. Cross-settlement building/decoration orders reject even when manually ignoring distance. Home housing cannot capture arrived remote founders; ordinary Home housing remains functional.

- Native level-one construction order coordination: builder-first dependencies, original local builder assignment, changed-terrain rejection, stable native order IDs and durable intent. Separate-server restart tests preserve the original order. The newer warehouse acceptance also verifies all ten level-one buildings completed through native work.
- Finite resupply allowances enforced during actual withdrawal, with immutable route identity and persisted dispatched totals. Real inventory tests retain cargo/allowance across service reloads and stop repeated refills at the cap. Returned cargo remains conservatively charged. The Mayor supply controller now reserves supplies, binds or reuses native hut endpoints and creates managed routes. Managed cargo pauses its clock when the owner is offline or the Mayor is unavailable.
- Pure Mayor decision policy: operating shortages, explicit targets, construction, then upgrades; approval mode proposes unapproved work; automatic mode checks scope and component-specific funds. A blocked higher-priority shortage cannot be bypassed to spend supplies on expansion. The live Mayor job now advances already-approved construction, with at most one planner action per100 ticks. Separate persistent expansion proposals and general production targets remain pending; native food/tool shortages are connected to the live planner.

- Shared persistent supply reservations, including already-paid founding costs, Home reserves and other settlements holding the same Home stock. Withdrawn quantities remain charged; cancellation accounting releases only unspent reservations. The ledger verifies its saved file before dispatch.
- Native construction material requests feed the Mayor supply controller. A real original Mayor now queues construction, requests finite material shipments, and assigns a founder to a completed native home while the owner stays at Home. The original builder completes both its hut and that home without a blueprint paste, level setter, creative resources, test-driven planner ticks, or supplies added after approval. The test checks Home inventory plus recorded dispatches against its initial finite stock.
- Independent material types can travel concurrently, with at most one new reservation per Mayor review. Requests above one stack retain their full target while each shipment remains at most one stack. Delivered hut supplies trigger a local native request reassessment so requests previously handed to the player can resume.
- Native food/tool shortages use stable request-based reservations and exact approved budget items that match native suitability rules. Low workplace food creates a small native request; native eating/pickup handles use. Repeated reviews do not duplicate shipments. These tests model consumption; extended natural eating and tool-break cycles remain a separate acceptance requirement.
- Completed native homes receive original unhoused founders through the native capacity-limited housing module. Existing homes are preserved. The Mayor uses the native working-job AI base and an explicit idle transition; actual NPC scheduling is covered by the completed-building test.
- Native courier endpoint and path boundaries now also recognize approved remote settlements, preventing them from being treated as ordinary Home huts.

## Still required

1. Generated-but-unloaded terrain survey. Current automatic survey only considers loaded explored terrain.
2. Remaining fault-injection/recovery cases and final transition into an operating settlement. Automatic first Mayor appointment and local courier staffing are implemented.
3. Sustained local food/tool use, doctor support and shortage visibility. All five founders receive local homes. Routine destination guards and courier path tests do not prove every worker emergency behavior; illness recovery still needs acceptance coverage.
4. Permitted upgrades and remaining interruption recovery. Full ten-building construction is now exercised with finite upfront Home warehouse stock; the deterministic logistics fixture isolates random illness.
5. Persistent expansion proposals, general resource targets and complete automatic/approval-mode management. Shared reservations, initial construction and native food/tool imports are implemented; autonomous management is not complete.
6. Simple MineColonies-style founding and Mayor screens.
7. Complete remote forestry/supply acceptance through native work cycles, shortages and restarts while the owner stays at Home.
8. Whole-branch review, exact packaged-server tests and an isolated colony-copy upgrade before recommending installation.

## Passing evidence

Reports: `build/reports/foundation/<run-id>/` in the development checkout. Named suites enforce exact case IDs; missing reports/nonzero exits fail. Multi-process suites use the same disposable world without reseeding.

| Check | Run |
| --- | --- |
| Autonomous native Mayor, completed builder/home, housing and finite Home conservation | `00ae5b8e1dd64595b0c975a693a732bb` |
| Native food/tool imports and duplicate-request protection,16 | `c7f7e90b691c4859a73c86ed6a72ae7f` |
| Native claims6 | `b997642dd494480194e6a9dc79dea30a` |
| Claims, three processes | `2d7e521e360d499abab7b4dd967cdd1b` |
| Endpoint limits/cleanup5 | `67c82f3d49394f87a12a77940727390d` |
| Protected travel, three processes | `76e8475c5abe4e3984be277d36f93fde` |
| Paid placement, three processes | `e7c485ed9e2443b4ac9a8b518cad6363` |
| Cancellation, three processes | `4e24cf56345d4ccbabde87d4cedeb7ff` |
| Combined founding,11+1+1 | `989b04e8e3d84212bc65947796e82880` |
| Native Mayor/tool compatibility,19+1+1 | `8514a74462374efd966f2da57d37417c` |
| Native construction queue/pause,21 | `c7ce10b113a64bbc8045349d5a1ed58c` |
| Construction order,19+1+1 | `b56bdd23d5dd499cb5ddac50ded29d73` |
| Finite supply allowance,6 | `142c720522f8413dbbc999ecb3810ff1` |
| Finite allowance through native huts,6 | `61ab50494dd54cb2ba9ded0efefb6037` |
| Shared reservation ledger/disk checkpoint,9 | `f3d0dcbd024544c1aa6520b1bc52e5f1` |
| Mayor-controlled native supply,20 | `422b4b61bbe34089bad09be13958abc6` |
| Native Mayor construction/material budget,22 | `4f300b946a464e4aac300e4f71c1d487` |
| Managed supply across processes,17+1+1 | `2f97d12762de42c993781582fc031c3a` |
| Mayor decision policy,7 | `08b35166ac1e4606a66142f7b52b463e` |
| Founding/locality,15 | `43d1af43fb694695afb5c831f545ba15` |

These are component and integration checks, not the final sustained-production release gate. Plain Gradle `test` has no test sources and is not acceptance evidence. Detailed red/green history and limitations remain in `.superpowers/sdd/2026-10-14-remote-outposts-and-mayor-alpha/progress.md`; the approved plan is `docs/superpowers/plans/2026-10-14-remote-outposts-and-mayor-alpha.md`.

Checkpoint regressions also passed: CourierMembership11, CourierNative22, CourierTravel3 and RemoteRecords11. Development/QA artifacts built successfully and the existing development-package boundary checker passed. Full remote packaged-server acceptance remains pending.


### Warehouse-first change requested October 14

The previous seven-building construction regression passed with finite initial Home supplies, including ordinary hopper overflow, and all four original founders housed (run `332d8a61043845db90d8e2192392509e`). Sustained forestry/endurance remains unverified.

Implemented in the development checkout: native builder hut, warehouse and courier hut first, then five homes and two foresters. The five original citizens include a dedicated local courier and an automatically appointed Mayor. New settlements require a completed Home warehouse. Recurring protected routes use the approved warehouses; native couriers carry goods locally, and missing/paused couriers hold protected cargo. Generic route creation cannot bypass this rule.

Founding withdraws a finite paid kit before departure: hut anchors/supports, native material requirements for the first three buildings, starter food/tools/saplings (including forester hoes), and 64 reserve bread plus two spare stone axes, pickaxes and shovels. Operating supplies can be handed to the builder hut between initial construction orders. Once logistics is ready, remaining paid stores are checkpointed into the warehouse; no ongoing Home-to-workplace shortcut is used. Unloaded registered racks wait and retry without permanently stopping unrelated supply routes. Warehouse racks participate in durable item checkpoints and the existing Home endpoint chunk limit.

The normal-disease ten-building run `e0452ba279f84e08a19f930fd090f05f` passed native construction, five homes, actual courier material carriage and finite Home conservation. Other long runs exposed native illness after the fifth citizen joined; food held by sick citizens did not mean an inventory failure. The deterministic construction/restart fixture now gives the five founders one initial immunity period, with no later healing, food creation or saturation changes. Production disease behavior is unchanged. Doctor support and sustained illness recovery are still required before calling the outpost self-sustaining.

Final regression verification passed: 93 named checks, including all three WarehouseRestart processes (`1d7e6fa106b0476292840e972bf018c6`), native courier safety, legacy Mayor/supply restarts and worker locality. Primary colony data loaded cleanly; no backup restoration was used. This is not an installation/release approval. Installed alpha.2 remains unchanged; all 942 baseline files were rechecked unchanged during this implementation.


### Reproducing the warehouse acceptance

From this development checkout in PowerShell (Java 21):

```powershell
./tools/Test-OutpostFoundation.ps1 -Suite WarehouseRestart -ArcologyPath '<user-home>/Documents/ChatGPT/Minecraft Colony Mod/.setup/alpha2-launcher-smoke/blueprints/arcology-horizons' -TimeoutSeconds 900
```

The runner creates a disposable world, builds from finite supplies, then launches two further server processes against that same world. It never installs into the playtest pack. `WarehouseInventory`, `WarehouseBootstrap`, `CourierNative`, `MayorRoleRestart`, `MayorSupplyRestart`, `RemoteEndpoints`, `SupplyNativeRestart`, and `RemoteLocality` use the same command with a different suite name. Construction uses fixed daytime and one initial QA immunity period to isolate logistics from disease; survival health/endurance coverage is separate.

Development and isolated QA JARs build successfully. The development-package check passed with the new warehouse hooks present and QA code excluded. Final installed-file check: 942 of 942 hashes unchanged. No release/package installation, commit, push or publication was performed. Exact packaged-game acceptance remains a separate release gate.


### Menus, survival recovery and endurance continuation

The development build now has a five-step Found Outpost menu and a simple Mayor page. Founding reviews Home warehouse, five original founders, supported site and finite supplies. The Mayor shows logistics readiness, health/food/tool/construction needs and remaining allowance, with Pause/Resume. Local Districts remain separate from remote settlement founding.

Native illness treatment and hunger now use finite local stores, including when the Mayor or courier is ill or a founder is not yet employed. Review and endurance tests exposed and fixed unsafe coordinate packing, a jobless care-request crash, fulfilled medicine requests blocking later illness, and a hungry courier's hut-walk loop. Native treatment still consumes medicine; production does not grant immunity or create resources.

Full ten-building native construction, five local homes, courier carriage, two forestry areas, protected log export and ten Minecraft days of finite operation passed. The strengthened test exhausts local medicine and infects all five founders a second time; it requires fresh paid imports and recovery. Normal packaged-server execution also passed this scenario. Separate-process restart/locality/courier/menu/food-tool regressions passed. Read [the detailed results and limitations](REMOTE-MAYOR-RESULTS.md) before treating this as an installable next alpha: expansion approvals and an existing-colony migration check remain separate release gates. Installed alpha.2 remains excluded.

### Upgrade approval checkpoint — 2026-10-15

Persisted owner-reviewed hut upgrades, native builder dispatch, research/terrain safeguards and simple Mayor approval menus are implemented. Native completion/restart, corruption/protocol, packaged endurance and 24-state/seven-button UI checks passed. A disposable player-colony copy also passed offline load/save/reload, preserving 18 buildings, 9 citizen records and 170 block-inventory records. Installed alpha.2 remains unchanged. Additional-hut expansion, in-progress upgrade interruption and live founding from the copied colony remain separate gates. See [upgrade results and exact scope](UPGRADE-APPROVAL-RESULTS.md).
