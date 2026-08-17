# Changelog

## 0.2.0-beta.4

- The automatic handbrake applies gradual braking to moving trains when the
  player releases the controls.

## 0.2.0-beta.3

### Engine-specific fuel

- Different combustion engine types keep separate fuel supplies. Fuel accepted
  by one engine cannot power an incompatible engine on the same train.
- When several compatible engine types run at once, each type consumes its own
  compatible fuel.
- A train acquires at most one new fuel portion per game tick, even if control
  updates run more than once.
- Fuel consumption accounts for all work during a tick, including multiple
  speed updates.
- The client receives current train power from the server, so the HUD displays
  available power in multiplayer.
- Liquid refills drain the selected compatible fluid from the matching tank.
- The `itemFuelStorageCustomName` option limits item fuel to storage with an
  exact name. `*` allows all storage and preserves the previous behavior.
- Named storage selection works for automated trains in unloaded chunks.
- Fuel saved by the previous version migrates without duplication between
  engine types.

### Petrochem and modpack configuration

- Petrochem Gasoline and Diesel Engines work as train motors.
- The Gasoline Engine accepts gasoline and kerosene. The Diesel Engine accepts
  diesel, fuel oil, and petroleum. The engine types keep these fuels separate.
- Steam 'n' Rails fuel tanks accept Petrochem fluids.
- Modpack authors can configure fluids and items for each engine block through
  a data pack, including selection order and priority.

## 0.2.0-beta.2

### Fuel and Steam 'n' Rails

- Fixed simultaneous consumption of liquid and item fuel.
- Each refill uses one fuel source.
- A train uses the first compatible Steam 'n' Rails fuel tank when liquid fuel
  is available. It consumes coal or other item fuel only when no compatible
  liquid is available.
- Fuel tanks on separate carriages no longer lose fuel at the same time.
- Create Steam Engines can mount on Steam 'n' Rails fuel tanks during locomotive
  construction, without a temporary standard tank.
- For multiplayer, disable `realisticTrains` in Steam 'n' Rails and control the
  fuel requirement with this mod's `requireFuel` option.

### Electric motors

- Electric motors from Create: Crafts & Additions, Create: Power Grid, and The
  Factory Must Grow no longer consume coal or liquid fuel.
- The `requireFuel` option applies only to steam and combustion engines.
- Create: Electro Energetics remains the only add-on that exposes live train
  power. Motors from other add-ons count as powered after train assembly because
  those add-ons do not expose their power network while the train is moving.

### Reliability

- Tests cover single-source fuel selection and separation between electric and
  combustion engines.
- Saved trains update their engine classification, including while the train is
  in an unloaded chunk.

## 0.2.0-beta.1

Version 0.2.0-beta.1 is a major rework of the original
`Szedann/CreateTrainPhysics` mod. The changes below affect normal gameplay.

### Train physics

- Train behavior during acceleration, braking, downhill travel, and speed loss
  is more stable and predictable.
- Engines across the full train contribute power. Multiple locomotives increase
  available traction and help heavy trains accelerate faster.
- Fixed safe cornering speed calculations.
- Fixed speed stalls on some S-shaped track sections.
- Improved train behavior during collisions and unusual positions on the track.

### Fuel and power

- The fuel requirement works: a standard engine without fuel produces no
  traction.
- Fixed the power setting for fueled engines.
- Fuel consumption follows the train's measured work.
- Electric and combustion engines on the same train keep their power and fuel
  handling separate and cannot create free fuel.

### Automated trains and handbrake

- Automated trains obey signals and stop at red signals.
- Added an automatic handbrake for unattended trains.
- Trains hold position at stations, while waiting, and on slopes.
- The mod configuration can disable the automatic handbrake.

### Create: Electro Energetics

- Electric motors produce traction only with live power from an energized
  catenary network, batteries, or a creative power source.
- Disconnected or unpowered electric trains produce no traction.
- All electric motor colors receive power checks.
- Fixed trains that combine electric and standard engines.
- Muted four loud electric traction sounds while retaining movement and wind
  noise.

### Create add-on compatibility

- Added support for Modular, Large, and Huge Engines from Create: Diesel
  Generators.
- Added support for Create: Power Grid motors.
- Added support for standard, large, and electric engines from The Factory Must
  Grow.
- Retained support for the Create: Crafts & Additions electric motor.
- Steam 'n' Rails handcars retain their original physics.

### Other

- Improved train data persistence across world restarts.
- Existing trains detect engine changes after mod updates or train rebuilding.
- Reworked documentation for supported engines and configuration.
