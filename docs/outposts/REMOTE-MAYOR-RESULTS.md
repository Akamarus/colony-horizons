# Remote outpost menus and recovery — development checkpoint

This work is isolated in the safe-supply-routes checkout. The installed alpha.2 pack and its worlds are not an output target. This is a development build, not a published alpha.3 or a declaration that every feature in the larger outpost plan is finished.

## Player flow

Open the Town Hall supply menu and choose **Found Outpost**. The main menu retains **Outposts** and **Supply Routes**; **Local District** retains the older within-colony grouping feature.

1. Select a completed Home warehouse.
2. Select five available adults in order: Mayor, builder, two foresters, courier.
3. Name the forestry outpost and enter the floor X/Y/Z of an explored, supported site. Coordinates outside Minecraft's supported range are rejected before packing. The server reviews the layout, claims and actual terrain.
4. Choose finite item allowances and how much stock to leave at Home. Item choices are paginated. Allowing the current page's stock is an explicit shortcut; review it before approval.
5. Review the required founding materials. Approval rechecks server authority and supply availability before spending. A rejected preflight does not leave an orphan proposal.

The Mayor page shows building progress, local courier readiness, illness and native food/tool requests, construction needs, and remaining allowance after founding spending and reservations. Pause/Resume and Refresh are available. Long row descriptions have full tooltips.

The builder hut precedes the warehouse and courier hut because native construction needs a builder. A finite paid founding kit supports those first buildings. Recurring imports/exports then use warehouses; the original courier delivers locally. The approved initial layout has ten buildings: builder, warehouse, courier, two foresters and five homes. Once complete, the Mayor assigns the original foresters and enables a surplus-log route that targets 64 at Home and retains 16 locally.

## Illness and hunger

The founding bill includes a bounded medical reserve based on native disease cures. Native disease treatment still consumes actual medicine; there is no production immunity or free healing. Sick residents can physically collect medicine from their local workplace or store, including when the courier is ill. Standing care reviews can request finite approved supplies while the Mayor is ill. Unemployed original founders use local building requests safely, and fulfilled medicine requests retire so another illness can receive a new request.

Hungry residents can collect suitable local food, including during the native hut-walk state. Ownership, active-settlement, physical-arrival and inventory-space guards remain in effect. Offline owners and paused outposts do not gain an unrestricted care or transport bypass.

## Evidence

- Focused illness regression: 20 checks, including finite pickup, native cure, hunger, pause, unemployed requests and a second illness after depletion. Development run `4eb1102cedbd41e4a9cae939ff8f7b41`; exact packaged server run `be3152f87f954890909f53ae88310b09`.
- Menu regression: 12 checks covering ownership, bounds, warehouse requirement, review without effects, malformed budgets, proposal resume rejection, truthful summary, bounded options, no orphan proposal, replay, wire copying and coordinates. Packaged run `e0b1f53d67c6472597dcb1bae1c4a098`.
- Full native endurance: 240,000 ticks (ten Minecraft days), original five founders, ten native completed buildings, five homes, local courier carriage, two forestry areas with at least three distinct harvested/replanted tree positions each, protected log export and finite Home stock conservation. All five begin ill without immunity; after 120,000 ticks the fixture exhausts local cure stock and infects them again. Success requires new paid medicine imports and everyone healthy and fed. Development run `d365f0a0d52f46e18b53f0662f907c01`; packaged normal-server run `db8e339f262a4f02b51d80478ababba8`.
- Restart: all three WarehouseRestart processes passed in `58b07831c99b4ce984b7bf2f73d07e01`, including paused reload and original protected cargo. Primary saved data loaded, without a backup fallback.
- Packaged client: 20 rendered states at 1920x1080 with GUI scales 2 and 3, plus five real button-handler navigation checks. Rendering `eb2aa7f522f54598b50a1af43a877019`; navigation `8474adf8031245319fd951999fbf853b`. The development client also rendered 20 states at 1280x720. The title was corrected to ASCII and the final package rerun passed; see below.

The endurance world starts with finite supplies and initial trees. After approval it adds no resources, completes no buildings artificially and injects no output drops. Disease injection and local medicine removal are explicit failure scenarios. It uses fixed daytime and accelerated ticks; it does not certify indefinite operation, night raids or arbitrary terrain. The distinct replanted positions are not a per-worker proof of three generations of natural regrowth.

## Still outside this completed slice

- Persisted upgrade proposals and simple approval menus now cover original outpost huts inside their approved footprints; see UPGRADE-APPROVAL-RESULTS.md. Additional buildings and new claimed land remain pending.
- Founding currently targets the reviewed forestry layout. It is not automatic biome scouting or a general settlement editor; unsuitable terrain is rejected rather than silently creating floating buildings.
- Editing an established outpost's budget, replacing lost founders/Mayor and broader recovery UX need further release work.
- A disposable copy passed offline native load/save/reload with its 18 buildings, 9 citizen records, job/home assignments, and 170 block-inventory records preserved. Owner-login and new-outpost founding from that copy remain a separate acceptance gate.
- No new public alpha, installation, Git push or publication is performed by this checkpoint.

## Reproduce

Use Java 21. `tools/Test-OutpostFoundation.ps1 -Suite RemoteEndurance -ArcologyPath <pack-folder> -TimeoutSeconds 900` creates a disposable development world. Other focused suites are `RemoteIllness`, `RemoteMenu`, `WarehouseRestart`, `RemoteLocality`, `CourierNative`, `MenuProtocol` and `MayorNeeds`.

Build `test supplyDevJar supplyQaJar -Pmod_version=0.1.0-dev.remote.1`. Plain Gradle `test` has no test sources and is not the acceptance evidence. The separate QA jar is never included in the production artifact.

`tools/Test-SupplyPackagedServer.py --jar build/supply-dev-libs/colony-horizons-0.1.0-dev.remote.1.jar --version 0.1.0-dev.remote.1 --suite RemoteEndurance` launches an isolated normal NeoForge server. `RemoteMenu` and `RemoteIllness` are additional suites. `tools/Test-SupplyPackagedClient.py` accepts the same jar/version and optionally `--navigation`. It uses existing local launcher libraries and an offline QA profile, opens no colony save, and closes itself. These harnesses require the prepared local dependencies and Arcology test pack; they are not portable launcher installers.

## Final artifact verification

Final development jar: `build/supply-dev-libs/colony-horizons-0.1.0-dev.remote.1.jar`.

SHA-256: `c408bd8ab77ec792f15d21a80b2484d9a3411f614884b80752b7ae2b10d445e1`.

The exact final jar passed packaged normal-server endurance (`527d6df63fbf450585c6d61a07559534`), 20-state packaged client rendering (`054e6f1ad8c74ce89c57f7db7db297fb`) and five packaged button-handler checks (`4d1dec5bd1014e569e6cd0d4ed650093`). The isolated clients exited successfully and opened no colony save. The final title screenshot was inspected visually. Artifact inspection passed, with production hooks included and QA excluded.

Additional final regressions passed: RemoteLocality 16 (`cd621c07d6a2430a91a32ebaadcadd4c`), CourierNative 23 (`854a4d65872e42bf8c14e078e432110e`), MenuProtocol 10 (`d48419e9e6014550af9acb2246482c94`), MayorNeeds 16 (`51e578205c7c4ab887e27603231abc90`). Together with the menu, illness, endurance and three restart results above, these are 101 named case results, plus five button checks; some fixture assertions repeat across suites.

Final preservation check: all 942 baseline installed-pack files, including saves, have identical SHA-256 hashes. Nothing was installed or published. Independent code review accepted the reviewed fixes; the limitations above remain explicit release gates.
