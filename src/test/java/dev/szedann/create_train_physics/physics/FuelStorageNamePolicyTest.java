package dev.szedann.create_train_physics.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuelStorageNamePolicyTest {
    @Test
    void onlyLiteralAsteriskDisablesTheFilter() {
        assertTrue(FuelStorageNamePolicy.allowsAll("*"));
        assertFalse(FuelStorageNamePolicy.allowsAll(""));
        assertFalse(FuelStorageNamePolicy.allowsAll(null));
    }

    @Test
    void exactNameMatchingIsCaseSensitiveAndFailsClosed() {
        assertTrue(FuelStorageNamePolicy.matches("Train Fuel", "Train Fuel"));
        assertFalse(FuelStorageNamePolicy.matches("Train Fuel", "train fuel"));
        assertFalse(FuelStorageNamePolicy.matches("", ""));
        assertFalse(FuelStorageNamePolicy.matches(null, "Train Fuel"));
        assertFalse(FuelStorageNamePolicy.matches("Train Fuel", null));
    }
}
