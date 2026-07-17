package dev.szedann.create_train_physics.compat;

import com.simibubi.create.content.trains.entity.Train;
import dev.szedann.create_train_physics.physics.TrainFuelSelection;

import java.lang.reflect.Method;

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
     * Ask Steam 'n' Rails to drain one liquid-fuel portion from the first
     * usable carriage in travel order. Returning zero leaves Create's solid
     * fuel scan as the fallback.
     */
    public static int drainOneLiquidFuel(Train train) {
        if (train == null || liquidFuelUnavailable || train.carriages.isEmpty())
            return 0;

        try {
            initializeLiquidFuelBridge(train);
            return TrainFuelSelection.drainFirst(
                    train.carriages.size(),
                    train.speed < 0,
                    index -> drainLiquidFuel(train, index)
            );
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
