package dev.szedann.create_train_physics.compat;

import com.simibubi.create.content.trains.entity.Train;
import dev.szedann.create_train_physics.physics.EngineFuelRestrictions;
import dev.szedann.create_train_physics.physics.TrainFuelSelection;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/** Optional bridge for the 1.21.1 Steam 'n' Rails handcar. */
public final class SteamNRailsCompat {
    private static final String HANDCAR_INTERFACE =
            "com.railwayteam.railways.mixin_interfaces.IHandcarTrain";
    private static final String FUEL_INVENTORY_INTERFACE =
            "com.railwayteam.railways.mixin_interfaces.IFuelInventory";
    private static final String LIQUID_FUEL_HANDLER =
            "com.railwayteam.railways.content.fuel.LiquidFuelTrainHandler";
    /** Matches the portion S&R's handleFuelDraining drains per refill. */
    private static final int FUEL_PORTION_MB = 100;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static volatile Method isHandcarMethod;
    private static volatile boolean handcarUnavailable;
    private static volatile Method getFluidFuelsMethod;
    private static volatile Method drainLiquidFuelMethod;
    private static volatile boolean liquidFuelUnavailable;
    private static volatile Method checkLiquidFuelMethod;
    private static volatile boolean restrictedBridgeUnavailable;

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
            @Nullable EngineFuelRestrictions.Policy policy
    ) {
        if (train == null || liquidFuelUnavailable || train.carriages.isEmpty())
            return 0;

        try {
            initializeLiquidFuelBridge(train);
            if (policy == null)
                return TrainFuelSelection.drainFirst(
                        train.carriages.size(),
                        train.speed < 0,
                        index -> drainLiquidFuel(train, index)
                );

            for (int group = 0; group < policy.groupCount(); group++) {
                if (policy.isWildcardGroup(group)) {
                    int drained = TrainFuelSelection.drainFirst(
                            train.carriages.size(),
                            train.speed < 0,
                            index -> drainLiquidFuel(train, index)
                    );
                    if (drained > 0)
                        return drained;
                    continue;
                }
                for (int tier = 0; tier < policy.fluidTierCount(group); tier++) {
                    int groupIndex = group;
                    int tierIndex = tier;
                    int drained = TrainFuelSelection.drainFirst(
                            train.carriages.size(),
                            train.speed < 0,
                            index -> drainTierLiquidFuel(train, index, policy, groupIndex, tierIndex)
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
     * unlisted fluid in tank 0 would hide a listed fluid behind it. Enumerate
     * tanks passively instead and drain by resource so the accepted fluid is
     * pulled from wherever it sits inside the carriage's combined fuel
     * storage. The portion matches S&R's own {@value #FUEL_PORTION_MB} mB.
     */
    private static int drainTierLiquidFuel(
            Train train,
            int carriageIndex,
            EngineFuelRestrictions.Policy policy,
            int group,
            int tier
    ) {
        try {
            Object storage = train.carriages.get(carriageIndex).storage;
            Method getFluidFuels = getFluidFuelsMethod;
            if (getFluidFuels == null
                    || !getFluidFuels.getDeclaringClass().isInstance(storage))
                return 0;

            Object fluidFuels = getFluidFuels.invoke(storage);
            if (!(fluidFuels instanceof IFluidHandler handler))
                return 0;

            Method checkFuel = resolveFuelCheck(train);
            if (checkFuel == null)
                return 0;

            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack inTank = handler.getFluidInTank(tank);
                if (!policy.fluidMatchesTier(inTank, group, tier))
                    continue;

                FluidStack request = new FluidStack(inTank.getFluid(), FUEL_PORTION_MB);
                // S&R grants a portion's full ticks even when less than the
                // portion remains; draining up to the portion matches that
                // leniency instead of stranding a residual below one portion.
                if (handler.drain(request, IFluidHandler.FluidAction.SIMULATE).isEmpty())
                    continue;

                Object burnTime = checkFuel.invoke(null, request);
                int fuelTicks = burnTime instanceof Number number ? number.intValue() : 0;
                if (fuelTicks <= 0)
                    continue;

                handler.drain(request, IFluidHandler.FluidAction.EXECUTE);
                return fuelTicks;
            }
            return 0;
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new LiquidFuelBridgeException(exception);
        }
    }

    /**
     * Resolved separately from the main bridge so a missing fuel-value
     * method only disables the restricted path (which then fails closed)
     * without breaking unrestricted liquid refuelling.
     */
    @Nullable
    private static Method resolveFuelCheck(Train train) {
        if (restrictedBridgeUnavailable)
            return null;
        Method method = checkLiquidFuelMethod;
        if (method != null)
            return method;

        synchronized (SteamNRailsCompat.class) {
            if (checkLiquidFuelMethod != null || restrictedBridgeUnavailable)
                return checkLiquidFuelMethod;
            try {
                Class<?> liquidFuelHandler = Class.forName(
                        LIQUID_FUEL_HANDLER,
                        false,
                        train.getClass().getClassLoader()
                );
                // Multiplatform signature: the common class takes Object and
                // casts to the platform FluidStack internally.
                checkLiquidFuelMethod = liquidFuelHandler.getMethod(
                        "handleFuelChecking",
                        Object.class
                );
            } catch (ReflectiveOperationException | LinkageError exception) {
                restrictedBridgeUnavailable = true;
                LOGGER.warn(
                        "Could not resolve Steam 'n' Rails' liquid fuel check; "
                                + "engine fuel restrictions will reject all fluids.",
                        exception
                );
            }
            return checkLiquidFuelMethod;
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