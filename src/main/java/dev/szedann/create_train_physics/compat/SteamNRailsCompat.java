package dev.szedann.create_train_physics.compat;

import com.simibubi.create.content.trains.entity.Train;

import java.lang.reflect.Method;

/** Optional bridge for the 1.21.1 Steam 'n' Rails handcar. */
public final class SteamNRailsCompat {
    private static final String HANDCAR_INTERFACE =
            "com.railwayteam.railways.mixin_interfaces.IHandcarTrain";

    private static volatile Method isHandcarMethod;
    private static volatile boolean unavailable;

    private SteamNRailsCompat() {
    }

    public static boolean isHandcar(Train train) {
        if (train == null || unavailable)
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
            unavailable = true;
            return false;
        } catch (ReflectiveOperationException | LinkageError exception) {
            unavailable = true;
            return false;
        }
    }
}
