package dev.szedann.create_train_physics.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainFuelLedgerTest {
    @Test
    void keepsEnergyDebtWhenTheCurrentFuelRunsOut() {
        TrainFuelLedger.Consumption result = TrainFuelLedger.consume(2, 75_000, 15_000);

        assertEquals(0, result.fuelTicks());
        assertEquals(45_000, result.energyJoules());
    }

    @Test
    void carriesFractionalEnergyIntoTheNextTick() {
        TrainFuelLedger.Consumption result = TrainFuelLedger.consume(100, 29_999, 15_000);

        assertEquals(99, result.fuelTicks());
        assertEquals(14_999, result.energyJoules());
    }

    @Test
    void ignoresInvalidEnergyWithoutConsumingFuel() {
        TrainFuelLedger.Consumption result = TrainFuelLedger.consume(10, Double.NaN, 15_000);

        assertEquals(10, result.fuelTicks());
        assertEquals(0, result.energyJoules());
    }
}
