# Colony Horizons

<img src="docs/release/media/colony-horizons-icon.png" alt="Connected timber and stone towers at sunrise" width="240">

An unofficial **MineColonies compatibility addon** for Minecraft Java **1.21.1 / NeoForge 21.1.250**, with the separately installed **Arcology Horizons** blueprint pack.

[Download alpha.2]( https://github.com/Akamarus/colony-horizons/releases/tag/v0.1.0-alpha.2 ) · [Installation](docs/release/INSTALLATION.md) · [Report an issue](https://github.com/Akamarus/colony-horizons/issues) · [Roadmap](ROADMAP.md)

## Beta.1 candidate

**0.1.0-beta.1** freezes the supported scope around the Exploration Hut, recipe
catalog, and Arcology compatibility fixes. This is a prepared beta candidate;
the published alpha.2 remains available until the beta is uploaded.

- **Exploration Hut:** discover resources in real biomes, then gather supported wood types,
  saplings, flowers, wheat seeds, vines, kelp and seagrass on protected simulated trips.
  Native couriers collect output. Includes five matching Arcology hut blueprints.
- **Recipe catalog:** search supported crafting recipes and select ingredients without
  carrying teaching materials. Native hut capacity, profession restrictions and production
  costs still apply. Manually taught recipes are recognized to prevent new duplicates.
- **Arcology repairs:** preserve completed Barracks child towers during upgrades and seal
  water during Mine shaft construction. Retains prior lantern, worker, quarry, raft and
  Builder recall fixes.
- **310 blueprints** in the separate Arcology Horizons pack, including the Exploration Hut.

Multi-building outposts, autonomous founding, Mayors and inter-settlement routes are
**paused and outside the supported beta scope**. Legacy code and save recovery remain.
Ars integration is deferred.

## Installation and limits

Use Java 21, Minecraft 1.21.1 and NeoForge 21.1.250, with MineColonies
1.1.1403-1.21.1, Structurize 1.0.832-1.21.1-snapshot, BlockUI
1.0.199-1.21.1-snapshot, Domum Ornamentum 1.0.223-snapshot and Multi-Piston
1.2.51-1.21.1-snapshot. Amendments 2.0.5 and Moonlight 2.23.8 are optional tested companions.

Install the addon JAR in `mods`, keeping one version. Extract the matching blueprint
ZIP so `blueprints/arcology-horizons/pack.json` exists. Select **Arcology Horizons**
in the normal build tool. The pack is not a resource pack or datapack. Back up worlds
before updating; existing buildings require repair/rebuild for blueprint changes.

See [beta notes](docs/release/BETA1.md), [installation](docs/release/INSTALLATION.md),
[Exploration guide](docs/outposts/EXPLORATION-RESULTS.md) and
[verification](docs/outposts/RELEASE-CANDIDATE-TESTING.md).
Testing covers controlled worker scenarios, copied-colony recovery, clean restart,
packaged client/server and alpha.2 save compatibility. Multiplayer, arbitrary modpacks,
power-loss recovery and indefinite endurance are not certified. Existing duplicate
recipes are not automatically removed.

## Build from source

For the beta candidate, use Java 21:

```powershell
.\gradlew.bat supplyDevJar --no-daemon
python tools/Test-SupplyDevPackage.py build/supply-dev-libs/colony-horizons-0.1.0-beta.1.jar --version 0.1.0-beta.1
```

Use `sh gradlew` on Linux/macOS. Initial dependency downloads require network
access. Use `supplyDevJar`, which excludes QA hooks; do not install the ordinary
development JAR or isolated QA JAR into your colony. See [development instructions](docs/DEVELOPMENT.md).
To reproduce published alpha.2, check out tag `v0.1.0-alpha.2` and follow that
tag's build instructions. The release tag and downloadable artifacts are unchanged.

## Credits and licensing

**Original Arcology: [BlockyUniverseProductions](https://www.planetminecraft.com/mod/arcology-5819547/).** Arcology Horizons is an unofficial adaptation published with their permission, including future updates. Compatibility repairs and extensions: Akamarus / Colony Horizons.

Addon code is **GPL-3.0-only**; see [LICENSE](LICENSE), [NOTICE](NOTICE), the retained template terms and Gradle notice. The **Arcology pack and original artwork are not GPL** and retain their authors' ownership; see [pack credits](blueprints/arcology-horizons/CREDITS.txt). Author permission to publish this adaptation is not a blanket asset reuse license.

MineColonies, Structurize and companion mods belong to their respective authors. This is not an official LDTTeam or Mojang release. The project icon is original AI-generated promotional artwork; it is not an in-game screenshot.
