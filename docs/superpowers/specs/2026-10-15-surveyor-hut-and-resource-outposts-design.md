# Surveyor hut and resource outposts design

Status: written design for review. Akamarus approved automatic discovery and identified seagrass, flowers, seeds, wood types, vines and kelp as priority resources. This document adds concrete proposed behavior; it is not an implementation or installation report.

## Intended experience

The player builds a Surveyor's Hut, assigns a native colonist and chooses the resource they need. The surveyor discovers suitable sites in new biomes without the player travelling there or preparing the terrain. The player approves a surveyed site and its budget. Four free founders establish the outpost, with the builder doing ordinary clearing, excavation and dirt foundation work. Local native workers produce resources, local couriers collect them, and the protected warehouse route brings them Home.

Keep the installed four-founder build unchanged during development. Preserve current colonies, previously approved sites and native job/hut menus. Move the addon outpost management entry from the Town Hall to the Surveyor's Hut; leave native MineColonies Town Hall functions alone.

## Chosen approach and alternatives

Use a real addon hut and native worker job, backed by a durable survey service. Reuse native producer professions and the existing protected supply transport. This adds the requested gameplay role without creating a second implementation of farming or forestry.

A menu-only search would be smaller but would not provide the requested hut and worker. A fully physical scout walking through every wilderness chunk would be more visible but would introduce long-distance navigation and survival failures. The proposed first version uses protected expeditions: the worker departs from and returns to the hut, with destination inspection represented by a timed, bounded server task. It does not claim to physically walk every kilometre of the route.

## Hut and worker

- Add a craftable Surveyor's Hut block, registered native building, native worker assignment, inventory and saved job. Use the existing addon namespace and pinned MineColonies registration APIs.
- Provide an Arcology-compatible connected module and its native building upgrades, preserving connector heights, access, palette and lantern safety. Avoid copying saved ownership or colony IDs into blueprints.
- One assigned surveyor performs one expedition at a time. Normal housing, food, illness and pause behavior apply. A surveyor is an ordinary Home worker, separate from the four free outpost settlers.
- Dispatch requires a completed hut, available worker and food supplied through normal local logistics. Dispatch consumes the configured expedition ration once. Cancelling does not recreate consumed supplies.
- Native recall safely ends or pauses the expedition and restores the same citizen. Death, reassignment, demolition, ownership change or restart cannot create a replacement worker or duplicate sample cargo.

## Simple menu

The hut uses native-style pages: **Surveys**, **Found Outpost**, and **Outposts**. Standard worker/inventory/build/upgrade controls remain available.

Choose an item target, with grouped categories for wood, flowers, seeds, vines and water plants. Optionally select a biome. Name the exact resource, rather than treating every flower or seed as interchangeable. Surveys list the target, progress and findings. A site report shows biome, resource evidence, distance, required producer, research blockers and estimated preparation/material costs.

Select a result to review and found an outpost. Keep four-free-settler information, supply allowance and approval in that flow. Existing outposts remain accessible through any completed Surveyor's Hut owned by the parent colony, even after the originating hut is destroyed. Persist findings and outposts at colony level; hut destruction must not erase them.

Remove the addon Town Hall outpost button after the new entry point is verified. Enforce ownership and hut requirements on the server, not only by hiding buttons. Do not disable native Town Hall management.

## Discovery and resource evidence

Automatic discovery may generate previously unexplored Overworld terrain. Proposed defaults are a 4,096-block search radius, at most 32 candidate sites per expedition, one active expedition per colony and two active inspections server-wide. Inspect one candidate footprint at a time, with at most its 49 chunks ticketed per expedition. Queue at most one new chunk load/generation request per server tick globally; never wait synchronously for it from the menu. These are configurable operating limits, not hut-level progression rewards. Stop cleanly with a readable result when the search budget is exhausted.

Each expedition persists its target, origin, search bounds, progress, worker identity, ration receipt and candidates. Pause when the owner is offline, the worker is unavailable or the colony is paused. Release survey chunk tickets while inactive, on completion and on cancellation. Reconcile them after restart.

A biome match is a search hint, not proof of the requested resource. Inspect actual target blocks or an audited native production capability before claiming a site can supply an item. Do not invent seeds or samples based solely on a biome name.

Reports distinguish **resource present**, **starter sample obtainable**, and **renewable production supported**. Store durable findings so the player can return Home without losing them. Revalidate ownership, protected blocks, terrain and costs before spending supplies or changing the world.

## Priority resources and production profiles

| Target | Proposed sustained producer | Requirements the report must explain |
| --- | --- | --- |
| Overworld wood types | Native forester | Exact tree species, sustainable planting stock and compatible native planting/harvest behavior. Bamboo and Nether stems must not be mislabeled as ordinary supported trees. |
| Flowers | Native florist where the exact flower is supported | Flower-specific native production rules, research and consumables. A wild flower discovery does not prove the florist can reproduce it. |
| Seeds | Native farmer for supported crops | Exact seed/crop identity, starter stock, suitable field and native crop compatibility. Rare acquisition such as archaeology or Sniffer seeds is not supplied merely because a matching biome exists. |
| Vines | Native Plantation with vine field | Native jungle research, shears, support faces and extension capacity. |
| Kelp | Native Plantation with kelp field | Native sea research, starter kelp, maintained water volume and reachable working positions. |
| Seagrass | Native Plantation with seagrass field | Native sea research, water/substrate, bone meal and verified shearing/drop behavior. |

The local crop integration already identifies native vine, kelp and seagrass extensions and their research effects. Audit and test the exact pinned version before producing the final item capability list. Do not claim support for every item within a category. Unsupported items get an explicit reason in the report, not a silently substituted resource.

A new resource outpost keeps four founders: Mayor, Builder, Courier and one production specialist chosen by its profile. The production specialist replaces the forestry-only fourth slot; it is not a fifth free citizen. Keep eight core buildings where appropriate: builder, warehouse, courier, specialist hut and four homes. Agricultural profiles additionally require their field/extension construction and supplies. Native research and Plantation type/extension limits still apply. Avoid founding a production profile that cannot operate under current research.

Introduce a new versioned profile/producer role rather than reinterpreting the persisted FORESTER role in existing free-founder records. Previously founded forestry outposts keep their identities, layout, bills and behavior. Additional production jobs at one outpost require later workers and approved expansion; do not promise unlimited jobs from the four-person grant.

## Starter samples

The survey service may collect up to 16 items of the selected target per expedition to seed production, without exceeding a single stack. Harvest real blocks using appropriate tools and loot rules; never spawn arbitrary resource stacks as survey rewards. Samples have finite inventory and durable custody through protected return to the hut, then normal local courier delivery. Do not modify claimed/protected terrain, containers or player structures to obtain samples. If a crash leaves a harvest/cargo handoff ambiguous, retain a recovery record rather than replaying a possible reward.

Native renewable production is preferred to repeatedly stripping a wild site. Some resources can be cultivated at Home after acquiring samples; the report should explain that option without requiring an unnecessary outpost. Outposts remain useful for sustained production and biome-dependent resources. Persistent inventories and operation receipts prevent duplicating samples on unload or restart.

## Builder terrain preparation

Surveying produces a bounded work estimate; it never silently requires the player to terraform. Ordinary trees, replaceable vegetation, uneven soil/stone, shallow gaps and blocked paths become builder work.

Generate explicit clearing, excavation, dirt filling and access tasks for each footprint. Foundations must reach solid support; do not leave floating floors or dirt platforms over unsupported voids. Reuse suitable recovered materials and request any shortfall through existing finite supplies. Track tools, drops, costs and completed operations. Perform work in bounded steps and resume safely after interruption.

Keep water needed by kelp/seagrass fields. Prefer a dry shore for huts with safe access to the resource/field rather than filling the whole shoreline. Ordinary shallow water outside preserved production areas can be contained and drained as a separately costed preparation operation. Lava, deep ocean, ravines, excessive excavation, world borders, other claims, containers and existing structures cause a specific rejection or selection of another site. They do not cause destructive automatic bulldozing.

Avoid a bootstrap deadlock: surveys create preparation plans before unsafe hut positions are placed. Secure finite supplies and distinct supported arrival/work staging positions first. The native builder needs a safe initial work anchor and temporary supply custody, then prepares the permanent builder hut site. Establish warehouse and courier next; ongoing transport remains warehouse-to-warehouse. Temporary staging has durable ownership, inventory and cleanup rules, and must not become an unlimited second supply endpoint.

Recheck each block immediately before modifying it. A chest or protected block placed after the survey pauses work with a precise message. Never use a stale survey fingerprint as permission to destroy new player changes. Cancellation retains citizens, recoverable inventory and completed physical work; it cannot refund already consumed blocks or erase the site.

## Components and delivery order

1. Native Surveyor hut/job, Arcology assets and menu migration, with legacy outpost visibility tests.
2. Bounded biome/resource search, protected expedition lifecycle, saved reports and finite sample collection.
3. Builder preparation plans and resumable execution, including bootstrap staging and dirt foundations.
4. Versioned resource profiles and native producer/field integration, then continuous exports for each supported priority target.
5. Packaged endurance, restart and copied-colony verification before a separately requested installation.

Keep survey records, terrain plans, sample custody and founding grants separate. The surveyor discovers and estimates; the player authorizes the site/budget; the Mayor coordinates; the builder prepares/builds; native specialists produce; couriers and protected routes move goods.

## Required verification

- Register/place/build/upgrade/reload the native hut; assign/recall/unassign a native surveyor; inspect Arcology connectors and access.
- Open the simple menus at the hut, reject unauthorized requests, remove the addon Town Hall entry and retain access to old outposts.
- Discover actual priority resources beyond loaded/explored terrain without requiring the player to travel. Enforce search/ticket/time limits and record truthful negative results.
- Restart, cancel, recall, lose or reassign a surveyor mid-expedition without duplicated citizens, rations, samples or leaked chunk tickets.
- For every advertised item, demonstrate its actual native harvest/renewal and required tools/research. Prove requested-item exports reach Home, not merely that a matching biome was found.
- Prepare hills, trees and shallow gaps automatically using finite tools/materials; verify supported dirt foundations, accessible paths and preserved aquatic fields. Test deep void/lava rejection and protected blocks appearing after approval.
- Interrupt clearing, filling, sample harvesting, foundation placement and bootstrap construction. Verify no duplicated drops, refunds, grants or supplies after reload.
- Establish each supported profile with four founders and no stolen Home workers; retain existing forestry outpost IDs, roles and construction after reload.
- Run long enough to exhaust starter tools/food and demonstrate resupply and repeated production in isolated packaged worlds. Use a stable copied-colony backup for migration tests, never the live installed world.

## Scope boundaries

First release targets Overworld exploration and the listed resource categories with an explicit tested item list. No magic-mod integration, Nether expeditions, paid recruitment, new progression tree or general purpose wilderness combat AI is included. Native building/research rules remain in force. These exclusions do not remove requested Overworld targets; unsupported native acquisition paths must be surfaced and resolved deliberately rather than advertised as complete.
