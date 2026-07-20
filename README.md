# Create Train Physics Reloaded

Make Create trains feel heavy, powerful, and believable.

Create Train Physics Reloaded adds mass, engine power, traction, gravity, fuel
use, rolling resistance, safer curve speeds, and improved train collisions to
Create's railway system. Long and heavy trains need enough engines, steep slopes
matter, and tight track layouts reward careful driving.

**Minecraft 1.21.1 · NeoForge 21.1.x · Requires Create 6.0.8–6.0.x**

[Source code](https://github.com/cat4blep/CreateTrainPhysicsReloaded) ·
[Issue tracker](https://github.com/cat4blep/CreateTrainPhysicsReloaded/issues) ·
[Changelog](https://github.com/cat4blep/CreateTrainPhysicsReloaded/blob/main/CHANGELOG.md)

<details>
<summary><strong>Important: fork notice, authorship, and credits</strong></summary>

This is an unofficial community-maintained fork of
[Szedann/CreateTrainPhysics](https://github.com/Szedann/CreateTrainPhysics).

The original concept and foundation were created by **Szedann**. This Reloaded
fork keeps that work, fixes outstanding gameplay problems, expands compatibility,
and maintains the mod for the supported Minecraft version.

Create Train Physics Reloaded is not an official Create add-on and is not
affiliated with the developers of Create, Create: Electro Energetics, or the
other supported add-ons. Please report problems caused by this fork to the
[Reloaded issue tracker](https://github.com/cat4blep/CreateTrainPhysicsReloaded/issues),
not to those projects.

The project remains available under the GNU LGPL 3.0 license used by the source
repository. Full source code and modification history are publicly available.

</details>

## What was already in the original mod?

The original Create Train Physics introduced the main simulation features:

- train mass and engine power;
- gravity on slopes;
- aerodynamic drag and rolling resistance;
- traction limits for acceleration;
- fuel use based on train movement;
- dynamic safe speeds through curves;
- more physical train collisions.

Reloaded preserves this idea and focuses on making it reliable in normal worlds,
large modpacks, and automated railway networks.

## What does Reloaded change?

### More reliable train physics

- Engines across the entire train are counted. Multiple locomotives now provide
  more usable power and help heavy trains accelerate.
- Acceleration, braking, coasting, slopes, and low-speed movement behave more
  consistently.
- Fuel consumption follows the work actually performed by the train.
- Safe curve speed has been corrected, including track layouts with S-bends.
- Trains no longer become speed-locked on certain S-shaped tracks.
- Saved trains correctly refresh their engine count after relevant mod or
  modpack changes.

### Fuel, signals, and parking

- The “require fuel” option now works: steam and combustion engines cannot
  provide traction without fuel when the option is enabled.
- Fueled engine power has its own working configuration value.
- With Steam 'n' Rails installed, each refill uses exactly one source: the first
  suitable liquid-fuel carriage, or one solid item when no liquid fuel is
  available. Liquid and solid fuel are no longer consumed together.
- Different combustion-engine types keep separate fuel supplies. Fuel accepted
  by one engine can no longer provide power to an incompatible engine elsewhere
  on the same train.
- Item fuel can optionally be limited to mounted storage with one exact custom
  name, including while an automated train is in an unloaded chunk.
- Automated trains once again respect signals and stop for a red signal.
- An optional automatic handbrake prevents unattended trains from rolling away
  and holds scheduled trains while they are waiting.
- Steam 'n' Rails handcars keep their intended hand-powered behaviour instead of
  being restricted like ordinary locomotives.

### Correct Create: Electro Energetics integration

- C:EE electric motors provide traction only when the train has real usable
  power from an energized catenary connection, charged accumulators, or a
  creative train power source.
- Disconnected and unpowered electric trains no longer receive free traction.
- All colour variants of C:EE train motors are supported, not only the red one.
- Mixed electric and combustion trains correctly keep their two power systems
  separate, preventing free fuel and incorrect power sharing.

### Quieter C:EE train audio

When Create: Electro Energetics is installed, Reloaded automatically enables a
small built-in resource pack that silences four unusually loud and repetitive
default GTO traction layers. The general rolling wind ambience remains enabled.

<details>
<summary><strong>Exact C:EE sound events changed by the built-in pack</strong></summary>

Silenced:

- `electroenergetics:train/gto/async`
- `electroenergetics:train/gto/async_decay`
- `electroenergetics:train/gto/async_rise`
- `electroenergetics:train/gto/p15`

Left unchanged:

- `electroenergetics:train/background/wind_static`
- `electroenergetics:train/background/wind_rise`

A higher-priority user resource pack can still replace these sounds.

</details>

## Supported engines and add-ons

| Mod | Recognized engines or compatibility |
| --- | --- |
| Create | Steam Engine |
| Create: Electro Energetics | All colour variants of train electric motors, live catenary power, accumulators, and creative train power |
| Create: Crafts & Additions | Electric Motor |
| Create: Diesel Generators | Diesel Engine, Large Diesel Engine, and Huge Diesel Engine |
| Create: Power Grid | Electric Motor and Constant Speed Motor |
| The Factory Must Grow | Turbine, regular, radial, large, simple large, electric, and heavy electric engines |
| Petrochem | Gasoline and Diesel Engines, with their matching liquid fuels kept separate |
| Steam 'n' Rails | Handcars retain their original behaviour; liquid fuel has priority without double-consuming solid fuel or draining several carriages; Create steam engines can be mounted directly on S&R fuel tanks when building a locomotive |

Modpack authors can add more engine blocks through the
`create_train_physics:train_motor` block tag. Custom electric motors without a
live power API should also be added to
`create_train_physics:unverified_electric_train_motor` so they never consume
solid or liquid fuel.

### Fuel profiles for modpacks

Modpack authors can restrict each combustion-engine block to an ordered list of
accepted fluids and items with the NeoForge block data map
`create_train_physics:engine_fuels`. Put it at
`data/create_train_physics/data_maps/block/engine_fuels.json` in a data pack.
A copyable [example](https://github.com/cat4blep/CreateTrainPhysicsReloaded/blob/main/docs/examples/engine_fuels.json)
shows exact IDs, tags, priorities, and the final `"*"` fallback.

Each engine entry supports:

- `fluids`: ordered fluid IDs, `#fluid_tags`, or `"*"`;
- `items`: ordered item IDs, `#item_tags`, or `"*"`;
- `priority`: engines with a higher number try to refill first.

A missing or empty `fluids`/`items` list accepts no fuel of that kind. An engine
without an entry remains unrestricted for backward compatibility. If `"*"` is
used, it must be the last value in its list. When a higher-priority data pack
defines the same engine block, its whole entry replaces the lower pack's entry;
copy any lower-pack values you still want to keep.

Petrochem support is included by default: its Gasoline Engine accepts gasoline
or kerosene, while its Diesel Engine accepts diesel, fuel oil, or petroleum.
Those fluids are also registered as Steam 'n' Rails liquid train fuel when both
add-ons are installed.

<details>
<summary><strong>Compatibility limitation for non-C:EE electric motors</strong></summary>

Create: Electro Energetics currently provides the only assembled-train power
state that Reloaded can verify directly. Electric motors from Crafts &
Additions, Power Grid, and TFMG are kept separate from combustion engines: they
contribute configured train power and never consume coal or liquid fuel.

Those add-ons do not expose a way to check their stationary energy network after
the blocks become part of a moving train, so Reloaded treats those motors as
presumed powered. C:EE remains the only integration with live power validation.

This means that only C:EE motors are guaranteed to require a live electrical
supply. Support can be improved when another add-on exposes a suitable train
power API.

</details>

## Configuration

The common configuration is created at
`config/create_train_physics-common.toml`.

| Option | What it does | Default |
| --- | --- | --- |
| `requireFuel` | Requires steam and combustion engines to have fuel before they can provide traction | `false` |
| `enginePower` | Base power of each recognized engine | `200 kW` |
| `fueledEnginePower` | Power of each ordinary engine while real fuel is available | `200 kW` |
| `automaticHandbrake` | Brakes unattended trains and holds automated trains at a requested stop | `true` |
| `itemFuelStorageCustomName` | Uses item fuel only from mounted storage with this exact, case-sensitive custom name; `*` allows every storage | `*` |

Adding more engines increases total available power, but it does not bypass
Create's configured top-speed limit or the safe speed of a tight curve.

When Steam 'n' Rails is installed, keep its `realisticTrains` option disabled
and use Reloaded's `requireFuel` option as the single fuel requirement. Liquid
fuel support remains available with `realisticTrains` disabled.

## Installation

1. Install **NeoForge 21.1.x** for **Minecraft 1.21.1**.
2. Install **Create 6.0.8–6.0.x** and its required dependencies.
3. Place the Create Train Physics Reloaded JAR in the `mods` folder.
4. Install any supported optional add-ons you want to use.

For multiplayer, install Reloaded on both the server and participating clients.
The server runs the train simulation, while clients also receive the bundled
C:EE sound adjustment.

## Frequently asked questions

### Why will my train not move?

Make sure the train contains a recognized engine. If fuel is required, a steam
or combustion engine needs valid train fuel. C:EE electric trains additionally
need an energized catenary connection, stored accumulator charge, or a creative
train power source.

### Do additional engines help?

Yes. Engines throughout the full consist contribute power, improving the
acceleration and hauling ability of heavy trains. Top speed is still limited by
available power, drag, Create's server settings, and track curvature.

### Can I restore the muted C:EE sounds?

Yes. A user resource pack placed above the built-in Reloaded pack can replace
the four sound files. Removing Reloaded is not required just to customize audio.

<details>
<summary><strong>Beta status and reporting problems</strong></summary>

Reloaded contains substantial changes compared with the original mod. Back up
important worlds before testing a beta build and report reproducible problems on
the [GitHub issue tracker](https://github.com/cat4blep/CreateTrainPhysicsReloaded/issues).

When reporting a problem, include the Minecraft, NeoForge, Create, Reloaded, and
optional add-on versions, along with a short description of the train involved.

</details>

## License and acknowledgements

- Original project and concept: [Szedann/CreateTrainPhysics](https://github.com/Szedann/CreateTrainPhysics)
- Reloaded source and maintenance: [cat4blep/CreateTrainPhysicsReloaded](https://github.com/cat4blep/CreateTrainPhysicsReloaded)
- License: [GNU LGPL 3.0](https://github.com/cat4blep/CreateTrainPhysicsReloaded/blob/main/LICENSE)

Create and all named add-ons belong to their respective authors. Their names are
used only to describe compatibility.
