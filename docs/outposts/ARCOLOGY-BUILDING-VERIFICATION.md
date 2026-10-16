# Arcology building verification matrix

Target: Minecraft 1.21.1 / MineColonies 1.1.1403 / Structurize 1.0.832. Original assets remain untouched. Generated repair pack is local `.setup/arcology-horizons`.

Asset validation is separate from worker acceptance. No row below is operationally approved until the named scenario succeeds in a native world, including access, upgrades and persistence. The earlier six-building outpost test is useful evidence for a narrower layout, not a pass for every building or upgrade here.

## Current acceptance snapshot

The complete local candidate is `.setup/arcology-horizons-complete` (305 blueprints). The older `.setup/arcology-horizons` folder contains repairs only.

- Current native asset contracts: **305 passed**; added-building/field access: **20 passed**.
- Actual native construction of all ten new crop fields: **10 passed**, including live coral, settled source water and exact work positions.
- Current added-building/field clean restart: **seed and verify passed**, preserving exact metadata, field ownership and paused staff.
- Quarries: **both full32/64-depth excavations passed**, with complete31/63-rung ladders and final floors. A native courier delivered all68coal, including ore under access ladders and wall torches. Same-worker round trips also passed.
- Active quarry clean restart: **seed and verify passed**. Both original citizen/entity identities and Mine-owned work-order IDs survived; each worker excavated at least100 additional cells in the second server process.
- Full combined production: **all ten fields passed**, with actual growth, native harvest statistics and inventory gains. Separate strict tests prove actual crimson and warped fungi; the combined fungus-bed cases also accept native roots/sprouts. Native daily work limits remain intact; playerless random growth is accelerated in the fixture, so these are not normal-speed yield measurements.
- Mounted cavalry: **mount, exit and return passed**. A tethered vanilla starter was natively trained and prepared before being untied; the guard chose its mount and followed native manual patrol points through the stable entrance with recovery disabled.
- The candidate requires the **Colony Horizons addon** as declared in pack metadata; quarry, coral, seagrass, Nether-worker and enchanting compatibility repairs are code hooks, not blueprint-only fixes.
- Remaining gaps include fluid/hazard terrain, crash recovery, broader every-role/every-level lifecycle coverage and player-facing placement/visual checks. All20 added surface/field levels also passed native interaction-range round trips in all8 rotations/mirrors (160 cases); transformed interior work and excavation remain unverified. No blanket survival-world guarantee is claimed.

## Live worker testing - current increment (2026-10-11)

The private pack repairs Plantation irrigation, walking surfaces, decorative-border exits and crop clearances (six blocks for bamboo, three for cactus/sugarcane). Live restart testing exposed another obstruction: the staggered bamboo plots enclosed the planter on all four sides once planted. Ground-level plots now form rows with continuous walking aisles. Bamboo capacity remains 4/8/12/16/20 across levels; raised plots and the outer frame remain intact. An unsuccessful extra border-opening experiment was reverted. Bamboo roof openings must remain open when connecting or stacking modules.

The generated pack changes35 blueprints;248 remain byte-identical to the source. All301 original files remain untouched, and two generations produce303 identical files. The chicken ladder repair passed the strict bounded level5 worker/containment test; current full-asset283 and navigation238 reruns passed with successful runner exits.

A pinned native Nether worker defect was reproduced: after travel replaces the live AI, the period trip limit blocks the replacement AI from finishing the current expedition. `NetherWorkerResumeMixin` permits only pending expedition work, retaining the normal limit on new trips. The native limit uses sleep/wake periods, not a daily reset. This fix requires the Colony Horizons addon; blueprints alone cannot fix scheduling.

| Check | Result | Scope |

|---|---:|---|

| Repair contracts | 74 passed | Irrigation, crop height, planted bamboo aisles, exits, markers, materials, source preservation, crop capacity, stable Mixer containment and Academy dummy heads and exact chicken ladder repair scope. |

| All blueprints | 283 passed | Native assets/metadata, crop survival and harvest-height clearance. |

| All hut access | 238 passed | Level-specific approach/exit routes within native interaction distance, without teleport recovery. |

| Priority lifecycle: construction | 15 passed | Real builder constructs level one, then upgrades through five for Mine, Nether Mine and Plantation. |

| Priority lifecycle: first work | 3 passed | Shaft excavation/tool wear; planting/harvesting all three legacy crops; Nether travel, return and loot deposit. |

| Priority lifecycle: restart | 3 passed | Same saved workers/huts/home assignments resume fresh excavation, crop harvesting and a new Nether expedition with fresh loot deposit. |

| Civic services | 6 passed | Library study, school teaching, hospital treatment, university progress, timed Tavern visitor and Mystical Site happiness effect. Research completion and other levels excluded. |

| Animal work | 8 passed | Five breeding roles, natural egg pickup, milk and wool. Strict bounded level5 chicken containment passed separately. |

| Gathering | 4 passed | Farmer till/plant/harvest (QA matures observed plantings), fisherman native bobber/fish, florist natural flower growth/harvest, beekeeper breeding. |

| Processing | 3 passed | Level-five Crusher gravel, Concrete Mixer white concrete with pickaxe wear/statistic, and Smeltery iron with cold-start/fuel/collection. |

| Mixer water stability | 5 passed | Actual levels1-5 native registration, water settling and refill after ordinary obstruction removal. |

| Sifting | 1 passed | Native gravel/string-mesh consumption, wear, random loot and statistics at level5. |

| Defense/training | 3 passed | Archery hits/XP, Academy dummy/sword-wear/XP and knight-attributed stationary-husk kill. |

| Alchemy and enchanter visits | 2 passed | Three awkward-to-swiftness potions and native enchanter station visit/book use/Mana gain. Ancient-tome production/cost is covered separately below. |

| Ancient-tome enchanting | 1 passed | Native Quick Charge III enchanted-book production and exact Mana cost3 (60 to57), after repairing the pinned upstream stored-enchantment cost lookup. |
| Restaurant service | 1 passed | Hungry customer arrives, cook serves prepared bread, customer eats and gains saturation; self-service rejected. |

| Barracks | 3 passed | Four native embedded-tower links, knight home/work assignment and two duty-point walks. Construction hooks are supplied; timed builder construction excluded. |

| Bee products | 2 passed | Natural nectar/hive accumulation from zero honey, then native honey-bottle/honeycomb harvest. |

| Graveyard recovery | 1 passed | Real fixture death, exact seven-emerald native grave, undertaker recovery and digging/resurrection statistics. Named burial excluded. |

| Food production | 2 passed | Level-five Baker bakes four bread from supplied dough; Kitchen cooks four steak from supplied raw beef, with native furnace operation and collection. |

| Compost production | 1 passed | Level-five Composter fetches wheat, fills registered barrels, waits for native processing and harvests six compost. |

| Furnace production | 2 passed | Level-five Glassblower smelts four glass; Stone Smeltery smelts four stone. Empty/cold start, fuel consumption and worker collection required. |

| Artisan production and deliveries | 4 passed | Level-five Blacksmith iron pickaxe, Mechanic minecart, Dyer red wool; courier delivers all three without loss or duplication. |

| Manufacturing and local deliveries | 4 passed | Level-five Sawmill crafts 6 oak slabs, Stonemason 4 stone bricks, Fletcher 4 arrows; courier carries all products to the requesting Town Hall without loss or duplication. |

| Six-hut operations | 5 passed | Native construction of six huts, workplace/bed access, two foresters chopping/collecting/replanting, local movement boundary. |

| Six-hut persistence regression | 3 phases passed | Earlier clean-save ownership, claims, assignments, housing, inventory and pause-state checks. |

| Addon build | Passed | No separate JUnit sources; native suites provide worker evidence. |

| Repeat generation | 303 identical files | 301 original hashes unchanged; 35 repaired and 248 untouched blueprints. |

Every accepted native runner listed in the table exited successfully. Current asset (283 cases) and navigation (238 cases) runs match all283 blueprints in the35-repair pack, including Mixer, Combat Academy and chicken ladder repairs. Historical lifecycle evidence matches its tested priority-role assets; the index retains the unrelated blueprint differences for that older snapshot. The operations suite's 15 referenced builder/forester/residence level assets also match the final pack. See the [verification index](evidence/arcology-live-verification.json) for run IDs.

The manufacturing and artisan suites validate their recipes against Minecraft's loaded recipes, supplies exact ingredients in workplace inventories, and observes native consumption and crafting statistics. Outputs start absent. The courier starts after production, carries every product, and deposits the exact requested quantities; source workers/huts, courier and warehouse retain no duplicate products. Worker identity and movement are sampled every 20 ticks with recovery disabled. This verifies local delivery from workplaces, not warehouse rack throughput or ingredient restocking. No blueprint or gameplay changes were needed for these production and delivery cases. Furnace tests separately validate loaded smelting recipes and require empty, unlit furnaces at the start, observed burning, input/fuel consumption and collection of every output with none left in furnaces. Both furnace workplaces use supplied hut inputs/fuel and prebuilt level-five structures.

The food suite waits for the bakery's native automatic recipe initialization (bread is excluded from manual recipe teaching). It supplies dough/raw beef and coal; workers receive golden carrots so their food cannot masquerade as output. These cases do not establish dough production. Restaurant meal service is covered separately by the accepted prepared-bread service test. The Composter starts with empty barrels, supplied wheat and the normal allowed-input setting; it must fill and process a barrel without QA timer manipulation, then record native consumption/harvest statistics and collect compost. Dirt mode and every barrel are not covered.

Tests supply tools, food, research, recipe and construction inputs. Lifecycle also supplies medical care; it does not certify unattended survival or hospital service. Support Builder/Town Hall/residence start prebuilt. The 15 target construction/upgrades use actual builder orders; workers are assigned after level five, so retention and operation through intermediate upgrades remain untested. A clean shutdown/restart then reuses those same three workers, buildings and native home assignments without fixture rebuilding, reassignment or respawning. Day/night and the native expedition cooldown remain active.

The crop fixture matures one stem per crop. First-cycle planting is observed; reload checks fresh harvesting/collection from existing stems, not fresh planting or natural growth timing. Raw archived reload output predates this wording correction; the archive includes that clarification. Navigation starts at prepared approaches and does not certify every interior workstation or stacked module connection. Miner branch production/hauling and mid-expedition restart remain untested.

Evidence: [strict chicken containment](evidence/arcology-native-chicken-containment.json), [original open-gate failure](evidence/arcology-chicken-containment-before.json), [graveyard recovery](evidence/arcology-native-graveyard.json), [enchanting](evidence/arcology-native-enchanting.json), [enchanting cost failure](evidence/arcology-enchanting-before.json), [food production](evidence/arcology-native-food.json), [compost production](evidence/arcology-native-compost.json), [furnace production](evidence/arcology-native-smelting.json), [artisan production and deliveries](evidence/arcology-native-artisans.json), [manufacturing and deliveries](evidence/arcology-native-crafting.json), [lifecycle and restart](evidence/arcology-native-lifecycle.json), [operations](evidence/arcology-native-operations.json), [native worker behavior](evidence/arcology-native-behavior.json), [native upgrades](evidence/arcology-native-upgrades.json), [navigation](evidence/arcology-native-navigation.json), [assets](evidence/arcology-native-assets.json), [earlier six-hut persistence](evidence/arcology-native-restart.json), [bamboo failure diagnosis](evidence/arcology-lifecycle-diagnosis.txt).

Independent review identified stale expedition-log evidence and native entity-readiness timing; both were corrected before final acceptance. Final scoped review found no material issue in the row geometry, preserved crop capacity or native housing checks.

These results do not certify all 48 building roles. The matrix below retains the remaining full acceptance scenarios; partial coverage above does not close every scenario.

## Accepted extended checks and unresolved work (2026-10-11)

All scoped extended suites listed above have accepted native reports and successful runner exits. Their individual fixture provisions and exclusions remain in the archived reports and verification index; a passing sample does not certify every recipe, level, upgrade, restart or full building role.

Concrete Mixer: all five original levels had water level1 (fluid amount7), above the native registration limit of5. The private repair retains the three-cell basin, isolates one source and changes exactly four cells per level. All five native settling/refill checks passed; level5 also produced four white concrete with tool wear and native statistics. Combat Academy: only plain-pumpkin heads directly above hay were converted to carved heads facing the practice aisle. Level5 training and all-five-level native dummy registration passed. Current independent contracts:74 PASS; repeated generation:303 identical files,301 original files unchanged,35 repaired and248 untouched blueprints. Current Full283 (d93f92f3b42d479eb28c319d924c5a57) and Navigation238 (78cecf05d2734c69bd7ed3cafce6bbf1) passed with exit0 against the35-repair pack. The chicken ladder repair also passed strict level5 native acceptance. The isolated Stable extension is a separate candidate and is not included in these283-asset results.

Chicken escape is **resolved for the bounded level5 reproduction**. Original open-gate run e583bedccc41439190a6021a92ae7e1c failed after a chicken stayed outside native hut bounds for220ticks. The six-cell repair replaces the gate with a two-block oak-plank barrier and two ladders on each face at all five levels. Strict run d890fbbaebee4d65837c8ab92350ac4b passed with exit0 after24000ticks, native breeding and natural egg collection, loaded tracked birds and at least two living adults; no bird stayed outside native hut bounds for over200ticks. Slaughter exemptions require fatal damage-source worker attribution, last-attacker identity and inside-pen location. This certifies the actual level5 bounded soak, not permanent containment or other-level worker access.

Ancient-tome enchanting and exact Mana-cost acceptance passed in run b386e780799645f29aea33408c1d67ef. RED run eb298f6e326644289012015863b48089 produced Quick Charge III but charged zero Mana (60 to60 rather than cost3). The addon repairs the pinned upstream stored-enchantment component lookup; GREEN produced the book and debited Mana60 to57. The separate Magic suite establishes station visits and Mana acquisition. Graveyard run2ca7a292a36e43549a8cc1aeb2f17933 now passes after correcting the fixture to respect native per-hit damage caps; it requires actual death and verifies seven emeralds in the native grave before recovery. No health, death or grave state is injected.

## Isolated Stable extension (2026-10-11)

The separate .setup/arcology-stable-testpack contains288 blueprints: the main283 plus five newly generated Stable levels. Five independent static tests passed (.setup/stable-static-tests.log). Native run d60e136cfa8c4b468891725462c66baa passed7 cases and exited0; all288 snapshot hashes match the isolated pack. The main repair pack stays283 and its accepted Full/Navigation results are unchanged.

All five Stable levels registered exact native stall tags, bed heads and patrol destinations. At level5, the stablemaster converted one supplied ordinary untamed adult vanilla horse into a cavalry horse with native home association, statistics and XP, and completed a worker-leashed stall delivery with roundup statistics. See [native Stable evidence](evidence/arcology-native-stable.json) and [design and remaining checks](ARCOLOGY-STABLE-DESIGN.md).

Those seven cases are prebuilt placement and scoped worker tests. A separate native builder run2a9865dc5405465390bb315f960cd72b passed all five stages, actual level1 construction then upgrades through5, with exit0. Its isolated .setup/arcology-additions-testpack snapshot has291 assets; all291 match the current additions pack. The same stablemaster, assigned after level1 and paused through upgrades, retained UUID/hut/module membership. Exactly17 sentinel shards were conserved across worker and own stable/racks. Native pause legitimately deposits inventory; fixed worker-slot retention was an invalid earlier expectation, not a gameplay defect. See [builder evidence](evidence/arcology-native-stable-build.json) and [native deposit diagnosis](evidence/arcology-stable-paused-deposit.json). Active stablemaster work during construction and worker-carried inventory are not certified. Breeding, maintenance, cavalry assignment, mounted patrol/return, rotation, restart, every-stall access and long-term containment remain uncertified.

Reproduce this isolated scope:

    python tools/Test-ArcologyStable.py
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyStableBuild -ArcologyPath "./.setup/arcology-additions-testpack" -TimeoutSeconds 1800
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyStable -ArcologyPath "./.setup/arcology-stable-testpack" -TimeoutSeconds 1200

## Earlier asset-only results

### Verified repair increment - 2026-10-10

- 24/24 independent repair/preservation contracts passed.

- 283/283 native asset cases passed; runner exit 0, run `16a60463fe8549c49e9cdd32065dd738`.

- Final Gradle build passed (no separate JUnit suite).

- Repeated generation produced 303 identical output files.

- Independent review findings about expected hut identity and immutable test snapshots were corrected. Follow-up review found no additional blocking issues in this increment.

Evidence: [repair contracts](evidence/arcology-repair-contracts.json), [native asset cases](evidence/arcology-native-assets.json), [reproducibility](evidence/arcology-reproducibility.json). These results do not close the worker/service scenarios below. The later live increment above extends these historical results; other professions, intermediate-upgrade operation, connected layouts and missing-building additions remain unfinished.

| Existing building | Levels present | Worker/service acceptance still required |

|---|---|---|

| alchemist | 1-5 | Level5 three awkward potions to three swiftness potions passed: native brewing, fuel/ingredient consumption, collection/statistics. Other potions/levels, harvesting, upgrades/restart remain. |

| archery | 1-5 | Level5 registered stand/target, worker-owned arrow, hits and gained XP passed. Other levels, promotion, upgrades/restart remain. |

| baker | 1-5 | Level5 dough-to-bread baking/collection passed using the native automatic recipe. Dough production, other recipes/levels, delivery, upgrades/restart remain. |

| barracks | 1-5 | Four level5 embedded tower links passed via native placement/registration hooks; exact forward/reverse relationships checked. Full timed builder construction, other levels and restart remain. |

| barrackstower | 1-5 | Level5 native knight assignment also assigned home, five beds/capacity verified, and same guard walked two duty points. Sleep, full occupancy, other levels/upgrades/restart remain. |

| beekeeper | 1-5 | Level5 native breeding plus natural nectar/hive filling and honey bottle/honeycomb harvesting passed. Harvest tests start with zero honey, use blueprint flowers and native hive assignments. Other levels/upgrades/restart remain. |

| blacksmith | 1-5 | Level5 iron-pickaxe recipe and outgoing courier delivery passed. Other recipes/levels, upgrades/restart remain. |

| builder | 1-5 | Native construction/upgrades1-5 passed for Mine, Nether Mine and Plantation with supplied materials. Other roles, repair/deconstruction and delivered-material logistics remain. |

| chickenherder | 1-5 | Level5 native breeding and natural egg collection passed during strict24000-tick containment soak after ladder repair. No bird stayed outside native hut bounds for over200ticks. Permanent containment, other-level worker behavior, delivery, upgrades/restart and full slaughter acceptance remain. |

| combatacademy | 1-5 | Repaired all5 plain-pumpkin dummy heads. Level5 native dummy training, sword wear and XP passed. All-five-level native registration passed; sparring, upgrades/restart remain. |

| composter | 1-5 | Level5 wheat pickup, barrel filling, native processing and compost harvest passed. Dirt mode, all-barrel coverage, other levels, delivery, upgrades/restart remain. |

| concretemixer | 1-5 | Level5 four white concrete, pickaxe wear and native statistics passed; all five levels passed water settling/refill. Other recipes, production at other levels, upgrades/restart remain. |

| cook | 1-5 | Level5 native arrival, cook service/statistic and customer eating/saturation gain passed using supplied prepared bread; self-service fallback rejected. Raw cooking, menus, other levels/upgrades/restart remain. |

| cowboy | 1-5 | Level5 native breeding passed. Configured milk collection passed. Slaughter, other levels, delivery, upgrades/restart remain. |

| crusher | 1-5 | Level5 four gravel from native crushing with input consumption and collection passed. Other recipes/levels, upgrades/restart remain. |

| deliveryman | 1-5 | Level5 native courier delivery of six manufactured products passed without loss/duplication. Ingredient restocking, other levels, upgrades/restart remain. |

| dyer | 1-5 | Level5 red-wool recipe and outgoing courier delivery passed. Other recipes/levels, upgrades/restart remain. |

| enchanter | 1-5 | Level5 native configured-station visit, book consumption and Mana acquisition passed. Ancient-tome Quick Charge III book production and exact Mana debit60 to57 passed after addon cost repair. Other enchantments/levels/upgrades/restart remain. |

| farmer | 1-5 | Level5 native tilling, planting and wheat harvest passed with natural day scheduling; QA matured observed planted crops. Natural growth timing, other crops/levels, upgrades/restart remain. |

| fisherman | 1-5 | Level5 native water discovery, bobber and collected fish passed. Other waters/levels, delivery, upgrades/restart remain. |

| fletcher | 1-5 | Level5 four-arrow recipe and courier delivery passed. Other recipes/levels, upgrades/restart remain. |

| florist | 1-5 | Level5 native compost use, natural flower growth and harvested poppies/statistics passed. Other flowers/levels, delivery, upgrades/restart remain. |

| glassblower | 1-5 | Level5 sand-to-glass furnace production/collection passed. Other recipes/levels, delivery, upgrades/restart remain. |

| graveyard | 1-5 | Level5 actual death, native grave holding exactly seven emeralds, undertaker recovery and native statistics passed. Named burial, resurrection probability, other levels/upgrades/restart remain. |

| guardtower | 1-5 | Level5 native knight targeting, attributed damage/kill and statistics passed against stationary full-health husk. Moving pursuit, raids, other levels, upgrades/restart remain. |

| hospital | 1-5 | Level5 native measles patient registration, treatment statistic and recovery passed; cure supplies in hospital. Other diseases/levels, upgrades/restart remain. |

| kitchen | 1-5 | Level5 raw-beef-to-steak cooking/collection passed. Other recipes/levels, delivery, upgrades/restart remain. |

| library | 1-5 | Level5 native paper pickup, study statistic and intelligence XP passed. Other levels, delivery, upgrades/restart remain. |

| lumberjack | 1-5 | Two native foresters chopped, collected and replanted in the six-hut operations fixture. All levels, upgrades and production after restart remain. |

| mechanic | 1-5 | Level5 minecart recipe and outgoing courier delivery passed. Other recipes/levels, upgrades/restart remain. |

| miner | 1-5 | Construction/upgrades1-5, level5 native shaft excavation/tool wear and fresh excavation by the same worker after restart passed. Branch mining, hauling and intermediate-upgrade worker retention remain. |

| mysticalsite | 1-5 | Level5 native happiness factor1.0 to2.5 passed. Resurrection synergy, other levels and restart remain. |

| netherworker | 1-5 | Construction/upgrades1-5, native travel/return/loot deposit and fresh same-worker expedition after restart passed with addon resume repair. Mid-expedition restart and intermediate-upgrade operation remain. |

| plantation | 1-5 | Construction/upgrades1-5, level5 native planting/harvest of all three crops and fresh harvest after restart passed. QA matures stems; natural growth timing, fresh planting after reload and intermediate-upgrade operation remain. |

| rabbithutch | 1-5 | Level5 native breeding passed. Slaughter, other levels, delivery, upgrades/restart remain. |

| residence | 1-5 | Native home/bed assignments and clean-save persistence passed in scoped fixtures; Barracks knight housing also passed. Sleeping, full occupancy and all-level upgrade retention remain. |

| sawmill | 1-5 | Level5 six oak slabs and courier delivery passed. Other recipes/levels, upgrades/restart remain. |

| school | 1-5 | Level5 native teacher lesson, child seating and pupil intelligence XP passed. Other levels, upgrades/restart remain. |

| shepherd | 1-5 | Level5 native breeding passed. Shearing, worn shears and collected wool passed. Slaughter, other levels, delivery, upgrades/restart remain. |

| sifter | 1-5 | Level5 native gravel/string-mesh consumption, tool wear, daily count, loot and statistics passed. Other meshes/inputs/levels, caps, delivery, upgrades/restart remain. |

| smeltery | 1-5 | Level5 four iron ingots passed with cold-start furnace, fuel consumption and worker collection. Other ores/levels, upgrades/restart remain. |

| stonemason | 1-5 | Level5 four stone bricks and courier delivery passed. Other recipes/levels, upgrades/restart remain. |

| stonesmeltery | 1-5 | Level5 cobblestone-to-stone furnace production/collection passed. Other recipes/levels, delivery, upgrades/restart remain. |

| swineherder | 1-5 | Level5 native breeding passed. Slaughter, other levels, delivery, upgrades/restart remain. |

| tavern | 1-3 | Actual level3 timed living visitor creation and native visitor statistic passed. Paid recruitment, music, other levels/upgrades/restart remain. |

| townhall | 1-5 | Scoped native ownership/claims and citizen assignments persisted across clean restart. Full management/census workflows, all levels and upgrade retention remain. |

| university | 1-5 | Level5 native progress from0 to5 passed; loaded research started with costs/prerequisites bypassed. Completion/effects, other levels and restart remain. |

| warehouse | 1-5 | Register racks, store/retrieve items, serve courier requests. |

Also validate all 45 non-building assets: supplycamp/ship deployment, crop-field linkage, and physical connections for paths, roofs, frames and walls. All 283 blueprints are included in native asset inspection.

## Repairs implemented; remaining operational checks listed above

Mine levels 3-5: restore ladder/cobble markers at matching physical blocks. Nether Mine levels 1-5: retain one in-bounds portal/vault marker pair. Plantation level 3: restore crop markers on matching substrates. Levels 4-5: move one cactus plot away from a solid outer column, preserving crop counts and the outer frame. Archery levels 1-5: replace decorative hay targets with native target blocks and restore every designed stand (1/2/3/4/6), removing stale out-of-bounds positions. Kitchen level 5: replace unavailable external pipe. Wooden walkway: replace unobtainable petrified slabs.

Current preservation checks verify all original hashes,283 typed NBT roundtrips and248 unmodified blueprints.

## Reproduce the current checks

The ChickenContainment command repeats the bounded level5 acceptance check. Use Java 21 and the project Gradle wrapper. `Build-ArcologyPack.py` and `Test-ArcologyPack.py` require Python 3 and use only its standard library.

```powershell

python tools/Build-ArcologyPack.py --source "blueprints/arcology-horizons" --output .setup/arcology-horizons

python tools/Test-ArcologyPack.py --pack .setup/arcology-horizons --source "blueprints/arcology-horizons" --output .setup/arcology-repairs-green.json

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyFull -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyNavigation -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyUpgrades -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyBehavior -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyFood -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyCompost -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologySmelting -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyArtisans -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyCrafting -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyLifecycle -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite Operations -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite NativeRestart -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900

powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyProcessors -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyAnimals -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyGathering -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyCivic -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyCivicGrave -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologySifter -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyDefense -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyMagic -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyMixerWater -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyRestaurant -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyBarracks -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyBeeProducts -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyEnchanting -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite ArcologyChickenContainment -ArcologyPath "./.setup/arcology-horizons" -TimeoutSeconds 900
./gradlew.bat build

```

The native suite uses a separate disposable world and copies the pack before inspection. Its expected 238 hut identities come from the committed original audit, not from the pack under test. All 283 assets are decoded, checked for available blocks/properties/mods, and rotated/mirrored through Structurize. Hut levels and saved metadata are roundtripped. Mine markers, Nether Mine portal formation, Plantation field registration/crop survival/free planting cells, and Archery stand/target lookup receive additional checks. These are prerequisites for operation, not completed end-to-end worker tests.

Wait for the runner's explicit PASS and successful process exit. Native GameTest assertions finish before the world-save step, which can take several minutes on this machine. `gradlew build` currently has no separate JUnit source suite; native GameTests and the Python contracts supply the test evidence.

The generated pack is for the pinned 1.21.1 addon environment. It is not installed over the original 1.20.1 pack or into the player's existing world. Original third-party blueprint assets and generated copies remain local and are not redistributed by this repository.

## Test-runner reliability

Verbose Gradle output is redirected directly to files by cmd, avoiding a Windows PowerShell output-pipe deadlock during WaitForExit. The runner still checks the process exit code and the complete expected case manifest.

Only explicitly enabled foundation GameTestServer instances batch chunk writes. Minecraft still waits for pending writes and performs its normal final disk flush. This tests clean-shutdown persistence, not crash-time durability; ordinary integrated/dedicated play worlds retain their normal saving behavior. The native restart regression passed all three phases (seed, verify, verify_paused) with runner exit 0. Its fixture now reads the selected pack name from pack.json, allowing the private Arcology Horizons name without weakening persistence assertions. This verifies clean-save persistence of the six-hut fixture, not every worker production cycle.


## Complete candidate, installation and remaining acceptance

The separate local candidate is .setup/arcology-horizons-complete:305 blueprints (283 existing plus22 additions). Four assembly tests verify exact inventory, deterministic generation, unchanged originals, mode-change protection and the required Colony Horizons dependency. The97 complete-pack contracts and final clean build passed. There is no separate JUnit suite; native GameTests and Python contracts provide the evidence.

Use Java21, Minecraft1.21.1, NeoForge21.1.250, MineColonies1.1.1403 and Structurize1.0.832, their required dependencies, and the built Colony Horizons addon. Copy the generated directory as a separate pack under a test instance's blueprints folder, and place the addon jar in that instance's mods folder. Select Arcology Horizons in the build tool. Do not replace the original Arcology folder or use these1.21.1 files in the existing1.20.1 instance. No installation or player-world modification was performed by this audit.

The authoritative evidence index is [arcology-live-verification.json](evidence/arcology-live-verification.json). It distinguishes historical snapshots from the current305 blueprints.

| Check | Accepted scope |
|---|---|
| Pack discovery |Native automatic server load; all305 blueprint files browsable through30 categories |
| Native assets and markers |305 assets; original238 hut routes;20 added surface/field routes |
| Real builder work | Stable construction/upgrades1-5; Gatehouse1-3; both Quarry surfaces; all10 field constructions |
| Crop production |All10 native field outputs, plus separate strict crimson/warped fungus proofs |
| Quarry work |Full32/64-depth excavation,31/63 ladders, final floors, native courier68coal including access-cell ore |
| Quarry enclosed fluids |Both quarry variants seal separate water/lava sources and then excavate12 fresh cells two layers below |
| Quarry pools and resupply |Connected3x3 water/lava pools; native shortage waiting and hut-restock resumption for both sizes |
| Quarry restart |Original workers and work orders continue fresh excavation in a second server process |
| Cavalry |Native conversion/readiness prerequisite, guard mounting, exterior patrol and mounted return |
| Transformations |160 interaction-range round trips:20 added surface/field levels x8 rotations/mirrors |
| Connected layouts |Three routes through touching timber/stone and stacked modules with disclosed external stairs |
| Added metadata restart |All10 field types/ownership, new hut metadata and same paused staff persist |

Raised aquatic fields and temporary construction-only coral protection resolve builder traps and premature coral drying. Quarry repairs resolve missing access rungs, unsupported cached work positions, blueprint-air erasure and ore replacement under access blocks. Sea-pickle fixtures require starter pickles; native daily scheduling remains unchanged. Earlier failed runs are diagnostics, not current acceptance.

Still unverified: the broader player-facing placement/preview matrix (scoped client checks below), visual fit across broader connected layouts, unbounded aquifers/lava lakes and full-depth wet excavation, crash recovery, transformed mounted/interior work and excavation, breeding/all-stall behavior, and every job at every intermediate level through every lifecycle state. The per-role matrix above limits each claim.

Reproduce the pack with tools/Build-ArcologyPack.py --include-extensions; run native cases with tools/Test-OutpostFoundation.ps1 -Suite <name> -ArcologyPath <candidate> -TimeoutSeconds 900. New suites are ArcologyDiscovery, ArcologyQuarryHazards, ArcologyCavalry, ArcologyQuarryRestart, ArcologyTransformedNavigation and stronger ArcologyQuarryRules/ArcologyQuarryDepth. On this host, the isolated Gradle user cache .setup/gradle-arcology-recovery avoids a stale native-filesystem daemon; the runner disables file watching and uses .setup/foundation-gradle-cache.

### Native pack discovery follow-up

ArcologyDiscovery passed3 cases with runner exit0. The server automatically selected the isolated candidate with its dependencies installed, and native category/blueprint enumeration reached all305 distinct files across30 categories, including the added buildings and fields. This covers backend discovery only; player-facing grouping, ghost previews and placement clicks still need client testing. See [discovery evidence](evidence/arcology-native-pack-discovery.json).

### Enclosed quarry fluid follow-up

ArcologyQuarryHazards passed4 cases with runner exit0. Both native workers replaced enclosed water and lava sources with their cobblestone fill, then cleared at least12 cells that were still solid after both seals, two layers below the sources. Same worker identities and assignments were retained; recovery was disabled, and no burning/lava contact was observed. Supplies and food were provided; the fixture did not edit terrain or restore health after work began. No gameplay or blueprint repair was needed. This does not certify open aquifers, lava lakes, fill-material shortages or full-depth wet excavation. See [hazard evidence](evidence/arcology-native-quarry-hazards.json).


### Connected pools and fill-supply repair

The next increment reproduced a missing supply check in native quarrier mining: with no fill block, the method continued instead of requesting materials and waiting. ArcologyQuarryFluidSupplyMixin now counts the adjacent fluid cells and uses the native material request/wait path before allowing mining. It applies only to the two exact Arcology Horizons quarry shaft paths.

Accepted runner exits0: ArcologyQuarryShortage2 cases (zero/one/two fill, native matching request, dry and unrelated-pack controls); ArcologyQuarryPools2 cases (nine connected water sources for Simple and lava sources for Medium, open headspace); ArcologyQuarryHazards4 regression cases; ArcologyQuarryRestock2 live cases. The live test withdraws worker/hut fill just before a fluid encounter, observes native NEEDS_ITEM and an unchanged barrier for200 ticks, then adds supplies only to the Mine hut. The same native worker seals the hazards and excavates at least12 previously solid lower cells afterward. No worker teleport, post-start terrain edits or health restoration. Initial tools and food remain supplied fixture inputs.

The pool test is bounded3x3 terrain, not proof for an entire lake or aquifer. Hut resupply is performed by the fixture; autonomous courier restocking is not certified. Blueprints were unchanged. See the evidence index for the failing reproduction and accepted results.

Run with the existing foundation runner using -Suite ArcologyQuarryShortage, ArcologyQuarryPools or ArcologyQuarryRestock and the complete candidate path.

### Pending client acceptance

The separate Windows computer-use runtime is now available. Initial client smoke coverage is recorded below; the broader placement matrix remains open. In a disposable client world using the pinned mod versions and separate Arcology Horizons pack:

1. Select the pack and find Stable, Gatehouse, Simple/Medium Quarry and aquatic plantation fields in the build tool.
2. Inspect the ghost preview in all rotations/mirrors, checking hut position, doors, ladders and field water containment.
3. Place a surface design on flat terrain and beside an existing Arcology module; verify the confirmed work order matches the preview position and orientation.
4. Request an upgrade and check the new preview remains aligned with the original hut and connected entrances.
5. Record screenshots and actual pass/fail observations before accepting client placement or broader visual fit.

### External reservoir and native client checks (2026-10-11)

ArcologyQuarryInflow passed both cases with runner exit0. Retained external 3x3 reservoir with a three-wide breach: native inflow observed at Y13, three liner cells restored, monitored Y13 interior stayed dry, and 12 initially solid Y11 cells subsequently excavated. All nine external sources remain; same worker, recovery disabled. Bounded reservoir; not full-depth wet excavation. Raw report wording "shaft interior" refers specifically to the monitored Y13 cells. [Runtime evidence](evidence/arcology-native-quarry-inflow.json).

In a fresh isolated creative superflat world, the native build tool selected Arcology Horizons, displayed Stable levels1-5 and Gatehouse in Military, rendered Stable1 and Stable5, and rotated Stable5 once. Stable1 was placed through Schematic Paste and remained after cancelling the preview. This does not establish survival work orders, colony registration, mirror behavior, connected fit, or the complete upgrade-preview matrix. [Rotated preview](evidence/arcology-client-stable5-rotated.png) and [placed structure](evidence/arcology-client-stable1-pasted.png) are limited ground-level screenshots. Client closed normally with exit0.

Final build passed (exit0); Gradle test is NO-SOURCE. No new pack/gameplay changes were required by these checks.

### Mirrored client placement and survival prerequisite (2026-10-11)

Reopened the same isolated test save and observed the previously pasted Stable1. Toggling Stable1 mirroring moved the preview footprint and entrance to opposite sides of the same hut anchor. After re-enabling mirror, Schematic Paste placed the mirrored blocks; they remained after cancelling the preview. Evidence: [mirrored preview](evidence/arcology-client-stable1-mirrored-preview.png), [unmirrored comparison](evidence/arcology-client-stable1-unmirrored-preview.png), and [mirrored placement](evidence/arcology-client-stable1-mirrored-pasted.png). This covers one Stable level, not every rotation, connected fit or upgrades.

Used the native Town Hall item/build tool, selected Arcology Horizons Town Hall, and chose Assign to Builder in creative. The hut block appeared; signing the native covenant registered a colony and opened the level-0 Town Hall menu. After switching to survival, Build Options displayed the Arcology Horizons material list. Build Building correctly rejected the request because there was no level-1-or-higher Builder hut. Evidence: [survival material list](evidence/arcology-client-townhall-survival-materials.png) and [builder prerequisite](evidence/arcology-client-townhall-builder-prerequisite.png). The latter screenshot was taken after the warning faded; the exact warning was directly observed during the click and is retained in the [client log excerpt](evidence/arcology-client-survival-prerequisite.txt).

A slime killed the test player after switching to survival; the player respawned, difficulty was changed to Peaceful, and the same Town Hall was revisited. This was fixture setup, not an Arcology defect. No successful queued survival order or completed player-requested construction is claimed. Next client checks: establish a Builder hut, submit and inspect an accepted survival work order, then verify connected placement and aligned upgrade previews. No code or blueprint changes were needed. Client left running after inspection; no fresh clean-shutdown or build claim for this documentation-only follow-up.
### Automated regression pass (2026-10-11)

At the player's request, functional acceptance uses automated fixtures rather than manually progressing a colony. Manual testing is reserved for rendering, visual alignment, readability, and input behavior that needs a client. Builder setup, work orders, navigation, production, supply rules, and persistence use native server tests. The older pending-client list is not a reason to repeat those functional checks manually.

Fresh automated results: **48 passing cases across six suites**, all runners exited 0. All six test snapshots match the current candidate's 305 blueprints and pack.json. No implementation or blueprint changes were required, and no manual testing was performed in this pass. These are reruns of existing regressions, not new coverage. [Complete results and scope](evidence/arcology-automated-regression-2026-10-11.json).

| Suite | Passing cases | Verified scope |
|---|---:|---|
| ArcologyLifecycle | 21 | Mine, Nether Mine, Plantation construction/upgrades 1-5, then tested worker operations before and after clean restart |
| ArcologyStableBuild | 5 | Native construction/upgrades 1-5, registered beds/stalls, paused worker identity and item conservation |
| ArcologyGatehouseBuild | 3 | Native construction/upgrades 1-3, guard markers, beds and connection registration |
| ArcologyConnectedLayout | 3 | Native walking through adjoining timber/stone modules and stacked modules with supplied external stairs |
| Supply | 12 | Protected depot delivery, capacity, availability, reserves, permissions, item components and conservation |
| SupplyRestart | 4 | Cargo/progress/pause persistence through seed, verify, paused and final server runs; delivery exactly once |

These results do not certify crash recovery, autonomous material acquisition, physical long-distance courier safety, every transformed production cycle, or visual connected fit. Construction fixtures supply a level-5 support builder, research and materials. Plantation reload checks mature existing crops as disclosed in the raw evidence.

Reproduce each row with Java 21 and `powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-OutpostFoundation.ps1 -Suite <suite> -ArcologyPath "./.setup/arcology-horizons-complete" -TimeoutSeconds 900`. This host used `GRADLE_USER_HOME=.setup/gradle-arcology-recovery`. Run suites sequentially.