package dev.szedann.create_train_physics.physics;

/** Pure conversion between measured traction energy and Create fuel ticks. */
public final class TrainFuelLedger {
    private TrainFuelLedger() {
    }

    public static Consumption consume(int fuelTicks, double energyJoules,
                                      int joulesPerFuelTick) {
        int availableTicks = Math.max(0, fuelTicks);
        if (!(energyJoules > 0) || joulesPerFuelTick <= 0
                || !Double.isFinite(energyJoules))
            return new Consumption(availableTicks, 0);

        int requestedTicks = (int) Math.min(
                Integer.MAX_VALUE,
                energyJoules / joulesPerFuelTick
        );
        int consumedTicks = Math.min(availableTicks, requestedTicks);
        double remainingEnergy = Math.max(
                0,
                energyJoules - (double) consumedTicks * joulesPerFuelTick
        );
        return new Consumption(availableTicks - consumedTicks, remainingEnergy);
    }

    public record Consumption(int fuelTicks, double energyJoules) {
    }
}
