package dev.szedann.create_train_physics.compat;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageWrapper;
import com.simibubi.create.content.trains.entity.Train;
import dev.szedann.create_train_physics.physics.EngineFuelRestrictions;
import dev.szedann.create_train_physics.physics.FuelKey;
import dev.szedann.create_train_physics.physics.TrainFuelSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/** Optional bridge for the 1.21.1 Steam 'n' Rails handcar. */
public final class SteamNRailsCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "railways";
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
    private static volatile Method checkLiquidFuelMethod;
    private static volatile boolean liquidFuelUnavailable;
    private static final AtomicBoolean LIQUID_FUEL_WARNING = new AtomicBoolean();

    private SteamNRailsCompat() {
    }

    public static boolean isHandcar(Train train) {
        if (train == null || handcarUnavailable || !ModList.get().isLoaded(MOD_ID))
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
        if (train == null || liquidFuelUnavailable || !ModList.get().isLoaded(MOD_ID)
                || train.carriages.isEmpty())
            return 0;

        try {
            initializeLiquidFuelBridge(train);
            return TrainFuelSelection.drainFirst(
                    train.carriages.size(),
                    train.speed < 0,
                    index -> drainLiquidFuel(train, index)
            );
        } catch (LiquidFuelBridgeException | ReflectiveOperationException | LinkageError exception) {
            disableLiquidFuelBridge(exception);
            return 0;
        }
    }

    /**
     * Drain one S&R liquid-fuel portion for the first eligible engine/fuel
     * candidate. Fuel candidates are considered before carriages so an
     * engine's configured priority applies train-wide, independent of where a
     * tank happens to be mounted.
     */
    public static @Nullable LiquidFuelAcquisition drainOneLiquidFuel(
            Train train,
            EngineFuelRestrictions.Policy policy,
            Set<String> enginesNeedingFuel
    ) {
        if (train == null || policy == null || enginesNeedingFuel == null
                || enginesNeedingFuel.isEmpty() || liquidFuelUnavailable
                || !ModList.get().isLoaded(MOD_ID)
                || train.carriages.isEmpty())
            return null;

        try {
            initializeLiquidFuelBridge(train);
            for (EngineFuelRestrictions.FuelCandidate candidate
                    : policy.candidates(FuelKey.Kind.FLUID, enginesNeedingFuel)) {
                for (int offset = 0; offset < train.carriages.size(); offset++) {
                    int carriageIndex = train.speed < 0
                            ? train.carriages.size() - 1 - offset
                            : offset;
                    LiquidFuelAcquisition acquisition = drainCandidateLiquidFuel(
                            train,
                            carriageIndex,
                            policy,
                            candidate
                    );
                    if (acquisition != null)
                        return acquisition;
                }
            }
            return null;
        } catch (LiquidFuelBridgeException | ReflectiveOperationException | LinkageError exception) {
            disableLiquidFuelBridge(exception);
            return null;
        }
    }

    private static void disableLiquidFuelBridge(Throwable failure) {
        liquidFuelUnavailable = true;
        if (LIQUID_FUEL_WARNING.compareAndSet(false, true)) {
            Throwable cause = failure instanceof LiquidFuelBridgeException
                    && failure.getCause() != null
                    ? failure.getCause()
                    : failure;
            LOGGER.warn(
                    "Steam 'n' Rails liquid train fuel is incompatible with this version; "
                            + "item fuel remains available as a fallback",
                    cause
            );
        }
    }

    private static void initializeLiquidFuelBridge(Train train)
            throws ReflectiveOperationException {
        if (getFluidFuelsMethod != null
                && drainLiquidFuelMethod != null
                && checkLiquidFuelMethod != null)
            return;

        synchronized (SteamNRailsCompat.class) {
            if (getFluidFuelsMethod != null
                    && drainLiquidFuelMethod != null
                    && checkLiquidFuelMethod != null)
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
            Method checkLiquidFuel = liquidFuelHandler.getMethod(
                    "handleFuelChecking",
                    Object.class
            );

            getFluidFuelsMethod = getFluidFuels;
            drainLiquidFuelMethod = drainLiquidFuel;
            checkLiquidFuelMethod = checkLiquidFuel;
        }
    }

    private static @Nullable LiquidFuelAcquisition drainCandidateLiquidFuel(
            Train train,
            int carriageIndex,
            EngineFuelRestrictions.Policy policy,
            EngineFuelRestrictions.FuelCandidate candidate
    ) {
        try {
            Object storage = train.carriages.get(carriageIndex).storage;
            Method getFluidFuels = getFluidFuelsMethod;
            Method drainLiquidFuel = drainLiquidFuelMethod;
            if (getFluidFuels == null || drainLiquidFuel == null
                    || !getFluidFuels.getDeclaringClass().isInstance(storage))
                return null;

            Object availableFuel = getFluidFuels.invoke(storage);
            if (!(availableFuel instanceof MountedFluidStorageWrapper wrapper))
                return null;

            DrainCapture capture = new DrainCapture();
            ImmutableMap.Builder<BlockPos, MountedFluidStorage> filtered = ImmutableMap.builder();
            for (Map.Entry<BlockPos, MountedFluidStorage> entry : wrapper.storages.entrySet()) {
                Predicate<FluidStack> allowed = stack -> policy.matches(candidate, stack)
                        && checkLiquidFuelTicks(stack) > 0;
                TierFilteredMountedFluidStorage proxy = new TierFilteredMountedFluidStorage(
                        entry.getValue(),
                        allowed,
                        capture
                );
                if (proxy.hasVisibleFluid())
                    filtered.put(entry.getKey(), proxy);
            }

            ImmutableMap<BlockPos, MountedFluidStorage> entitled = filtered.build();
            if (entitled.isEmpty())
                return null;

            Object result = drainLiquidFuel.invoke(
                    null,
                    new MountedFluidStorageWrapper(entitled)
            );
            int ticks = result instanceof Number number
                    ? Math.max(0, number.intValue())
                    : 0;
            if (ticks <= 0)
                return null;

            FluidStack drained = capture.drained();
            if (drained.isEmpty())
                throw new LiquidFuelBridgeException(new IllegalStateException(
                        "Steam 'n' Rails reported fuel without executing an exact filtered drain"
                ));
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(drained.getFluid());
            if (fluidId == null)
                throw new LiquidFuelBridgeException(new IllegalStateException(
                        "Steam 'n' Rails drained an unregistered fluid"
                ));
            return new LiquidFuelAcquisition(
                    candidate.engineId(),
                    FuelKey.fluid(fluidId.toString()),
                    ticks
            );
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new LiquidFuelBridgeException(exception);
        }
    }

    private static int checkLiquidFuelTicks(FluidStack stack) {
        Method checkLiquidFuel = checkLiquidFuelMethod;
        if (checkLiquidFuel == null || stack == null || stack.isEmpty())
            return 0;
        try {
            Object result = checkLiquidFuel.invoke(null, stack);
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

    /**
     * Exact-resource implementation for the otherwise ambiguous
     * {@link IFluidHandler#drain(int, IFluidHandler.FluidAction)} operation.
     * A partial simulation is skipped instead of being returned: Create's
     * combined wrapper subtracts partial results before trying the next
     * handler, which can otherwise hide an entitled full portion behind it.
     */
    private static FluidStack drainMatchingFluid(
            IFluidHandler delegate,
            int maxDrain,
            IFluidHandler.FluidAction action,
            Predicate<FluidStack> allowed
    ) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(allowed, "allowed");
        if (maxDrain <= 0)
            return FluidStack.EMPTY;

        for (int tank = 0; tank < delegate.getTanks(); tank++) {
            FluidStack candidate = delegate.getFluidInTank(tank);
            if (candidate == null || candidate.isEmpty() || !allowed.test(candidate))
                continue;

            FluidStack request = candidate.copyWithAmount(maxDrain);
            FluidStack simulated = delegate.drain(
                    request,
                    IFluidHandler.FluidAction.SIMULATE
            );
            if (!isExactDrain(request, simulated))
                continue;
            if (action.simulate())
                return simulated;

            FluidStack drained = delegate.drain(
                    request,
                    IFluidHandler.FluidAction.EXECUTE
            );
            return isExactDrain(request, drained) ? drained : FluidStack.EMPTY;
        }
        return FluidStack.EMPTY;
    }

    private static boolean isExactDrain(FluidStack request, FluidStack result) {
        return result != null
                && result.getAmount() == request.getAmount()
                && FluidStack.isSameFluidSameComponents(request, result);
    }

    public record LiquidFuelAcquisition(
            String engineId,
            FuelKey fuel,
            int ticks
    ) {
        public LiquidFuelAcquisition {
            Objects.requireNonNull(engineId, "engineId");
            Objects.requireNonNull(fuel, "fuel");
            if (engineId.isBlank())
                throw new IllegalArgumentException("engineId cannot be blank");
            if (fuel.kind() != FuelKey.Kind.FLUID)
                throw new IllegalArgumentException("liquid acquisition requires a fluid fuel key");
            if (ticks <= 0)
                throw new IllegalArgumentException("ticks must be positive");
        }
    }

    private static final class DrainCapture {
        private FluidStack drained = FluidStack.EMPTY;

        private void record(FluidStack stack) {
            if (stack == null || stack.isEmpty())
                return;
            if (drained.isEmpty()) {
                drained = stack.copy();
                return;
            }
            if (!FluidStack.isSameFluidSameComponents(drained, stack))
                throw new LiquidFuelBridgeException(new IllegalStateException(
                        "A filtered liquid-fuel drain mixed different fluids"
                ));
            drained.grow(stack.getAmount());
        }

        private FluidStack drained() {
            return drained.copy();
        }
    }

    private static final class TierFilteredMountedFluidStorage extends MountedFluidStorage {
        private final MountedFluidStorage delegate;
        private final Predicate<FluidStack> allowed;
        private final DrainCapture capture;

        private TierFilteredMountedFluidStorage(
                MountedFluidStorage delegate,
                Predicate<FluidStack> allowed,
                DrainCapture capture
        ) {
            super(Objects.requireNonNull(delegate, "delegate").type);
            this.delegate = delegate;
            this.allowed = Objects.requireNonNull(allowed, "allowed");
            this.capture = Objects.requireNonNull(capture, "capture");
        }

        private boolean hasVisibleFluid() {
            for (int tank = 0; tank < delegate.getTanks(); tank++) {
                FluidStack stack = delegate.getFluidInTank(tank);
                if (stack != null && !stack.isEmpty() && allowed.test(stack))
                    return true;
            }
            return false;
        }

        @Override
        public int getTanks() {
            return delegate.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            FluidStack stack = delegate.getFluidInTank(tank);
            return stack != null && !stack.isEmpty() && allowed.test(stack)
                    ? stack
                    : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return delegate.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(
                FluidStack resource,
                IFluidHandler.FluidAction action
        ) {
            if (resource == null || resource.isEmpty() || !allowed.test(resource))
                return FluidStack.EMPTY;
            FluidStack drained = delegate.drain(resource, action);
            if (action.execute() && drained != null && !drained.isEmpty())
                capture.record(drained);
            return drained;
        }

        @Override
        public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            FluidStack drained = drainMatchingFluid(delegate, maxDrain, action, allowed);
            if (action.execute() && !drained.isEmpty())
                capture.record(drained);
            return drained;
        }

        @Override
        public void unmount(
                Level level,
                BlockState state,
                BlockPos pos,
                BlockEntity blockEntity
        ) {
            throw new UnsupportedOperationException("ephemeral fuel filter cannot be unmounted");
        }
    }

    private static final class LiquidFuelBridgeException extends RuntimeException {
        private LiquidFuelBridgeException(Throwable cause) {
            super(cause);
        }
    }
}
