package dev.szedann.create_train_physics.physics;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineFuelStateTest {
    private static final int JOULES_PER_TICK = 15_000;
    private static final String DIESEL_ENGINE = "test:diesel_engine";
    private static final String STEAM_ENGINE = "test:steam_engine";
    private static final FuelKey COAL = FuelKey.item("minecraft:coal");
    private static final FuelKey DIESEL = FuelKey.fluid("test:diesel");

    @Test
    void reconcilesAccountsWithTheCurrentEngineRoster() {
        EngineFuelState state = new EngineFuelState();
        state.reconcile(Map.of(STEAM_ENGINE, 2, DIESEL_ENGINE, 1));
        state.credit(STEAM_ENGINE, COAL, 4, JOULES_PER_TICK);

        state.reconcile(Map.of(DIESEL_ENGINE, 3));

        assertEquals(Set.of(DIESEL_ENGINE), state.accounts().keySet());
        assertFalse(state.hasUsableFuel(DIESEL_ENGINE));
    }

    @Test
    void consumesEachEngineTypesFuelAndDebtIndependently() {
        EngineFuelState state = new EngineFuelState();
        state.reconcile(Map.of(STEAM_ENGINE, 2, DIESEL_ENGINE, 1));
        state.credit(STEAM_ENGINE, COAL, 10, JOULES_PER_TICK);
        state.credit(DIESEL_ENGINE, DIESEL, 10, JOULES_PER_TICK);

        state.consumeFuelEnergy(
                Map.of(STEAM_ENGINE, 30_000d, DIESEL_ENGINE, 15_000d),
                JOULES_PER_TICK
        );

        assertEquals(8, state.accounts().get(STEAM_ENGINE).fuelTicks());
        assertEquals(9, state.accounts().get(DIESEL_ENGINE).fuelTicks());
        assertEquals(COAL, state.accounts().get(STEAM_ENGINE).currentFuel());
        assertEquals(DIESEL, state.accounts().get(DIESEL_ENGINE).currentFuel());
    }

    @Test
    void carriesFractionalDebtOnlyOnTheEngineThatUsedPower() {
        EngineFuelState state = new EngineFuelState();
        state.reconcile(Map.of(STEAM_ENGINE, 1, DIESEL_ENGINE, 1));
        state.credit(STEAM_ENGINE, COAL, 2, JOULES_PER_TICK);
        state.credit(DIESEL_ENGINE, DIESEL, 2, JOULES_PER_TICK);

        state.consumeFuelEnergy(Map.of(STEAM_ENGINE, 7_500d), JOULES_PER_TICK);

        assertEquals(7_500, state.accounts().get(STEAM_ENGINE).energyDebtJoules());
        assertEquals(0, state.accounts().get(DIESEL_ENGINE).energyDebtJoules());
        assertEquals(2, state.accounts().get(STEAM_ENGINE).fuelTicks());
    }

    @Test
    void legacyPoolPowersAllTypesAndIsConsumedBeforeAccounts() {
        EngineFuelState state = new EngineFuelState(
                Map.of(),
                new EngineFuelState.LegacyPool(4, 0)
        );
        state.reconcile(Map.of(STEAM_ENGINE, 2, DIESEL_ENGINE, 1));

        assertEquals(
                Set.of(STEAM_ENGINE, DIESEL_ENGINE),
                state.fueledEngineTypes(Set.of(STEAM_ENGINE, DIESEL_ENGINE))
        );
        state.consumeFuelEnergy(
                Map.of(STEAM_ENGINE, 30_000d, DIESEL_ENGINE, 15_000d),
                JOULES_PER_TICK
        );

        assertEquals(1, state.legacyPool().fuelTicks());
        assertFalse(state.hasUsableFuel(STEAM_ENGINE));
        assertFalse(state.hasUsableFuel(DIESEL_ENGINE));
        assertFalse(state.needsFuel(STEAM_ENGINE));
    }

    @Test
    void firstNewFuelSettlesResidualLegacyDebt() {
        EngineFuelState state = new EngineFuelState(
                Map.of(),
                new EngineFuelState.LegacyPool(1, 0)
        );
        state.reconcile(Map.of(STEAM_ENGINE, 1));
        state.consumeFuelEnergy(Map.of(STEAM_ENGINE, 30_000d), JOULES_PER_TICK);

        assertEquals(EngineFuelState.LegacyPool.EMPTY, state.legacyPool());
        assertEquals(15_000, state.accounts().get(STEAM_ENGINE).energyDebtJoules());

        EngineFuelState.Account account = state.credit(
                STEAM_ENGINE,
                COAL,
                2,
                JOULES_PER_TICK
        );

        assertEquals(0, state.legacyPool().energyDebtJoules());
        assertEquals(1, account.fuelTicks());
        assertTrue(state.hasUsableFuel(STEAM_ENGINE));
    }

    @Test
    void fractionalLegacyDebtJoinsTheNextMeasuredEngineEnergy() {
        EngineFuelState state = new EngineFuelState(
                Map.of(),
                new EngineFuelState.LegacyPool(0, 7_500)
        );
        state.reconcile(Map.of(STEAM_ENGINE, 1));

        EngineFuelState.Account account = state.credit(
                STEAM_ENGINE,
                COAL,
                1,
                JOULES_PER_TICK
        );

        assertEquals(7_500, state.legacyPool().energyDebtJoules());
        assertEquals(0, account.energyDebtJoules());
        assertEquals(1, account.fuelTicks());

        state.consumeFuelEnergy(Map.of(STEAM_ENGINE, 7_500d), JOULES_PER_TICK);

        assertEquals(0, state.legacyPool().energyDebtJoules());
        assertEquals(0, state.accounts().get(STEAM_ENGINE).fuelTicks());
    }

    @Test
    void residualLegacyDebtIsSplitByEngineEnergyInsteadOfRefillOrder() {
        EngineFuelState state = new EngineFuelState(
                Map.of(),
                new EngineFuelState.LegacyPool(1, 0)
        );
        state.reconcile(Map.of(STEAM_ENGINE, 2, DIESEL_ENGINE, 1));

        state.consumeFuelEnergy(
                Map.of(STEAM_ENGINE, 30_000d, DIESEL_ENGINE, 15_000d),
                JOULES_PER_TICK
        );

        assertEquals(0, state.legacyPool().fuelTicks());
        assertEquals(0, state.legacyPool().energyDebtJoules());
        assertEquals(20_000, state.accounts().get(STEAM_ENGINE).energyDebtJoules());
        assertEquals(10_000, state.accounts().get(DIESEL_ENGINE).energyDebtJoules());
    }

    @Test
    void subsequentRefillOrderDoesNotChangeSplitLegacyDebt() {
        EngineFuelState steamFirst = stateWithSplitLegacyDebt();
        EngineFuelState dieselFirst = stateWithSplitLegacyDebt();

        steamFirst.credit(STEAM_ENGINE, COAL, 2, JOULES_PER_TICK);
        steamFirst.credit(DIESEL_ENGINE, DIESEL, 1, JOULES_PER_TICK);
        dieselFirst.credit(DIESEL_ENGINE, DIESEL, 1, JOULES_PER_TICK);
        dieselFirst.credit(STEAM_ENGINE, COAL, 2, JOULES_PER_TICK);

        assertEquals(steamFirst.accounts(), dieselFirst.accounts());
    }

    @Test
    void unknownOrRemovedEngineEnergyDoesNotConsumeLegacyFuel() {
        EngineFuelState state = new EngineFuelState(
                Map.of(),
                new EngineFuelState.LegacyPool(2, 0)
        );
        state.reconcile(Map.of(STEAM_ENGINE, 1));

        state.consumeFuelEnergy(Map.of("removed:engine", 45_000d), JOULES_PER_TICK);

        assertEquals(new EngineFuelState.LegacyPool(2, 0), state.legacyPool());
        assertEquals(0, state.accounts().get(STEAM_ENGINE).energyDebtJoules());
    }

    @Test
    void ruleReloadLeavesUnidentifiedLegacyFuelAloneUntilMigrationFinishes() {
        EngineFuelState state = new EngineFuelState(
                Map.of(),
                new EngineFuelState.LegacyPool(2, 0)
        );
        state.reconcile(Map.of(STEAM_ENGINE, 1));

        Set<String> invalidated = state.reconcileFuelRules((engine, fuel) -> false);

        assertTrue(invalidated.isEmpty());
        assertTrue(state.hasActiveLegacyPool());
        assertEquals(Set.of(STEAM_ENGINE), state.fueledEngineTypes(Set.of(STEAM_ENGINE)));
    }

    @Test
    void changingOnlyEngineCountPreservesItsAccount() {
        EngineFuelState state = new EngineFuelState();
        state.reconcile(Map.of(STEAM_ENGINE, 1));
        state.credit(STEAM_ENGINE, COAL, 3, JOULES_PER_TICK);
        EngineFuelState.Account before = state.accounts().get(STEAM_ENGINE);

        state.reconcile(Map.of(STEAM_ENGINE, 5));

        assertEquals(before, state.accounts().get(STEAM_ENGINE));
    }

    @Test
    void refusesToMixDifferentFuelsInOneLiveAccount() {
        EngineFuelState state = new EngineFuelState();
        state.reconcile(Map.of(STEAM_ENGINE, 1));
        state.credit(STEAM_ENGINE, COAL, 2, JOULES_PER_TICK);

        assertThrows(
                IllegalStateException.class,
                () -> state.credit(STEAM_ENGINE, DIESEL, 1, JOULES_PER_TICK)
        );
    }

    @Test
    void refusesAmbiguousLegacyAndPerEngineBalances() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EngineFuelState(
                        Map.of(
                                STEAM_ENGINE,
                                new EngineFuelState.Account(1, 0, COAL)
                        ),
                        new EngineFuelState.LegacyPool(1, 0)
                )
        );
    }

    @Test
    void datapackReloadInvalidatesOnlyFuelRejectedByTheNewRules() {
        EngineFuelState state = new EngineFuelState();
        state.reconcile(Map.of(STEAM_ENGINE, 1, DIESEL_ENGINE, 1));
        state.credit(STEAM_ENGINE, COAL, 2, JOULES_PER_TICK);
        state.credit(DIESEL_ENGINE, DIESEL, 2, JOULES_PER_TICK);
        state.consumeFuelEnergy(Map.of(STEAM_ENGINE, 7_500d), JOULES_PER_TICK);

        Set<String> invalidated = state.reconcileFuelRules(
                (engine, fuel) -> engine.equals(DIESEL_ENGINE) && fuel.equals(DIESEL)
        );

        assertEquals(Set.of(STEAM_ENGINE), invalidated);
        assertEquals(0, state.accounts().get(STEAM_ENGINE).fuelTicks());
        assertEquals(7_500, state.accounts().get(STEAM_ENGINE).energyDebtJoules());
        assertEquals(2, state.accounts().get(DIESEL_ENGINE).fuelTicks());
        assertEquals(DIESEL, state.accounts().get(DIESEL_ENGINE).currentFuel());
    }

    private static EngineFuelState stateWithSplitLegacyDebt() {
        EngineFuelState state = new EngineFuelState(
                Map.of(),
                new EngineFuelState.LegacyPool(1, 0)
        );
        state.reconcile(Map.of(STEAM_ENGINE, 2, DIESEL_ENGINE, 1));
        state.consumeFuelEnergy(
                Map.of(STEAM_ENGINE, 30_000d, DIESEL_ENGINE, 15_000d),
                JOULES_PER_TICK
        );
        return state;
    }
}
