# Arcology compatibility

The 25 required builder, lumberjack, citizen, warehouse and courier blueprints (levels 1-5) decode with pinned Structurize 1.0.832/Minecraft 1.21.1. Their original palette names resolve and primary anchors are native huts. Six checks pass. No Gatehouse blueprint is present; terminal readiness remains unmet. This is a loader/metadata test, not construction, preview or upgrade proof.

Source: blueprints/arcology-horizons. Original assets were read only. See evidence/arcology-compatibility.json and evidence/arcology-catalog.txt for results, hashes, dimensions and anchors.

Measured unrotated bounds (X × Y × Z) and primary hut offsets, identical across levels 1–5 for each listed role:

| Native role | Bounds | Hut offset |
| --- | --- | --- |
| Builder | 5 × 12 × 5 | 1, 1, 1 |
| Forester | 5 × 6 × 9 | 1, 0, 3 |
| Residence | 5 × 6 × 10 | 1, 1, 1 |
| Warehouse | 15 × 6 × 15 | 12, 1, 12 |
| Courier | 5 × 6 × 5 | 3, 1, 2 |

Offsets are blueprint coordinates, not inferred door/road/connector positions. Layout and access must be checked before automated construction. The pack remains an external user-installed dependency.

The later [full installed-pack audit](ARCOLOGY-FULL-AUDIT.md) covers all 283 files, newer missing building types, crop extensions and functional marker/material issues outside this original five-role sample.
