package dev.szedann.create_train_physics.physics;

/** Pure matching rules for the optional named item-fuel storage filter. */
public final class FuelStorageNamePolicy {
    private FuelStorageNamePolicy() {
    }

    public static boolean allowsAll(String requiredName) {
        return "*".equals(requiredName);
    }

    public static boolean matches(String requiredName, String actualName) {
        return requiredName != null
                && !requiredName.isEmpty()
                && requiredName.equals(actualName);
    }
}
