# Colony Horizons

Compatibility fixes for MineColonies, with an optional connected Arcology building pack.

Colony Horizons is an unofficial addon for **Minecraft 1.21.1 / NeoForge**. It focuses on builder and worker fixes and supports **Arcology Horizons**, our repaired and expanded adaptation of BlockyUniverseProductions' connected, vertical building style.

## Features

- Builders collect lanterns before removing support blocks, addressing falling-lantern fires during upgrades with Amendments.
- Fixes for Nether worker resumption, enchanted books, plantation shears and animal-home reloads.
- Arcology quarry excavation and access fixes.
- A separate **305-blueprint** pack with repaired buildings, Stable levels 1–5, Gatehouse levels 1–3, quarry modules and ten additional plantation fields.

## Install

1. Use **Java 21**, **Minecraft 1.21.1** and **NeoForge 21.1.250**.
2. Install **MineColonies 1.1.1403**, **Structurize 1.0.832**, **BlockUI 1.0.199**, **Domum Ornamentum 1.0.223** and **Multi-Piston 1.2.51**, using their Minecraft 1.21.1 releases.
3. Put the Colony Horizons JAR in `mods`.
4. Download the [separate Arcology Horizons blueprint ZIP](https://github.com/Akamarus/colony-horizons/releases/download/v0.1.0-alpha.1/arcology-horizons-0.1.0-alpha.1-blueprints.zip). Extract it into `blueprints`, producing `blueprints/arcology-horizons/pack.json`.
5. In the normal build tool, choose **Switch Pack → Arcology Horizons**. Use MineColonies' normal builders and assignments.

The pack is **not** a resource pack or datapack. Back up your world and test separately first. Normal tools, food, research and material requirements apply. Existing placed buildings are not automatically migrated. [Full installation guide](https://github.com/Akamarus/colony-horizons/blob/main/docs/release/INSTALLATION.md).

Amendments 2.0.5 and Moonlight 2.23.8 are optional tested companions.

## Alpha status

The final files passed official-launcher world creation, pack selection, preview, save/reload and clean-exit checks. The source rebuild is byte-identical. The pack passes 95 contract checks. These are scoped checks; **multiplayer and arbitrary modpacks remain unverified**. Restart tests cover clean saves, not power loss.

Automated outpost founding, Mayor management, protected delivery routes and colonist evolution are future plans, not features of this alpha. Ars integration is deferred.

## Credits and source

Original **Arcology** by **[BlockyUniverseProductions](https://www.planetminecraft.com/mod/arcology-5819547/)**. Arcology Horizons is an unofficial adaptation published with the author's permission, including future updates. Repairs and extensions by Akamarus / Colony Horizons.

Addon code: **GPL-3.0-only**. Arcology assets are separately permitted and **not GPL**. MineColonies and its companions remain the work of their respective authors. This is not an official LDTTeam release.

[Source](https://github.com/Akamarus/colony-horizons) · [Matching source download](https://github.com/Akamarus/colony-horizons/releases/download/v0.1.0-alpha.1/colony-horizons-0.1.0-alpha.1-source.zip) · [Issues](https://github.com/Akamarus/colony-horizons/issues) · [Release files and checksums](https://github.com/Akamarus/colony-horizons/releases/tag/v0.1.0-alpha.1) · [Roadmap](https://github.com/Akamarus/colony-horizons/blob/main/ROADMAP.md)
