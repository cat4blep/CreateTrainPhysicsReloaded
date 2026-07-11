package dev.szedann.create_train_physics.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TractionPolicyTest {
    @Test
    void handlesSubnormalSpeedsWithoutMultiplicationUnderflow() {
        double tiny = Double.MIN_VALUE;

        assertTrue(TractionPolicy.requiresTraction(tiny, 2 * tiny));
        assertTrue(TractionPolicy.requiresTraction(-tiny, -2 * tiny));
        assertEquals(0.0, TractionPolicy.targetForTick(tiny, -tiny, false));
        assertEquals(0.0, TractionPolicy.targetForTick(-tiny, tiny, false));
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, 1.0, true",
            "0.0, -1.0, true",
            "0.25, 0.5, true",
            "-0.25, -0.5, true",
            "0.5, 0.25, false",
            "-0.5, -0.25, false",
            "0.5, 0.0, false",
            "-0.5, 0.0, false",
            "0.5, -0.5, false",
            "-0.5, 0.5, false",
            "0.5, 0.5, false",
            "0.0, 0.0, false"
    })
    void classifiesTractionSeparatelyFromBraking(double speed, double targetSpeed, boolean expected) {
        assertEquals(expected, TractionPolicy.requiresTraction(speed, targetSpeed));
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, 0.5, false, 0.0",
            "0.0, -0.5, false, 0.0",
            "0.2, 0.5, false, 0.2",
            "-0.2, -0.5, false, -0.2",
            "0.5, 0.2, false, 0.2",
            "-0.5, -0.2, false, -0.2",
            "0.5, 0.0, false, 0.0",
            "-0.5, 0.0, false, 0.0",
            "0.5, 0.5, false, 0.5",
            "-0.5, -0.5, false, -0.5",
            "0.5, -0.5, false, 0.0",
            "-0.5, 0.5, false, 0.0",
            "0.0, 0.5, true, 0.5",
            "0.0, -0.5, true, -0.5",
            "0.2, 0.5, true, 0.5",
            "-0.2, -0.5, true, -0.5",
            "0.5, -0.5, true, 0.0",
            "-0.5, 0.5, true, 0.0"
    })
    void choosesReachableTargetForCurrentTick(double speed, double requestedTarget,
                                               boolean tractionAvailable, double expected) {
        assertEquals(expected, TractionPolicy.targetForTick(speed, requestedTarget, tractionAvailable));
    }
}
