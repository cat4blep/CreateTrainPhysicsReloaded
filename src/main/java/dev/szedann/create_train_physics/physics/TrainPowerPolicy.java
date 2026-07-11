package dev.szedann.create_train_physics.physics;

/** Pure power-source policy shared by the train mixin and unit tests. */
public final class TrainPowerPolicy {
    /** C:EE refreshes this short fuel-tick lease while an electric train is powered. */
    public static final int CEE_FUEL_TICK_LEASE = 10;

    private TrainPowerPolicy() {
    }

    public static int availablePowerWatts(
            int totalEngines,
            int electricEngines,
            boolean requireFuel,
            boolean combustionFueled,
            boolean electricPowered,
            int enginePowerKilowatts,
            int fueledEnginePowerKilowatts
    ) {
        int total = Math.max(0, totalEngines);
        int electric = Math.min(Math.max(0, electricEngines), total);
        int combustion = total - electric;

        long power = 0;
        if (!requireFuel || combustionFueled) {
            int perEngine = combustionFueled
                    ? fueledEnginePowerKilowatts
                    : enginePowerKilowatts;
            power += powerFromEngines(combustion, perEngine);
        }
        if (electricPowered)
            power += powerFromEngines(electric, enginePowerKilowatts);

        return (int) Math.min(Integer.MAX_VALUE, power);
    }

    private static int powerFromEngines(int engineCount, int powerKilowatts) {
        if (engineCount <= 0 || powerKilowatts <= 0)
            return 0;
        long wattsPerEngine = (long) powerKilowatts * 1000L;
        if (wattsPerEngine >= Integer.MAX_VALUE
                || engineCount > Integer.MAX_VALUE / wattsPerEngine)
            return Integer.MAX_VALUE;
        return (int) (engineCount * wattsPerEngine);
    }
}
