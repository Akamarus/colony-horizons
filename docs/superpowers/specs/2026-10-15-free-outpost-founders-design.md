# Four free settlers for every new outpost

Status: implemented and verified in the isolated development checkout. Installed build unchanged. See `../../outposts/FREE-FOUNDERS-RESULTS.md` for evidence and implementation rulings.

## Intent and scope

Akamarus wants to found outposts without keeping five unemployed citizens in the main colony. Every genuinely new outpost receives four free native MineColonies adults once: Mayor, builder, courier, and forester. Existing Home workers keep their jobs, homes, inventories, and identities. The player still supplies construction materials, food, tools, and subsequent housing.

This slice implements the initial cohort, its lifecycle, and the smaller founding layout. Paid recruitment beyond the four founders remains a subsequent feature; this slice must not show a working recruitment button or promise automatic replacement settlers. Existing outposts do not receive a retrospective grant.

## Selected approach

Use native citizen records belonging to the existing parent colony, with explicit outpost membership and roles. This preserves native jobs, huts, requests, and menus while removing the requirement to select Home workers. Creating independent native colonies would change ownership and logistics beyond this request. Continuing to borrow Home workers does not solve the user's staffing problem.

Four free settlers means four people, not four workers plus a fifth Mayor. The initial plan contains eight buildings: builder hut, warehouse, courier hut, forester hut, and four homes. Preserve the native builder prerequisite: builder hut first, warehouse second, courier third, followed by housing and forestry under existing ordering rules. Keep the finite bootstrap supply exception; ongoing transport goes through the warehouses and local native couriers.

## Founding flow and menu

1. Choose a new outpost site and review its land, buildings, and material budget.
2. The review displays “4 free settlers: Mayor, Builder, Courier, Forester.” There is no existing-citizen picker for this mode.
3. Approval reserves the site, supplies, and four population slots. No citizens are created merely by previewing, renaming, or revising a proposal.
4. Once the reviewed founding supplies and a safe arrival area are available, register the four native adults under a durable grant journal and bind each to its role.
5. Use protected arrival staging: no walking across the wilderness and no visible entities until their native identities and outpost membership are durable. Spawn them at distinct dry, supported positions inside the authorized site. Unsafe or unloaded destinations wait with a readable status.
6. The existing Mayor, construction, care, housing, and logistics systems take over using the explicit role roster.

Keep the current MineColonies-style menu layout. Show concise blockers such as insufficient population capacity, supplies unavailable, unsafe arrival, or recovery required. Do not expose journal identifiers in normal menus.

## Population and safety policy

These settlers remain part of the parent colony's native population. The proposed default is to respect server and research population limits, reserving all four slots before creation. Founding may temporarily exceed completed housing capacity, because the founders must build their own homes; it does not increase the research or configured population ceiling. Show the capacity requirement before approval. Revalidate before creation because native immigration can change population after reservation; wait rather than exceed the ceiling. Housing and food shortages retain native consequences.

The free entitlement belongs to a persisted outpost identity and its originally approved site. It is consumed once creation starts, not when construction finishes. Reload, recall, pause, failure, upgrade, death, and cancellation never refresh it. Reopening the same site resumes its original entitlement rather than creating another. Keep a retained site tombstone after cancellation: a proposed footprint overlapping a previously granted footprint in the same dimension and parent colony must reuse/recover that site's identity or be rejected with an explanation. A separate nonoverlapping site is a new outpost and gets its own four settlers, subject to existing active-site limits. There is no extra lifetime grant cap.

Cancellation before creation releases reservations. Cancellation after creation retains surviving native citizens and a consumed grant record; recover them safely through the existing protected recovery path, never delete citizens as a refund. Lost or dead settlers are not replaced for free.

## Data and component boundaries

- Add a versioned founding policy separating legacy selected citizens from the new free cohort. New proposals describe four role slots before citizen IDs exist; do not fake IDs or weaken legacy validation to accept arbitrary empty parties.
- Persist an actual roster separately from immutable approved intent. Each entry binds role, native citizen ID, and UUID. A single membership/role resolver serves travel, reservations, Mayor selection, housing, care, production, construction, courier assignment, and menu summaries.
- A grant service owns capacity review, entitlement persistence, native registration, and reconciliation. A placement service continues to own safe arrival. Existing cargo and construction authorities remain separate and unchanged.
- Validate complete, unique rosters server-side. Never infer courier identity from party size or list order. Never accept a client-supplied citizen identity as authority.
- Native citizen IDs can be reused: every operation validates UUID as well as numeric ID. Use pinned runtime APIs for native initialization and required lifecycle notifications; audit-source signatures alone are not sufficient.

## Persistence and interrupted creation

Use per-role creation intent and a durable receipt containing native ID and UUID. Checkpoint the grant intent before native mutation, then the native record and its receipt before exposing that citizen to assignment or arrival. Record completion only when all four entries are reconciled. Restart must compare the grant journal with native records rather than blindly replay registration.

An interrupted save between native registration and recording its identity is an ambiguous outcome. Unless a persistent native marker proves the exact match, enter recovery-required and create no further citizens. Do not identify citizens by name, assume newly observed IDs belong to the grant, or silently replace a missing member. The implementation plan must identify the pinned API's available persistence/marker mechanism and test this boundary explicitly. Fail closed if cross-save identity cannot be proven.

Implementation ruling after independent review: an unreceipted survivor at this ambiguous boundary may appear at Home through native immigration handling. Do not quarantine unrelated new citizens to hide that survivor. The original grant stays consumed and recovery-required; it cannot automatically retry, adopt, delete, or replace an unidentified citizen. Receipted settlers retain protected arrival.

## Existing worlds

Decode legacy four-person and five-person selected-party records using their historical semantics. Preserve existing IDs, UUIDs, roles, layout, approved fingerprints, cargo, and phase. The new four-person warehouse layout has a distinct version; it must not be confused with the older four-person layout without a courier. Do not shrink existing outposts or convert their citizens to free recruits. Existing saved proposals can continue under their legacy rules; only newly created proposals use the new default.

## Acceptance evidence

Automated tests must establish:

1. Founding succeeds when Home has no idle adults, creates exactly four new native adults, and leaves all original citizens and assignments unchanged.
2. The new eight-building plan assigns Mayor, builder, courier, and forester correctly; the native courier serves the local warehouse and the forester's goods reach Home through the protected route.
3. Preview, duplicate approval, retries, reload, recall, rename, cancellation/reopening, and overlapping-site proposals cannot duplicate grants. A separate eligible site can receive four of its own.
4. Every creation/save boundary either recovers the same identities or stops with recovery-required; no extra settlers or silent replacements appear.
5. Research/server limits and concurrent population changes block safely, while lack of finished homes alone does not prevent initial founding.
6. Unsafe arrivals, missing supplies, dead settlers, and full inventories preserve native identities and cargo without teleporting into danger or granting replacements.
7. Legacy four- and five-person saves retain their roles and can continue construction, deliveries, upgrades, and recovery after reload.
8. A packaged test in an isolated copy of the colony demonstrates founding without removing Home workers, save/reload, completed local logistics, housing, food/tool supply, and repeated production deliveries.

Use automated scenarios for behavior and persistence. Reserve manual checks for readability and actual menu interaction. Do not alter the installed playtest build or world for these tests. Installing a later verified build is a separate user-requested step.

## Source observations

Reviewed current `RemoteModels.SiteRequest`, `FoundingTravel`, `FoundingReservation`, and `MayorLogistics`: current founding requires real selected IDs and several consumers infer roles from ordering/count. Reviewed native `CitizenManager`: native initialization registers citizen data, IDs are reused, initial Town Hall arrivals are handled separately, and population ceilings combine housing, research, and server settings. Therefore simply spawning four entities or reducing the required selection count is insufficient.
