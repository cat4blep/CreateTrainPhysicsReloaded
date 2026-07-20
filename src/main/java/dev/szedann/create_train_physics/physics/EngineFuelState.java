package dev.szedann.create_train_physics.physics;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;

/**
 * Mutable runtime ledger with one independent fuel account per engine type.
 *
 * <p>The legacy pool represents the one train-wide fuel balance used by older
 * saves. While it contains fuel it powers every current combustion type and is
 * debited before the independent accounts. It is never refilled as a pool; a
 * one-time residual energy debt is instead paid by the next acquired fuel.
 */
public final class EngineFuelState {
    private static final java.util.Comparator<String> ID_ORDER =
            java.util.Comparator.naturalOrder();

    private final Map<String, Account> accounts = new HashMap<>();
    private LegacyPool legacyPool;

    public EngineFuelState() {
        this(Map.of(), LegacyPool.EMPTY);
    }

    public EngineFuelState(
            Map<String, Account> accounts,
            LegacyPool legacyPool
    ) {
        Objects.requireNonNull(accounts, "accounts");
        this.legacyPool = Objects.requireNonNull(legacyPool, "legacyPool");
        for (Map.Entry<String, Account> entry : accounts.entrySet()) {
            String engineId = Objects.requireNonNull(entry.getKey(), "engine id");
            Account account = Objects.requireNonNull(entry.getValue(), "account");
            if (legacyPool.fuelTicks() > 0
                    && (account.fuelTicks() > 0 || account.energyDebtJoules() > 0))
                throw new IllegalArgumentException(
                        "legacy and per-engine fuel balances cannot coexist"
                );
            this.accounts.put(engineId, account);
        }
    }

    /** Add new engine accounts and discard accounts for types no longer aboard. */
    public void reconcile(Map<String, Integer> currentEngineCounts) {
        Objects.requireNonNull(currentEngineCounts, "currentEngineCounts");
        Set<String> current = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : currentEngineCounts.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                current.add(entry.getKey());
        }
        accounts.keySet().retainAll(current);
        for (String engineId : current)
            accounts.putIfAbsent(engineId, Account.EMPTY);
    }

    /**
     * Apply freshly reloaded fuel rules to live accounts. Fuel that is no
     * longer accepted is discarded so stale datapack state cannot keep an
     * engine powered. Already incurred energy debt remains on that engine and
     * is settled by its next valid fuel.
     *
     * @return engine types whose live fuel was invalidated
     */
    public Set<String> reconcileFuelRules(
            BiPredicate<String, FuelKey> acceptsFuel
    ) {
        Objects.requireNonNull(acceptsFuel, "acceptsFuel");
        LinkedHashSet<String> invalidated = new LinkedHashSet<>();
        TreeMap<String, Account> sorted = new TreeMap<>(ID_ORDER);
        sorted.putAll(accounts);
        for (Map.Entry<String, Account> entry : sorted.entrySet()) {
            Account account = entry.getValue();
            FuelKey fuel = account.currentFuel();
            if (account.fuelTicks() <= 0 || fuel == null
                    || acceptsFuel.test(entry.getKey(), fuel))
                continue;
            accounts.put(entry.getKey(), new Account(
                    0,
                    account.energyDebtJoules(),
                    null
            ));
            invalidated.add(entry.getKey());
        }
        return Set.copyOf(invalidated);
    }

    /**
     * Credit one engine type and immediately settle any carried energy debt.
     * A live account cannot be silently changed to another fuel kind.
     */
    public Account credit(
            String engineId,
            FuelKey fuel,
            int fuelTicks,
            int joulesPerFuelTick
    ) {
        Objects.requireNonNull(engineId, "engineId");
        Objects.requireNonNull(fuel, "fuel");
        validateFuelAmount(fuelTicks, joulesPerFuelTick);
        if (legacyPool.fuelTicks() > 0)
            throw new IllegalStateException("legacy pool must be exhausted before refilling engine accounts");
        Account current = accounts.get(engineId);
        if (current == null)
            throw new IllegalArgumentException("engine account is not part of the current roster: " + engineId);

        if (current.fuelTicks() > 0
                && current.currentFuel() != null
                && !current.currentFuel().equals(fuel))
            throw new IllegalStateException("cannot mix fuels in one engine account");

        int combinedTicks = saturatedAdd(current.fuelTicks(), fuelTicks);
        FuelKey currentFuel = combinedTicks > 0
                ? current.currentFuel() == null ? fuel : current.currentFuel()
                : null;
        TrainFuelLedger.Consumption settlement = TrainFuelLedger.consume(
                combinedTicks,
                current.energyDebtJoules(),
                joulesPerFuelTick
        );
        Account updated = new Account(
                settlement.fuelTicks(),
                settlement.energyJoules(),
                settlement.fuelTicks() > 0 ? currentFuel : null
        );
        accounts.put(engineId, updated);
        return updated;
    }

    /** Debit the exact energy already attributed to each fuel-backed type. */
    public void consumeFuelEnergy(
            Map<String, Double> energyByEngine,
            int joulesPerFuelTick
    ) {
        Objects.requireNonNull(energyByEngine, "energyByEngine");
        if (joulesPerFuelTick <= 0)
            throw new IllegalArgumentException("joulesPerFuelTick must be positive");

        TreeMap<String, Double> validEnergy = new TreeMap<>();
        double totalEnergy = 0;
        for (Map.Entry<String, Double> entry : energyByEngine.entrySet()) {
            Double energy = entry.getValue();
            if (entry.getKey() == null || !accounts.containsKey(entry.getKey())
                    || energy == null || !Double.isFinite(energy) || energy <= 0)
                continue;
            validEnergy.merge(entry.getKey(), energy, EngineFuelState::saturatedAdd);
            totalEnergy = saturatedAdd(totalEnergy, energy);
        }
        if (!(totalEnergy > 0))
            return;

        if (legacyPool.fuelTicks() > 0) {
            TrainFuelLedger.Consumption consumption = TrainFuelLedger.consume(
                    legacyPool.fuelTicks(),
                    saturatedAdd(legacyPool.energyDebtJoules(), totalEnergy),
                    joulesPerFuelTick
            );
            if (consumption.fuelTicks() > 0) {
                legacyPool = new LegacyPool(
                        consumption.fuelTicks(),
                        consumption.energyJoules()
                );
                return;
            }

            legacyPool = LegacyPool.EMPTY;
            consumeProportionally(
                    validEnergy,
                    totalEnergy,
                    consumption.energyJoules(),
                    joulesPerFuelTick
            );
            return;
        }

        if (legacyPool.energyDebtJoules() > 0) {
            double combinedDebt = saturatedAdd(
                    legacyPool.energyDebtJoules(),
                    totalEnergy
            );
            legacyPool = LegacyPool.EMPTY;
            consumeProportionally(
                    validEnergy,
                    totalEnergy,
                    combinedDebt,
                    joulesPerFuelTick
            );
            return;
        }

        consumeAccounts(validEnergy, joulesPerFuelTick);
    }

    public boolean hasUsableFuel(String engineId) {
        Account account = accounts.get(engineId);
        return account != null && account.fuelTicks() > 0;
    }

    /** Whether the acquisition planner may seek a new portion for this type. */
    public boolean needsFuel(String engineId) {
        return !hasActiveLegacyPool()
                && accounts.containsKey(engineId)
                && !hasUsableFuel(engineId);
    }

    public boolean hasActiveLegacyPool() {
        return legacyPool.fuelTicks() > 0;
    }

    /** Engine types that may currently contribute fueled power. */
    public Set<String> fueledEngineTypes(Set<String> currentEngineTypes) {
        Objects.requireNonNull(currentEngineTypes, "currentEngineTypes");
        if (hasActiveLegacyPool())
            return Set.copyOf(currentEngineTypes);

        HashSet<String> fueled = new HashSet<>();
        for (Map.Entry<String, Account> entry : accounts.entrySet())
            if (currentEngineTypes.contains(entry.getKey()) && entry.getValue().fuelTicks() > 0)
                fueled.add(entry.getKey());
        return Set.copyOf(fueled);
    }

    public Map<String, Account> accounts() {
        TreeMap<String, Account> sorted = new TreeMap<>(ID_ORDER);
        sorted.putAll(accounts);
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public LegacyPool legacyPool() {
        return legacyPool;
    }

    private static void validateFuelAmount(int fuelTicks, int joulesPerFuelTick) {
        if (fuelTicks <= 0)
            throw new IllegalArgumentException("fuelTicks must be positive");
        if (joulesPerFuelTick <= 0)
            throw new IllegalArgumentException("joulesPerFuelTick must be positive");
    }

    private static int saturatedAdd(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, (long) left + right);
    }

    private static double saturatedAdd(double left, double right) {
        double sum = left + right;
        return Double.isFinite(sum) ? sum : Double.MAX_VALUE;
    }

    private void consumeProportionally(
            Map<String, Double> energyWeights,
            double totalWeight,
            double energyDebt,
            int joulesPerFuelTick
    ) {
        if (!(energyDebt > 0))
            return;

        LinkedHashMap<String, Double> allocation = new LinkedHashMap<>();
        double allocated = 0;
        int index = 0;
        int lastIndex = energyWeights.size() - 1;
        for (Map.Entry<String, Double> entry : energyWeights.entrySet()) {
            double share = index++ == lastIndex
                    ? Math.max(0, energyDebt - allocated)
                    : energyDebt * entry.getValue() / totalWeight;
            allocation.put(entry.getKey(), share);
            allocated = saturatedAdd(allocated, share);
        }
        consumeAccounts(allocation, joulesPerFuelTick);
    }

    private void consumeAccounts(
            Map<String, Double> energyByEngine,
            int joulesPerFuelTick
    ) {
        for (Map.Entry<String, Double> entry : energyByEngine.entrySet()) {
            Account current = accounts.get(entry.getKey());
            if (current == null)
                continue;
            TrainFuelLedger.Consumption consumption = TrainFuelLedger.consume(
                    current.fuelTicks(),
                    saturatedAdd(current.energyDebtJoules(), entry.getValue()),
                    joulesPerFuelTick
            );
            accounts.put(entry.getKey(), new Account(
                    consumption.fuelTicks(),
                    consumption.energyJoules(),
                    consumption.fuelTicks() > 0 ? current.currentFuel() : null
            ));
        }
    }

    public record Account(
            int fuelTicks,
            double energyDebtJoules,
            FuelKey currentFuel
    ) {
        public static final Account EMPTY = new Account(0, 0, null);

        public Account {
            if (fuelTicks < 0)
                throw new IllegalArgumentException("fuelTicks cannot be negative");
            if (!Double.isFinite(energyDebtJoules) || energyDebtJoules < 0)
                throw new IllegalArgumentException("energyDebtJoules must be finite and non-negative");
            if (fuelTicks > 0 && currentFuel == null)
                throw new IllegalArgumentException("a non-empty account needs a fuel identity");
            if (fuelTicks == 0)
                currentFuel = null;
        }
    }

    public record LegacyPool(int fuelTicks, double energyDebtJoules) {
        public static final LegacyPool EMPTY = new LegacyPool(0, 0);

        public LegacyPool {
            if (fuelTicks < 0)
                throw new IllegalArgumentException("fuelTicks cannot be negative");
            if (!Double.isFinite(energyDebtJoules) || energyDebtJoules < 0)
                throw new IllegalArgumentException("energyDebtJoules must be finite and non-negative");
        }
    }
}
