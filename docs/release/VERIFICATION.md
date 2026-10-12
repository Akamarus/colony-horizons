# Alpha verification

Tested 2026-10-12 on Minecraft 1.21.1 / Java 21 / NeoForge 21.1.250 with the versions listed in README.

- Final public JAR and matching source ZIP built successfully.
- Extracted matching source rebuilt with `publicJar --offline --no-build-cache --rerun-tasks`, using cached dependencies. Result was byte-identical to the public JAR.
- Artifact verifier passed: GPL metadata, notices, 13 production mixins; excludes QA references, test structures, blueprint assets and bundled dependencies.
- Public pack: 95 contract checks passed; all 305 blueprint payloads match the previously tested candidate byte-for-byte.
- Official launcher: final JAR and public pack with Amendments/Moonlight; new creative world, native pack selection and Cowhand preview, save, world reload, clean exit. No ERROR/FATAL log entries.
- Earlier candidate acceptance: 49 native scenarios and 44 Python checks. These were not all repeated in the public launcher.

Multiplayer and arbitrary modpack compatibility remain unverified. The native tests use supplied materials and controlled scenarios. Stable containment was checked over finite intervals. Clean restart recovery is not crash/power-loss recovery. A successful smoke test does not exercise every mixin branch.

SHA-256 of launcher-tested JAR before attribution correction: `1a22ad38b82bf4f67fd9654760bea3eb067ae6e67edbb3d57d627772e75acb74`.

Attribution update: the current release uses Akamarus. Both public builds pass and are byte-identical. Compared with the launcher-tested JAR, only NOTICE and the authors field in neoforge.mods.toml changed; all compiled classes and other resources are identical. Current JAR SHA-256: `717164e87fc5cd7f3d659c0703caa68026247327f20e6d231178581be9b71bc0`.
