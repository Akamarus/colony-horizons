# Safe supply resumption — 2026-10-14

The published alpha.2 and the player's installed pack were not changed.
Work resumed in the existing development checkout under the approved safe-hut
supply plan. The existing design uses outpost districts within one parent colony;
same-colony route validation is intentional, not a reason to create independent
colonies or remove ownership checks.

## Changes

Added three bridge regression cases and registered them in the runner:

- Two outgoing routes share one real inventory and preserve the highest required
  reserve. Both deliveries together conserve stock.
- Two incoming routes share one destination and count in-flight stock once,
  avoiding target oversupply.
- A changed destination rejects the staged batch before any source writes,
  preserving both the original source and the intervening destination change.

No production behavior changes were needed for these cases.

## Fresh results

30 required case/phase results passed:

| Suite | Cases | Report run |
| --- | ---: | --- |
| SupplyNative | 8 | 5fad7b73cc404f76ad9762e49c8acd2a |
| Supply | 12 | 90ae76de581140b7b55566100292e4c9 |
| SupplyBridge (expanded) | 7 | b69d2ae122254dfaa6dcd6785b3a5127 |
| SupplyNativeRestart | 3 phases | 8b9b553a3c3b4b55b8fb631f583cc4b0 |

Reports are under build/reports/foundation/<run>/<phase>. The unexpanded bridge
suite also passed before changes. Java 21 and the existing recovery Gradle cache
were used. Each runner exited successfully. Restart phases run separate servers.

## Next integration gate

Test a real forester production/export and food/tool/sapling resupply loop. Before
calling outposts safe, establish district-local native courier/request boundaries;
the current protected transport does not stop native couriers taking long trips.
Integrate with existing settlement membership records and preserve native research,
claim, ownership and inventory rules. Command-free controls follow that verified
gameplay loop. Warehouse racks, save-checkpoint performance and recovery tooling
remain release blockers for broader use.

These are controlled regression fixtures, not proof of sustained autonomous
forestry or multiplayer readiness. No new development JAR was installed or released.
