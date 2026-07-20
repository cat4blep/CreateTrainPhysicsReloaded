package dev.szedann.create_train_physics.physics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Pure power-source policy shared by the train mixin and unit tests. */
public final class TrainPowerPolicy {
    /** C:EE refreshes this short fuel-tick lease while an electric train is powered. */
    public static final int CEE_FUEL_TICK_LEASE = 10;

    private TrainPowerPolicy() {
    }

    public static int availablePowerWatts(
            int totalEngines,
            int verifiedElectricEngines,
            int unverifiedElectricEngines,
            boolean requireFuel,
            boolean combustionFueled,
            boolean verifiedElectricPowered,
            int enginePowerKilowatts,
            int fueledEnginePowerKilowatts
    ) {
        int total = Math.max(0, totalEngines);
        int verifiedElectric = Math.min(
                Math.max(0, verifiedElectricEngines),
                total
        );
        int unverifiedElectric = Math.min(
                Math.max(0, unverifiedElectricEngines),
                total - verifiedElectric
        );
        int combustion = total - verifiedElectric - unverifiedElectric;

        long power = 0;
        if (!requireFuel || combustionFueled) {
            int perEngine = combustionFueled
                    ? fueledEnginePowerKilowatts
                    : enginePowerKilowatts;
            power += powerFromEngines(combustion, perEngine);
        }
        if (verifiedElectricPowered)
            power += powerFromEngines(verifiedElectric, enginePowerKilowatts);
        // These add-ons expose no assembled-train power API. Preserve their
        // historical configured power, but never make them burn solid/liquid
        // fuel or receive fueledEnginePower.
        power += powerFromEngines(unverifiedElectric, enginePowerKilowatts);

        return (int) Math.min(Integer.MAX_VALUE, power);
    }

    /**
     * Per-type power calculation used by data-driven combustion engines.
     * Only the types present in {@code fueledEngineTypes} receive fueled power;
     * other combustion types are either disabled or retain base power depending
     * on {@code requireFuel}.
     */
    public static PowerBreakdown powerBreakdown(
            Map<String, Integer> combustionEngineCounts,
            Set<String> fueledEngineTypes,
            int verifiedElectricEngines,
            int unverifiedElectricEngines,
            boolean requireFuel,
            boolean verifiedElectricPowered,
            int enginePowerKilowatts,
            int fueledEnginePowerKilowatts
    ) {
        Objects.requireNonNull(combustionEngineCounts, "combustionEngineCounts");
        Objects.requireNonNull(fueledEngineTypes, "fueledEngineTypes");

        TreeMap<String, Long> byEngine = new TreeMap<>();
        TreeMap<String, Double> allocationWeights = new TreeMap<>();
        long combustionPower = 0;
        long fuelBackedPower = 0;
        double combustionWeight = 0;
        double fuelBackedWeight = 0;

        for (Map.Entry<String, Integer> entry : combustionEngineCounts.entrySet()) {
            String engineId = entry.getKey();
            Integer rawCount = entry.getValue();
            if (engineId == null || rawCount == null || rawCount <= 0)
                continue;

            int count = rawCount;
            if (fueledEngineTypes.contains(engineId)) {
                long power = powerFromEnginesLong(count, fueledEnginePowerKilowatts);
                double weight = powerFromEnginesDouble(count, fueledEnginePowerKilowatts);
                combustionPower = saturatedAdd(combustionPower, power);
                fuelBackedPower = saturatedAdd(fuelBackedPower, power);
                combustionWeight = saturatedAdd(combustionWeight, weight);
                fuelBackedWeight = saturatedAdd(fuelBackedWeight, weight);
                if (power > 0)
                    byEngine.put(engineId, power);
                if (weight > 0)
                    allocationWeights.put(engineId, weight);
            } else if (!requireFuel) {
                combustionPower = saturatedAdd(
                        combustionPower,
                        powerFromEnginesLong(count, enginePowerKilowatts)
                );
                combustionWeight = saturatedAdd(
                        combustionWeight,
                        powerFromEnginesDouble(count, enginePowerKilowatts)
                );
            }
        }

        long electricPower = verifiedElectricPowered
                ? powerFromEnginesLong(verifiedElectricEngines, enginePowerKilowatts)
                : 0;
        electricPower = saturatedAdd(
                electricPower,
                powerFromEnginesLong(unverifiedElectricEngines, enginePowerKilowatts)
        );
        double electricWeight = verifiedElectricPowered
                ? powerFromEnginesDouble(verifiedElectricEngines, enginePowerKilowatts)
                : 0;
        electricWeight = saturatedAdd(
                electricWeight,
                powerFromEnginesDouble(unverifiedElectricEngines, enginePowerKilowatts)
        );
        long totalPower = saturatedAdd(combustionPower, electricPower);
        double totalWeight = saturatedAdd(combustionWeight, electricWeight);
        double fuelBackedShare = totalWeight > 0
                ? Math.min(1, fuelBackedWeight / totalWeight)
                : 0;

        return new PowerBreakdown(
                totalPower,
                combustionPower,
                fuelBackedPower,
                byEngine,
                allocationWeights,
                fuelBackedShare
        );
    }

    /**
     * Client-side snapshot supplied by the authoritative server. It contains
     * no per-engine allocation because clients never debit fuel accounts.
     */
    public static PowerBreakdown syncedPowerBreakdown(int totalPowerWatts) {
        return new PowerBreakdown(
                Math.max(0, totalPowerWatts),
                0,
                0,
                Map.of(),
                Map.of(),
                0
        );
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

    private static long powerFromEnginesLong(int engineCount, int powerKilowatts) {
        if (engineCount <= 0 || powerKilowatts <= 0)
            return 0;
        long wattsPerEngine = (long) powerKilowatts * 1000L;
        if (engineCount > Long.MAX_VALUE / wattsPerEngine)
            return Long.MAX_VALUE;
        return engineCount * wattsPerEngine;
    }

    private static double powerFromEnginesDouble(int engineCount, int powerKilowatts) {
        if (engineCount <= 0 || powerKilowatts <= 0)
            return 0;
        return (double) engineCount * powerKilowatts * 1000d;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left)
            return Long.MAX_VALUE;
        return left + right;
    }

    private static double saturatedAdd(double left, double right) {
        double sum = left + right;
        return Double.isFinite(sum) ? sum : Double.MAX_VALUE;
    }

    public static final class PowerBreakdown {
        private final long totalWatts;
        private final long combustionWatts;
        private final long fuelBackedWatts;
        private final Map<String, Long> fuelBackedWattsByEngine;
        private final Map<String, Double> allocationWeights;
        private final double fuelBackedShare;

        private PowerBreakdown(
                long totalWatts,
                long combustionWatts,
                long fuelBackedWatts,
                Map<String, Long> fuelBackedWattsByEngine,
                Map<String, Double> allocationWeights,
                double fuelBackedShare
        ) {
            if (totalWatts < 0 || combustionWatts < 0 || fuelBackedWatts < 0)
                throw new IllegalArgumentException("power values cannot be negative");
            if (combustionWatts > totalWatts || fuelBackedWatts > combustionWatts)
                throw new IllegalArgumentException("power breakdown is internally inconsistent");
            Objects.requireNonNull(fuelBackedWattsByEngine, "fuelBackedWattsByEngine");
            TreeMap<String, Long> sorted = new TreeMap<>();
            long mappedFuelPower = 0;
            for (Map.Entry<String, Long> entry : fuelBackedWattsByEngine.entrySet()) {
                String engineId = Objects.requireNonNull(entry.getKey(), "engine id");
                Long power = Objects.requireNonNull(entry.getValue(), "engine power");
                if (engineId.isBlank() || power <= 0)
                    throw new IllegalArgumentException("fuel-backed engine entries must be positive");
                sorted.put(engineId, power);
                mappedFuelPower = saturatedAdd(mappedFuelPower, power);
            }
            if (mappedFuelPower != fuelBackedWatts)
                throw new IllegalArgumentException("per-engine power does not match fuelBackedWatts");
            this.totalWatts = totalWatts;
            this.combustionWatts = combustionWatts;
            this.fuelBackedWatts = fuelBackedWatts;
            this.fuelBackedWattsByEngine = Collections.unmodifiableMap(
                    new LinkedHashMap<>(sorted)
            );
            TreeMap<String, Double> sortedWeights = new TreeMap<>();
            sortedWeights.putAll(Objects.requireNonNull(allocationWeights, "allocationWeights"));
            this.allocationWeights = Collections.unmodifiableMap(
                    new LinkedHashMap<>(sortedWeights)
            );
            if (!Double.isFinite(fuelBackedShare)
                    || fuelBackedShare < 0 || fuelBackedShare > 1)
                throw new IllegalArgumentException("fuelBackedShare must be between zero and one");
            this.fuelBackedShare = fuelBackedShare;
        }

        public long totalWatts() {
            return totalWatts;
        }

        public long combustionWatts() {
            return combustionWatts;
        }

        public long fuelBackedWatts() {
            return fuelBackedWatts;
        }

        public Map<String, Long> fuelBackedWattsByEngine() {
            return fuelBackedWattsByEngine;
        }

        public int totalWattsClamped() {
            return (int) Math.min(Integer.MAX_VALUE, totalWatts);
        }

        public int combustionWattsClamped() {
            return (int) Math.min(Integer.MAX_VALUE, combustionWatts);
        }

        public int fuelBackedWattsClamped() {
            return (int) Math.min(Integer.MAX_VALUE, fuelBackedWatts);
        }

        /**
         * Split measured traction energy between only the engine accounts that
         * supplied fuel-backed power. Base and electric power consume no fuel.
         */
        public Map<String, Double> allocateFuelEnergy(
                double totalTractionEnergyJoules
        ) {
            if (!(totalTractionEnergyJoules > 0)
                    || !Double.isFinite(totalTractionEnergyJoules)
                    || totalWatts <= 0
                    || fuelBackedWatts <= 0
                    || fuelBackedWattsByEngine.isEmpty())
                return Map.of();

            double fuelEnergy = totalTractionEnergyJoules * fuelBackedShare;
            double totalWeight = allocationWeights.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
            if (!(totalWeight > 0) || !Double.isFinite(totalWeight))
                return Map.of();
            LinkedHashMap<String, Double> allocation = new LinkedHashMap<>();
            double allocated = 0;
            int index = 0;
            int lastIndex = allocationWeights.size() - 1;
            for (Map.Entry<String, Double> entry : allocationWeights.entrySet()) {
                double share = index++ == lastIndex
                        ? Math.max(0, fuelEnergy - allocated)
                        : fuelEnergy * entry.getValue() / totalWeight;
                allocation.put(entry.getKey(), share);
                allocated += share;
            }
            return Collections.unmodifiableMap(allocation);
        }
    }
}
