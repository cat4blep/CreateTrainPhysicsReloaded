package dev.szedann.create_train_physics.physics;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainPowerPolicyTest {
    private static final String DIESEL = "test:diesel_engine";
    private static final String STEAM = "test:steam_engine";

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

    @Test
    void fuelPowersOnlyTheMatchingEngineType() {
        TrainPowerPolicy.PowerBreakdown result = TrainPowerPolicy.powerBreakdown(
                Map.of(STEAM, 1, DIESEL, 1),
                Set.of(STEAM),
                0,
                0,
                true,
                false,
                200,
                350
        );

        assertEquals(350_000, result.totalWatts());
        assertEquals(350_000, result.combustionWatts());
        assertEquals(350_000, result.fuelBackedWatts());
        assertEquals(Map.of(STEAM, 350_000L), result.fuelBackedWattsByEngine());
    }

    @Test
    void optionalFuelKeepsUnfueledTypesAtBasePower() {
        TrainPowerPolicy.PowerBreakdown result = TrainPowerPolicy.powerBreakdown(
                Map.of(STEAM, 1, DIESEL, 1),
                Set.of(STEAM),
                0,
                0,
                false,
                false,
                200,
                350
        );

        assertEquals(550_000, result.totalWatts());
        assertEquals(550_000, result.combustionWatts());
        assertEquals(350_000, result.fuelBackedWatts());
    }

    @Test
    void allocatesOnlyFuelBackedEnergyAndWeightsItByEngineCount() {
        TrainPowerPolicy.PowerBreakdown result = TrainPowerPolicy.powerBreakdown(
                Map.of(STEAM, 2, DIESEL, 1),
                Set.of(STEAM, DIESEL),
                1,
                0,
                true,
                true,
                200,
                300
        );

        Map<String, Double> allocation = result.allocateFuelEnergy(800);

        // 600 kW steam + 300 kW diesel + 200 kW electric. Fuel receives
        // 900/1100 of the measured energy, then splits in a 2:1 ratio.
        assertEquals(800d * 600 / 1100, allocation.get(STEAM), 1.0e-9);
        assertEquals(800d * 300 / 1100, allocation.get(DIESEL), 1.0e-9);
        assertEquals(800d * 900 / 1100, allocation.values().stream()
                .mapToDouble(Double::doubleValue).sum(), 1.0e-9);
    }

    @Test
    void perTypePowerAlsoSaturatesWithoutOverflowing() {
        TrainPowerPolicy.PowerBreakdown result = TrainPowerPolicy.powerBreakdown(
                Map.of(STEAM, Integer.MAX_VALUE),
                Set.of(STEAM),
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                true,
                true,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        );

        assertEquals(Long.MAX_VALUE, result.totalWatts());
        assertEquals(Integer.MAX_VALUE, result.totalWattsClamped());
        assertTrue(result.fuelBackedWatts() > 0);
    }

    @Test
    void saturatedElectricAndFuelPowerStillSplitEnergyByTheirRealWeights() {
        TrainPowerPolicy.PowerBreakdown result = TrainPowerPolicy.powerBreakdown(
                Map.of(STEAM, Integer.MAX_VALUE),
                Set.of(STEAM),
                Integer.MAX_VALUE,
                0,
                true,
                true,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        );

        assertEquals(Long.MAX_VALUE, result.totalWatts());
        assertEquals(500, result.allocateFuelEnergy(1_000).get(STEAM), 1.0e-9);
    }

    @Test
    void syncedClientPowerHasNoFuelAccountingSideEffects() {
        TrainPowerPolicy.PowerBreakdown result =
                TrainPowerPolicy.syncedPowerBreakdown(750_000);

        assertEquals(750_000, result.totalWattsClamped());
        assertEquals(0, result.combustionWatts());
        assertTrue(result.allocateFuelEnergy(1_000).isEmpty());
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
