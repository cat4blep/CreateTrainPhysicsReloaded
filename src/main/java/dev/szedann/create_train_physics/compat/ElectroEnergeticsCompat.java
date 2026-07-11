package dev.szedann.create_train_physics.compat;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.trains.entity.Train;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional, reflection-only integration with Create: Electro Energetics.
 *
 * <p>CEE does not currently expose a stable powered-train API. Its injected
 * train extension and electric train data are therefore resolved lazily so
 * this mod does not acquire a hard class-loading dependency on CEE. The two
 * voltage field names cover the published CEE releases before and after the
 * field was renamed in autobuild 112.</p>
 */
public final class ElectroEnergeticsCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String MOD_ID = "electroenergetics";
    private static final String CONFIGS_CLASS =
            "com.george_vi.electroenergetics.config.CEEConfigs";

    private static final Object ACCESSORS_LOCK = new Object();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private static volatile Accessors accessors;
    private static volatile boolean incompatible;

    private ElectroEnergeticsCompat() {
    }

    public enum PowerStatus {
        POWERED,
        UNPOWERED,
        UNKNOWN,
        NOT_APPLICABLE
    }

    /**
     * Returns CEE's current electrical power state for a train.
     *
     * <p>{@link PowerStatus#UNKNOWN} means CEE is present but its optional API
     * could not be read. Callers must fail closed so ordinary Create fuel can
     * never masquerade as electrical power. {@link PowerStatus#NOT_APPLICABLE}
     * means CEE is not loaded.</p>
     */
    public static PowerStatus getPowerStatus(Train train) {
        if (!ModList.get().isLoaded(MOD_ID))
            return PowerStatus.NOT_APPLICABLE;
        if (train == null || incompatible)
            return PowerStatus.UNKNOWN;

        try {
            Accessors resolved = getAccessors(train);
            if (resolved == null)
                return PowerStatus.UNKNOWN;

            Object trainData = resolved.getElectricTrainData.invoke(train);
            Object serverConfig = resolved.getServerConfig.invoke(null);
            if (trainData == null || serverConfig == null)
                return PowerStatus.UNKNOWN;

            boolean hasCreativeSource = resolved.hasCreativeSource.getBoolean(trainData);
            double accumulatorCharge = resolved.accumulatorCharge.getDouble(trainData);
            double voltage = Math.abs(resolved.voltage.getDouble(trainData));

            Object voltageValues = resolved.voltageValues.get(serverConfig);
            if (voltageValues == null)
                return PowerStatus.UNKNOWN;
            Object minimumVoltageValue = resolved.trainMinimumVoltage.get(voltageValues);
            if (minimumVoltageValue == null)
                return PowerStatus.UNKNOWN;
            Object configuredMinimum = resolved.configValueGet.invoke(minimumVoltageValue);
            if (!(configuredMinimum instanceof Number minimumVoltage))
                throw new IllegalStateException("CEE trainMinVoltage did not return a number");

            boolean powered = hasCreativeSource
                    || accumulatorCharge > 0.0d
                    || voltage > minimumVoltage.doubleValue();
            return powered ? PowerStatus.POWERED : PowerStatus.UNPOWERED;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            markIncompatible(exception);
            return PowerStatus.UNKNOWN;
        }
    }

    /** Return true only when C:EE itself positively reports usable power. */
    public static boolean isPowered(Train train) {
        return getPowerStatus(train) == PowerStatus.POWERED;
    }

    private static Accessors getAccessors(Train train)
            throws ReflectiveOperationException {
        Accessors resolved = accessors;
        if (resolved != null)
            return resolved;

        synchronized (ACCESSORS_LOCK) {
            resolved = accessors;
            if (resolved != null)
                return resolved;

            Method getElectricTrainData = train.getClass().getMethod("getElectricTrainData");
            Object trainData = getElectricTrainData.invoke(train);
            if (trainData == null)
                return null;

            Class<?> trainDataClass = trainData.getClass();
            Field hasCreativeSource = trainDataClass.getField("hasCreativeSource");
            Field accumulatorCharge = trainDataClass.getField("accumulatorCharge");
            Field voltage = findVoltageField(trainDataClass);

            ClassLoader loader = train.getClass().getClassLoader();
            Class<?> configsClass = Class.forName(CONFIGS_CLASS, false, loader);
            Method getServerConfig = configsClass.getMethod("server");
            Object serverConfig = getServerConfig.invoke(null);
            if (serverConfig == null)
                return null;

            Field voltageValues = serverConfig.getClass().getField("voltageValues");
            Object voltageValuesObject = voltageValues.get(serverConfig);
            if (voltageValuesObject == null)
                return null;

            Field trainMinimumVoltage =
                    voltageValuesObject.getClass().getField("trainMinVoltage");
            Object minimumVoltageValue = trainMinimumVoltage.get(voltageValuesObject);
            if (minimumVoltageValue == null)
                return null;
            Method configValueGet = minimumVoltageValue.getClass().getMethod("get");

            resolved = new Accessors(
                    getElectricTrainData,
                    hasCreativeSource,
                    accumulatorCharge,
                    voltage,
                    getServerConfig,
                    voltageValues,
                    trainMinimumVoltage,
                    configValueGet
            );
            accessors = resolved;
            return resolved;
        }
    }

    private static Field findVoltageField(Class<?> trainDataClass)
            throws NoSuchFieldException {
        try {
            return trainDataClass.getField("lastVoltage");
        } catch (NoSuchFieldException ignored) {
            return trainDataClass.getField("displayVoltage");
        }
    }

    private static void markIncompatible(Throwable exception) {
        incompatible = true;
        if (WARNED.compareAndSet(false, true)) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    && invocation.getCause() != null
                    ? invocation.getCause()
                    : exception;
            LOGGER.warn(
                    "Could not inspect Create: Electro Energetics train power; "
                            + "electric traction will remain disabled until a compatible version is loaded",
                    cause
            );
        }
    }

    private record Accessors(
            Method getElectricTrainData,
            Field hasCreativeSource,
            Field accumulatorCharge,
            Field voltage,
            Method getServerConfig,
            Field voltageValues,
            Field trainMinimumVoltage,
            Method configValueGet
    ) {
    }
}
