package dev.szedann.create_train_physics.compat;

import com.google.common.collect.ImmutableMap;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageWrapper;
import com.simibubi.create.content.trains.entity.Train;
import dev.szedann.create_train_physics.physics.EngineFuelRestrictions;
import dev.szedann.create_train_physics.physics.TrainFuelSelection;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;

/** Optional bridge for the 1.21.1 Steam 'n' Rails handcar. */
public final class SteamNRailsCompat {
    private static final String HANDCAR_INTERFACE =
            "com.railwayteam.railways.mixin_interfaces.IHandcarTrain";
    private static final String FUEL_INVENTORY_INTERFACE =
            "com.railwayteam.railways.mixin_interfaces.IFuelInventory";
    private static final String LIQUID_FUEL_HANDLER =
            "com.railwayteam.railways.content.fuel.LiquidFuelTrainHandler";

    private static volatile Method isHandcarMethod;
    private static volatile boolean handcarUnavailable;
    private static volatile Method getFluidFuelsMethod;
    private static volatile Method drainLiquidFuelMethod;
    private static volatile boolean liquidFuelUnavailable;

    private SteamNRailsCompat() {
    }

    public static boolean isHandcar(Train train) {
        if (train == null || handcarUnavailable)
            return false;

        try {
            Method method = isHandcarMethod;
            if (method == null) {
                Class<?> handcarInterface = Class.forName(
                        HANDCAR_INTERFACE,
                        false,
                        train.getClass().getClassLoader()
                );
                if (!handcarInterface.isInstance(train))
                    return false;
                method = handcarInterface.getMethod("railways$isHandcar");
                isHandcarMethod = method;
            }
            return Boolean.TRUE.equals(method.invoke(train));
        } catch (ClassNotFoundException exception) {
            handcarUnavailable = true;
            return false;
        } catch (ReflectiveOperationException | LinkageError exception) {
            handcarUnavailable = true;
            return false;
        }
    }

    /**
     * Drain one liquid-fuel portion. Unrestricted: first usable carriage in
     * travel order, via S&R's own drain. Restricted: fluid tiers are walked
     * train-wide in priority order — a lower-priority fluid is only touched
     * once no tank anywhere can supply a higher one — and an unrestricted
     * engine aboard adds S&R's generic drain as the final wildcard tier.
     */
    public static int drainOneLiquidFuel(
            Train train,
            @Nullable EngineFuelRestrictions.Policy fuelPolicy
    ) {
        if (train == null || liquidFuelUnavailable || train.carriages.isEmpty())
            return 0;

        try {
            initializeLiquidFuelBridge(train);
            if (fuelPolicy == null)
                return TrainFuelSelection.drainFirst(
                        train.carriages.size(),
                        train.speed < 0,
                        index -> drainLiquidFuel(train, index)
                );

            for (int group = 0; group < fuelPolicy.groupCount(); group++) {
                if (fuelPolicy.isWildcardGroup(group)) {
                    int drained = TrainFuelSelection.drainFirst(
                            train.carriages.size(),
                            train.speed < 0,
                            index -> drainLiquidFuel(train, index)
                    );
                    if (drained > 0)
                        return drained;
                    continue;
                }
                for (int tier = 0; tier < fuelPolicy.fluidTierCount(group); tier++) {
                    int groupIndex = group;
                    int tierIndex = tier;
                    int drained = TrainFuelSelection.drainFirst(
                            train.carriages.size(),
                            train.speed < 0,
                            index -> drainTierLiquidFuel(train, index, fuelPolicy, groupIndex, tierIndex)
                    );
                    if (drained > 0)
                        return drained;
                }
            }
            return 0;
        } catch (LiquidFuelBridgeException | ReflectiveOperationException | LinkageError exception) {
            liquidFuelUnavailable = true;
            return 0;
        }
    }

    private static void initializeLiquidFuelBridge(Train train)
            throws ReflectiveOperationException {
        if (getFluidFuelsMethod != null && drainLiquidFuelMethod != null)
            return;

        synchronized (SteamNRailsCompat.class) {
            if (getFluidFuelsMethod != null && drainLiquidFuelMethod != null)
                return;

            ClassLoader loader = train.getClass().getClassLoader();
            Class<?> fuelInventory = Class.forName(
                    FUEL_INVENTORY_INTERFACE,
                    false,
                    loader
            );
            Method getFluidFuels = fuelInventory.getMethod("railways$getFluidFuels");
            Class<?> liquidFuelHandler = Class.forName(
                    LIQUID_FUEL_HANDLER,
                    false,
                    loader
            );
            Method drainLiquidFuel = liquidFuelHandler.getMethod(
                    "handleFuelDraining",
                    getFluidFuels.getReturnType()
            );

            getFluidFuelsMethod = getFluidFuels;
            drainLiquidFuelMethod = drainLiquidFuel;
        }
    }

    /**
     * Tier-filtered variant of {@link #drainLiquidFuel}. The aggregate
     * no-argument drain always answers with the first non-empty tank, so an
     * unlisted fluid in tank 0 would hide a listed fluid behind it. Filter
     * the carriage's fuel tanks down to just those currently holding an
     * entitled fluid and delegate the actual drain to S&R's own method, so
     * its portion size and exact-amount gate stay authoritative instead of
     * being duplicated here.
     */
    private static int drainTierLiquidFuel(
            Train train,
            int carriageIndex,
            EngineFuelRestrictions.Policy fuelPolicy,
            int group,
            int tier
    ) {
        try {
            Object storage = train.carriages.get(carriageIndex).storage;
            Method getFluidFuels = getFluidFuelsMethod;
            Method drainLiquidFuel = drainLiquidFuelMethod;
            if (getFluidFuels == null || drainLiquidFuel == null
                    || !getFluidFuels.getDeclaringClass().isInstance(storage))
                return 0;

            Object fluidFuels = getFluidFuels.invoke(storage);
            if (!(fluidFuels instanceof MountedFluidStorageWrapper wrapper))
                return 0;

            ImmutableMap.Builder<BlockPos, MountedFluidStorage> entitled = ImmutableMap.builder();
            for (Map.Entry<BlockPos, MountedFluidStorage> tank : wrapper.storages.entrySet()) {
                MountedFluidStorage tankHandler = tank.getValue();
                boolean matches = false;
                for (int i = 0; i < tankHandler.getTanks() && !matches; i++)
                    matches = fuelPolicy.fluidMatchesTier(tankHandler.getFluidInTank(i), group, tier);
                if (matches)
                    entitled.put(tank.getKey(), tankHandler);
            }
            ImmutableMap<BlockPos, MountedFluidStorage> filtered = entitled.build();
            if (filtered.isEmpty())
                return 0;

            Object result = drainLiquidFuel.invoke(null, new MountedFluidStorageWrapper(filtered));
            return result instanceof Number number ? Math.max(0, number.intValue()) : 0;
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new LiquidFuelBridgeException(exception);
        }
    }

    private static int drainLiquidFuel(Train train, int carriageIndex) {
        try {
            Object storage = train.carriages.get(carriageIndex).storage;
            Method getFluidFuels = getFluidFuelsMethod;
            Method drainLiquidFuel = drainLiquidFuelMethod;
            if (getFluidFuels == null || drainLiquidFuel == null
                    || !getFluidFuels.getDeclaringClass().isInstance(storage))
                return 0;

            Object fluidFuels = getFluidFuels.invoke(storage);
            if (fluidFuels == null)
                return 0;

            Object result = drainLiquidFuel.invoke(null, fluidFuels);
            return result instanceof Number number ? Math.max(0, number.intValue()) : 0;
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new LiquidFuelBridgeException(exception);
        }
    }

    private static final class LiquidFuelBridgeException extends RuntimeException {
        private LiquidFuelBridgeException(Throwable cause) {
            super(cause);
        }
    }
}