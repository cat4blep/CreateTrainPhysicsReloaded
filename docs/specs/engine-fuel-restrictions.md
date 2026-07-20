# Spec: Engine-aware train fuel restrictions

## Objective

Add opt-in, data-driven fuel rules for combustion engine blocks without allowing
one engine's fuel to power incompatible engines. Modpack authors can order
accepted fluids and items by engine priority, while trains without rules keep
the existing Reloaded behaviour.

Each combustion engine block type has an independent fuel account. At most one
new source is acquired per train tick, but already-filled accounts can power
their compatible engine types simultaneously. With `requireFuel = false`, an
unfueled type retains base power but does not consume or benefit from another
type's active fuel.

The optional named-storage filter must work for automated trains whose carriage
entities are unloaded. Liquid selection must never drain a different internal
tank merely because another tank in the same mounted storage matched a rule.

## Tech Stack

- Minecraft 1.21.1
- NeoForge 21.1.217
- Create 6.0.8
- Java 21
- JUnit Jupiter 5.11.4

## Public Data Contract

The block data map ID is `create_train_physics:engine_fuels`, loaded from:

`data/create_train_physics/data_maps/block/engine_fuels.json`

Each block value accepts these optional fields:

- `fluids`: ordered fluid IDs, `#fluid_tags`, or `"*"`.
- `items`: ordered item IDs, `#item_tags`, or `"*"`.
- `priority`: integer; higher-priority engines are considered first.

A missing or empty list means that engine accepts no fuel of that kind. When
multiple data packs define the same engine block, the higher-priority pack's
whole entry replaces the lower-priority entry. Different engine keys accumulate
normally. This atomic last-pack-wins rule keeps ordered lists and priorities
predictable; pack authors copy any lower values they still want to retain.

Unknown IDs and malformed matcher strings never match. A wildcard placed before
the end of a list fails closed for that whole list. Syntactically invalid JSON
is rejected and reported by NeoForge's data-map loader rather than being
treated as an unrestricted rule.

Engines with no data-map value remain unrestricted and form the final fallback
priority. Accounts are keyed by engine block ID and store their concrete fluid
or item ID, remaining fuel ticks, and energy debt. A legacy common pool carries
old beta.2 fuel until it is exhausted, without duplicating it across accounts.

## Configuration Contract

`itemFuelStorageCustomName = "*"` keeps all mounted item fuel storages eligible.
Any other value allows item fuel only from a storage whose plain custom name is
an exact, case-sensitive match. Names and positions are cached and serialized on
the carriage so the same rule applies while its entity is unloaded.

## Commands

- Build and unit tests: `.\gradlew.bat clean build --console=plain`
- Focused tests: `.\gradlew.bat test --console=plain`
- Data generation and mixin load smoke test: `.\gradlew.bat runData --console=plain`
- Diff hygiene: `git diff --check`

## Project Structure

- `src/main/java/.../physics`: pure policy, per-type accounts, power, and
  acquisition decisions.
- `src/main/java/.../compat`: optional Steam 'n' Rails reflection bridge.
- `src/main/java/.../network`: authoritative server-to-client power snapshots.
- `src/main/java/.../mixin`: lifecycle integration and persisted carriage/train state.
- `src/test/java/.../physics`: policy and regression unit tests.
- `docs/examples`: copyable data-pack examples.

## Code Style

Prefer immutable records for policy results and explicit clamp-at-boundary code:

```java
int fueled = Math.max(0, Math.min(combustionEngines, fueledCombustionEngines));
```

Mixin methods orchestrate; matcher, merge, and power calculations live in named
classes with unit-testable APIs. No hard dependency on optional add-ons.

## Testing Strategy

Pure JUnit tests cover plan ordering, mixed compatible/incompatible engine
counts, optional versus required fuel power, independent account consumption,
reload invalidation, the named-storage predicate, the per-tick update gate, and
legacy common-pool migration. Client calculations use a server-authoritative
power snapshot and never debit fuel locally. The Minecraft/NeoForge boundary is checked by a
full build, generated-resource validation, code review against the optional-mod
APIs, and the `runData` mixin-load smoke test. An in-world integration test is
still required before declaring every possible modpack combination verified.

## Boundaries

- Always: preserve old unrestricted trains, old saves, C:EE lease isolation,
  Steam 'n' Rails one-source refill, and optional-addon loading.
- Ask first: add a hard dependency, change the world format incompatibly, push,
  tag, or publish a release.
- Never: let an electric-only train consume combustion fuel, silently treat an
  invalid data-map entry as permission for a restricted engine, or drain a
  fluid that was not the selected concrete match.

## Success Criteria

- Coal accepted only by a steam-engine type cannot add diesel-engine power.
- Two blocks of one engine type share its account and consume that account in
  proportion to their combined fuel-backed power.
- Different compatible engine types can run simultaneously from independent
  accounts without sharing ticks or energy debt.
- Unrestricted engine types remain final wildcard rules without granting their
  fuel or power to incompatible restricted engines.
- Named item storage works after save/reload with no carriage entity loaded.
- A multi-tank mounted storage drains only the selected fluid and never a
  different earlier tank.
- Petrochem small and medium engines are optional recognized train motors.
- The feature has user documentation, a copyable JSON example, regression
  tests, a successful build, and a successful mixin-load smoke test.

## Open Questions

None. The user approved the implementation direction after review of PR #2.
