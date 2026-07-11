## Features

- Gravity and rolling resistance
- Energy-based fuel usage
- Mass, power, and tractive-effort simulation
- Dynamic safe speed on curves
- Multiple engines distributed across a train

The physics model rewards realistic track layouts. Tight curves, abrupt slopes, and
short S-bends can require substantially lower speeds than broad railway curves.

## Supported engines

Create's Steam Engine is supported directly. Optional block recognition is
provided for engines and motors from:

- Create: Crafts & Additions
- Create: Diesel Generators, including modular and huge engines
- Create: Electro Energetics
- Create: Power Grid
- Create: The Factory Must Grow

An engine block must be in the `create_train_physics:train_motor` block tag. Modpack
authors can merge additional blocks into that tag with a datapack. For example:

```json
{
  "values": [
    {
      "id": "examplemod:locomotive_engine",
      "required": false
    }
  ]
}
```

Steam 'n' Rails handcars are handled as hand-powered vehicles and should not be
added to the motor tag.

## Configuration

The common configuration is written to
`config/create_train_physics-common.toml`.

- `enginePower` is the nominal power of each tagged engine, in kW.
- `fueledEnginePower` is the power of each ordinary engine while fuel is
  available, in kW. It defaults to the same value as `enginePower`.
- `requireFuel` controls whether ordinary fueled engines may produce traction
  without fuel. C:EE electrical availability is checked separately.
- `automaticHandbrake` applies a dead-man/parking brake to unattended trains
  and holds automatic trains at a requested stop, including red signals.

Power from every carriage is added together. More engines improve acceleration
and can raise the drag-limited maximum speed until the train reaches Create's
configured speed cap. Engine count does not raise the safe speed of a curve.

### Create: Electro Energetics

All 16 colors of C:EE electric motor are recognized through C:EE's own
`electroenergetics:train_electric_motor` tag. A motor contributes traction only
while C:EE reports usable train power: a live electrical connection, stored
accumulator charge, or a creative train power source. Merely placing an electric
motor on the train is not sufficient when its electrical supply is unavailable.

C:EE is the only optional electricity mod whose live train supply is currently
validated. Power Grid, TFMG, and Crafts & Additions blocks are recognized as
configurable engine blocks, but those mods do not expose an assembled-train
power API; their stationary network state is therefore not inferred.

When C:EE is installed, an automatically enabled built-in resource pack silences
its loud default GTO `async`, `async_decay`, `async_rise`, and `p15` traction
layers. The general `wind_static` and `wind_rise` movement ambience is left
unchanged. A higher-priority user resource pack can still replace these files.

## Q&A

### How do I make a train run?

Place at least one supported engine block on the train. Ordinary tagged engines
follow `requireFuel`; C:EE train motors always require power reported by C:EE.

### How do I make a train faster?

Add more engines. Their power is cumulative, but Create's server speed limits and
the safe speed of the current curve still apply.
