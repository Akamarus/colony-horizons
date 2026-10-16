# Exploration Hut — implementation and verification

Status: implemented and independently reviewed. Development build; installed in a backed-up local playtest instance on 2026-10-15. Not a new public binary release.

## Player flow

Build the existing hut blueprint (now displayed as **Exploration Hut**) and assign one Explorer through the native Workers tab. Its registry ID remains `colonyhorizons:blockhutsurveyor`; existing huts and saves retain their identity. Open the Exploration tab from the hut.

1. Choose a resource and put eight bread plus its listed tool in the hut. **Discover** searches actual terrain and returns a real sample. A successful return records the resource, source position, and actual biome.
2. A returned discovery unlocks **Gather once** and **Repeat** for supported resources. Each gathering trip consumes two bread and takes 6,000 eligible game ticks (five minutes at normal tick rate).
3. Gathering is simulated: it does not harvest or load distant terrain. The original Explorer uses protected native travel; local couriers remain within the colony.
4. Resources and the remaining tool return to the hut. A normal courier pickup request makes the goods available to the warehouse. Configure native minimum stock to retain bread and tools.
5. **Recall** stops repeat and brings existing custody home. Food and tool wear already spent are not refunded; an earned reward is retained. A full hut holds cargo until there is room.

For a manual departure, the Explorer must be near the hut. Use the native Recall Worker control if the Explorer has wandered away.

One expedition per colony is allowed, shared by discovery and gathering. The owner must be online in the Overworld; offline time earns no progress. Repeat stops on insufficient supplies, illness, pause/reassignment, or full output inventory and requires a manual restart. It does not require the owner to stand beside the hut.

| Resource | Per gathering trip | Tool wear |
|---|---:|---:|
| Overworld logs (8 species) | 16 | Axe: 16 |
| Saplings / mangrove propagules | 4 | None |
| Flowers (16 types) | 8 | None |
| Wheat seeds | 8 | None |
| Vines | 8 | Shears: 8 |
| Kelp | 16 | None |
| Seagrass | 8 | Shears: 8 |

All trips cost two bread. Wear is fixed, including enchanted tools. Pumpkin and melon remain discovery-only. Existing reports lacking biome evidence require rediscovery; they do not silently unlock gathering. Hut levels and native build, upgrade, assignment, inventory, and minimum-stock modules remain native; this milestone adds no level efficiency bonuses.

## Save safeguards

Payment and delivery use durable intent, native inventory save, and receipt boundaries. An ambiguous interrupted inventory transaction is held for recovery rather than replayed. This milestone does not include an automatic repair button for such ambiguity. Original worker, hut, discovery and owner identities are retained. Existing outpost data/runtime are preserved; outpost development is paused and its entry is removed from the exploration page.

## Verification record

Development tests use isolated worlds. A native gathering run executes two complete 6,000-tick trips; shorter lifecycle fixtures are explicitly journal fixtures, not evidence of elapsed time. A native courier test observes walking, pickup, warehouse delivery and minimum-stock retention; it does not fake a direct transfer.

Seven restart boundaries use separate seed/reload server processes: payment pending, payment saved natively, timer in progress, earned reward, delivery pending, delivery saved natively, and delivered receipt. Pending transactions remain held; clean transactions resume without repeated debit or reward.

Independent review found three issues; all were reproduced and corrected: reward/offer/timer mismatches now quarantine before delivery, malformed discovery receipts remain locked, and bounded history prunes only safely delivered completed trips. The reviewer confirmed these fixes without additional blockers. Lifecycle now includes an explicit refill-without-auto-restart check; separate-process restart fixtures also return legacy survey cargo without inventing discovery evidence.

Packaged preview/navigation is distinct from normal-client interaction. Normal-client testing uses no QA JAR or preview hooks.

The copied-world migration passed two native server boots (upgrade and reload), preserving colony identities, jobs, buildings, citizen inventories and block inventories. The source backup hashes remained identical. Local report: `build/copy/08282841`; source: the `20261015-090342-before-free-founders` backup, `New World`. It does not claim active simulation of every existing colony job.

| Development suite | Passing checks | Report ID |
|---|---:|---|
| Discovery | 11 | `14b557dcca374bb2911affde7714d3ea` |
| Records | 13 | `d27118b0f88d418d9e15595e1e281891` |
| Supplies | 10 | `408f3cec9f0d4c009bcd243e36cff450` |
| Gathering (two full trips) | 8 | `5b0bfca4122d495c8564097f54fd7c75` |
| Lifecycle | 16 | `fa80bcc69bab4c4ea03ffe9d430b0023` |
| Courier | 3 | `333db29a24cd404eae4a0692b56e66f8` |
| Menus | 10 | `5d19ce9ccd7e4d8ab40e0fac6b743c5c` |
| Restart with legacy return (timer) | seed/reload | `e8a1b7d2a4174cf3ac4878baf7eb20d6` |

These reports are under `build/reports/foundation/<ID>`. The seven restart fault pairs and targeted native Surveyor, menu protocol, ticket and free-founder regression reports are recorded in the local feature ledger. Repeated runs are not counted as additional scenarios.

### Packaged production-JAR checks

| Suite | Passing checks | Report ID |
|---|---:|---|
| Discovery | 11 | `12dfbe98082b480ab94765612a1a474f` |
| Gathering | 8 | `b5e2552b09504832bd0f920e4f0ba252` |
| Lifecycle | 16 | `aa7d9bf9979b461a92fb339fbe4fccc2` |
| Courier | 3 | `0575eb77457644d0a9f2c952931e75ce` |
| Menus | 10 | `3e390048de0d4d309ae0ad2db288fa64` |
| Restart: payment-pending | seed + reload | `a3b24f3d4ece40349e61f3f79d8dbb05` |
| Restart: payment-native | seed + reload | `7cf07c08f980489a80f333cbdb9cc7b3` |
| Restart: timer | seed + reload | `63e663f302ff451c87795cbe0cbaeb5b` |
| Restart: earned | seed + reload | `f041f6a30ab3437786e37e2dc4d8cd63` |
| Restart: delivery-pending | seed + reload | `3226e11f56134d91bf7b6595f6ae8d4a` |
| Restart: delivery-native | seed + reload | `12481f6c131b478c972934806856fcd2` |
| Restart: delivered | seed + reload | `6a9f5b7b3c5048608e5554d166a0c0ea` |

These reports are under `build/reports/courier-districts/packaged-server`. Each restart pair also verifies legacy discovery cargo returns without fabricated unlocks. The server harness waits for initial chunk generation to settle before shutdown; it requires a clean exit and captures thread diagnostics on a shutdown timeout.

Client navigation passes 13 exact cases, including errors surviving empty refresh replies: `build/packaged-client/26c22a5b39504085b6b6caad88c783d1`. The feedback regression first failed alone in `234128e6c02d4e6f8dc77a963ad88522`, then passed after the fix. Independent review also checked that final fix. Final two-scale rendering report `build/packaged-client/dbfb229b306d41bc98e2e205bbea73c5` passed 38 states, including locked, ready, running, full-hut, and historical exploration panels.

### Normal client interaction

Report instance: `build/packaged-client/59d869e0dd3143049e367fe787fd5100`. No QA JAR or preview hooks were loaded. It used a copied disposable native fixture world, not the user's colony. Observed checks: native hut overview; fire and rehire the same Explorer; native Recall Worker; build/repair/upgrade options and material list; hut inventory; resource scrolling and selection; paid Gather once with a decreasing timer; Findings with stored ocean evidence; expedition Recall through completed return; Save and Quit and normal game exit. The worker-distance rejection was legitimate, but its explanation disappeared on refresh; that UI defect is now covered by the regression above. Missing skill tooltip translation and stale outpost tab copy were also corrected.

This manual pass did not place a new schematic, execute an upgrade, or wait through another full five-minute gathering trip. Placement/native modules are covered by existing native hut tests; full gathering, repeat, and courier movement have automated native-world coverage. Offline local-launcher authentication/Realms warnings are expected and are not gameplay test failures.

Packaged server results used SHA-256 `143FCC52F5165DF4EDF3DDA8AA6C957F4E249CB920021B690FF5C0C0136AAAE6`. Subsequent changes are confined to client feedback, text/layout and QA assertions; server/runtime classes are unchanged.

## Installation boundary

Implementation and disposable testing initially left the installed founders build unchanged. After separate user authorization, the Exploration Hut build and five hut blueprints were installed on 2026-10-15, following a verified full instance backup. World files remained unchanged. Private worlds and backup manifests are not included in this repository.

Development output is `build/supply-dev-libs/colony-horizons-0.1.0-dev.exploration.1.jar`. The public source snapshot includes the hut pack under `blueprints/arcology-horizons`. Published alpha.2 downloads remain unchanged.

Final development JAR SHA-256: `303E4BAE4BA856E247DF20DF5C7721E53CB25292F840FEFC76961EEB97C59756`. All server/runtime class bytes were compared against the packaged server-tested artifact and are identical.

## Public source verification — 2026-10-16

The prepared GitHub checkout rebuilt the development JAR with the exact SHA-256 above. Both production and isolated QA builds passed. Artifact isolation, survival assets, menu layouts and all five blueprint geometry checks passed. The documented native `ExplorationRecords` command passed 13 cases from this checkout. Python harness syntax checks passed. This source-publication check did not repeat the entire historical test matrix above.
