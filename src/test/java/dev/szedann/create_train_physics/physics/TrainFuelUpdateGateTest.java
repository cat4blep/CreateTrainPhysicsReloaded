package dev.szedann.create_train_physics.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainFuelUpdateGateTest {
    @Test
    void permitsOnlyOnePassPerServerTick() {
        TrainFuelUpdateGate gate = new TrainFuelUpdateGate();

        assertTrue(gate.enter(42));
        assertFalse(gate.enter(42));
        assertTrue(gate.enter(43));
        assertFalse(gate.enter(43));
    }

    @Test
    void firstPassWorksForEveryIntegerTickValue() {
        TrainFuelUpdateGate gate = new TrainFuelUpdateGate();

        assertTrue(gate.enter(Integer.MIN_VALUE));
        assertTrue(gate.enter(Integer.MAX_VALUE));
        assertTrue(gate.enter(Integer.MIN_VALUE));
    }
}
