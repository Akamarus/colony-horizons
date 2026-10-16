# Arcology Horizons implementation plan

> Execute inline with superpowers:executing-plans. Track durable progress in .superpowers/sdd/2026-10-10-arcology-horizons/progress.md.

**Goal:** repair, validate and extend the existing Arcology assets for the pinned addon environment.
**Spec:** ../specs/2026-10-10-arcology-horizons.md
**Architecture:** typed-NBT local pack builder plus independent static acceptance and native GameTest suites. External original assets remain read-only; only tooling, original new design definitions and evidence are tracked.
**Tech:** Python standard library; Java21; Minecraft1.21.1; MineColonies1.1.1403; Structurize1.0.832.

## Tasks
- [x] 1. Add failing pack-contract checks for missing markers, foreign/petrified materials, preservation, native building coverage and required crop extensions. Implement typed NBT roundtrip and reproducible local pack generation; repair existing files. Prove RED on original, GREEN on repaired subset, byte-preserve untouched files.
- [ ] 2. Add native full-pack inspection suite and per-building acceptance matrix. Decode every file with pinned Structurize, reject unresolved original palette names, verify primary anchors, every level's native registration and upgrade metadata; validate actual marker locations. Exercise required worker services/outputs and access; fix additional discovered defects with regressions.
- [ ] 3. Design missing Gatehouse/Stable/Quarry structures and crop extensions using the existing module grid and palette. Add native requirements/geometry tests before generation, then produce all levels and support shafts. Keep footprints stable across upgrades and use horse-width entrances and appropriate crop substrates.
- [ ] 4. Test new worker behavior, local access, upgrade/reload behavior and connected sample layouts. Validate player build-tool discovery and preserve attribution. Re-run existing relevant tests and clean build; retain a complete role-by-role evidence matrix, with no untested role labeled complete.
- [ ] 5. Fresh-context final review; fix material findings; document installation and results; commit only this task's tools/source/docs. Leave generated pack locally available, preserve original and unrelated changes.

## Review focus
Source preservation and output containment; marker positions matching real blocks after rotations/upgrades; missing palette blocks silently becoming air; beds/workstations/animals becoming inaccessible inside compact modules; static/native-hook tests being mistaken for actual worker operation.

Commands: Python unittest pack-contract runner with explicit source/output arguments; tools/Build-ArcologyPack.py --source <original> --output .setup/arcology-horizons; tools/Test-OutpostFoundation.ps1 -Suite ArcologyFull -ArcologyPath <generated> -TimeoutSeconds 900; native role suites as introduced; gradlew.bat clean build. Game worlds and original assets never modified by cleanup.
