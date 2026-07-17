package dev.szedann.create_train_physics.physics;

import java.util.function.IntUnaryOperator;

/** Pure source-selection rules used while a train acquires new fuel. */
public final class TrainFuelSelection {
    private TrainFuelSelection() {
    }

    /**
     * Drain sources in travel order and stop after the first successful one.
     * A non-positive result means that the source did not provide usable fuel.
     */
    public static int drainFirst(
            int sourceCount,
            boolean reverse,
            IntUnaryOperator drainer
    ) {
        int count = Math.max(0, sourceCount);
        for (int attempt = 0; attempt < count; attempt++) {
            int index = reverse ? count - 1 - attempt : attempt;
            int fuelTicks = drainer.applyAsInt(index);
            if (fuelTicks > 0)
                return fuelTicks;
        }
        return 0;
    }

    /** Solid fuel is a fallback only when no liquid source supplied fuel. */
    public static boolean mayUseSolidFuel(int acquiredLiquidFuelTicks) {
        return acquiredLiquidFuelTicks <= 0;
    }
}
