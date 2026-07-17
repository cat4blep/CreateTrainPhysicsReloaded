package dev.szedann.create_train_physics.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainPowerPolicyTest {
    @Test
    void requireFuelBlocksAnUnfueledCombustionEngine() {
        assertEquals(0, power(1, 0, 0, true, false, false));
    }

    @Test
    void optionalFuelUsesBaseEnginePower() {
        assertEquals(200_000, power(1, 0, 0, false, false, false));
    }

    @Test
    void realFuelUsesFueledEnginePower() {
        assertEquals(350_000, power(1, 0, 0, true, true, false));
    }

    @Test
    void electricMotorAlwaysRequiresCeePower() {
        assertEquals(0, power(1, 1, 0, false, false, false));
        assertEquals(200_000, power(1, 1, 0, true, false, true));
    }

    @Test
    void ceePowerDoesNotFuelCombustionEnginesInMixedTrain() {
        assertEquals(200_000, power(2, 1, 0, true, false, true));
        assertEquals(0, power(2, 1, 0, true, false, false));
        assertEquals(550_000, power(2, 1, 0, true, true, true));
    }

    @Test
    void unverifiedElectricMotorsNeverUseCombustionFuel() {
        assertEquals(200_000, power(1, 0, 1, true, false, false));
        assertEquals(200_000, power(1, 0, 1, true, true, false));
    }

    @Test
    void mixedUnverifiedElectricAndCombustionPowerStaySeparate() {
        assertEquals(200_000, power(2, 0, 1, true, false, false));
        assertEquals(550_000, power(2, 0, 1, true, true, false));
    }

    @Test
    void verifiedAndUnverifiedElectricPowerAreIndependent() {
        assertEquals(200_000, power(2, 1, 1, true, false, false));
        assertEquals(400_000, power(2, 1, 1, true, false, true));
    }

    @Test
    void corruptElectricCountsAreClampedToTheTotal() {
        assertEquals(200_000, power(1, 1, 1, true, true, true));
    }

    @Test
    void powerSaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, TrainPowerPolicy.availablePowerWatts(
                Integer.MAX_VALUE,
                0,
                0,
                false,
                false,
                false,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        ));
    }

    private static int power(int total, int verifiedElectric, int unverifiedElectric,
                             boolean requireFuel, boolean combustionFueled,
                             boolean verifiedElectricPowered) {
        return TrainPowerPolicy.availablePowerWatts(
                total,
                verifiedElectric,
                unverifiedElectric,
                requireFuel,
                combustionFueled,
                verifiedElectricPowered,
                200,
                350
        );
    }
}
