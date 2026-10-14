# Colony Horizons and Arcology Horizons 0.1.0-alpha.1

This is an unofficial MineColonies compatibility addon and a separate, author-permitted Arcology blueprint adaptation. Original Arcology: BlockyUniverseProductions, https://www.planetminecraft.com/mod/arcology-5819547/ . Repairs and extensions: Akamarus / Colony Horizons.

## Installation

1. Create a Minecraft Java 1.21.1 instance with Java 21 and NeoForge 21.1.250.
2. Install MineColonies 1.1.1403-1.21.1, Structurize 1.0.832-1.21.1-snapshot, BlockUI 1.0.199-1.21.1-snapshot, Domum Ornamentum 1.0.223-snapshot, and Multi-Piston 1.2.51-1.21.1-snapshot from their official distributions.
3. Put colony-horizons-0.1.0-alpha.1.jar into that instance's mods folder. Keep only one addon version.
4. Extract arcology-horizons-0.1.0-alpha.1-blueprints.zip into that instance's blueprints folder. The resulting path must be blueprints/arcology-horizons/pack.json; avoid an extra nested directory. This is not a resource pack or datapack.
5. Start a new test world. Use the MineColonies build tool, choose Switch Pack, then Arcology Horizons. Use the normal builder and worker assignment menus. Materials, food, tools, research and normal native requirements still apply.

Optional tested integration: Amendments 2.0.5 and Moonlight 2.23.8. They are not required. Back up existing worlds. Installing the pack does not migrate existing buildings automatically.

## Included and future work

Includes builder lantern support-removal handling, Nether worker resumption, stored-enchantment handling, plantation shears, animal-home reload and quarry compatibility fixes. The pack contains 305 blueprints including repairs, Stable 1-5, Gatehouse 1-3, quarry components and ten added plantation fields.

Automated outpost founding, Mayor management, protected supply routes, evolution and Ars integration are not available in this alpha. Multiplayer and arbitrary modpacks remain unverified. Recovery tests cover clean saves, not power loss.

## Licensing and source

Addon code is GPL-3.0-only. Matching source is colony-horizons-0.1.0-alpha.1-source.zip; see PUBLIC-BUILD.md, LICENSE and NOTICE. Dependencies are separate downloads. Arcology assets are distributed with the original author's permission and retain their ownership; the code license does not apply to them and this download grants no general asset reuse license.

## Updating to alpha.2

Back up the world. Replace the previous addon JAR; keep only one version. Replace blueprints/arcology-horizons with the matching alpha.2 pack. Existing Builder huts need repair/rebuild to receive the two safety rails. Previously placed ships are not relocated. Dependencies are unchanged.
