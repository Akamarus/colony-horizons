# Safe hut supply implementation

Scope approved in chat: protected timed shipments, actual source reserves, destination stock targets, visible status and save/reload checks. Existing outpost and protected-ledger designs remain authoritative. Implement in `feat/safe-supply-routes`; do not rebuild, replace, retag or upload alpha.1.

1. Extend the existing ledger with staged external inventory access; preserve the internal ledger contract. Test real-stock accounting, slot rules and staging isolation.
2. Add a durable prepared/committed handoff barrier. Fault-test each boundary. Pending transfers on restart are quarantined rather than automatically replayed.
3. Bind depots to existing native hut block inventories with owner, colony, claim and instance checks on every tick. No combined warehouse capability, citizen travel, claims, chunk loading or native request scheduling changes.
4. Add owner-only commands to bind home/outpost huts, create directed routes using a held item as component-aware template, pause/resume/cancel and inspect status. Explicit bindings do not found settlements or assign workers.
5. Run native inventory and persistence tests, existing ledger regression, and a development build. Record exact results and limitations.

Handoff implementation stages all inventory changes first, durably records both ledger snapshots, applies to validated native huts, requests a synchronous native chunk flush, and reads persisted hut NBT back to verify inventory and identity before publishing committed custody. Exceptions stop the network; pending disk records remain intact for operator recovery. This conservative initial implementation can stall while flushing chunks; batching is limited to once per five seconds per dimension. It is not a power-loss/filesystem durability guarantee.

Development artifacts and evidence stay separate from the submitted release. No public publication is part of this task.
