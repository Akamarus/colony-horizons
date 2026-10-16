# Barracks upgrade overlap investigation

**Update:** the development repair and its native upgrade results are documented in [CATALOG-AND-BARRACKS-RESULTS.md](CATALOG-AND-BARRACKS-RESULTS.md). The investigation below records the original pre-fix findings.

Checked 2026-10-16 after a report that upgrading the main Barracks dismantles its completed guard towers.

The ten Barracks and Barracks Tower blueprints inspected match the installed playtest pack byte for byte. A read-only comparison aligned each existing tower by its hut position and facing, then checked the next main-Barracks blueprint. All four upgrade transitions (1→2, 2→3, 3→4, 4→5) contain ordinary air targets at positions occupied by completed tower blocks. All 30 existing-anchor/tower-level combinations checked contain conflicts: 76–86 solid blocks per tower, including beds, walls, stairs and lanterns.

Reproduction command from the development checkout:

```powershell
python tools/Audit-BarracksUpgradeOverlap.py --pack build/surveyor-pack --output build/reports/barracks-upgrade-overlap.json
```

This is a static blueprint conflict diagnostic, not a completed native builder upgrade. It strongly explains the reported dismantling, but does not establish whether every conflicted block is removed in a particular live work order or whether tower registry/worker assignments are lost. Existing Barracks coverage tested level-five links, housing and duty movement; it did not cover parent upgrades with completed child towers.

Next verification should run the native upgrade with completed towers, preserving hut identity, beds, inventory and guard assignments. A repair should protect existing child-building volumes while still allowing new tower anchors and the main Barracks shell to upgrade. A blanket air substitution has not been applied because it could leave terrain in newly unlocked tower locations or prevent necessary structural changes.

At the time of the initial investigation, only this diagnostic and report were added. No installed files, release files, blueprint assets or gameplay code were changed.
