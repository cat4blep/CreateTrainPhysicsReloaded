package dev.szedann.create_train_physics.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainPhysicsMathTest {
    @Test
    void returnsZeroWithoutUsablePowerOrMass() {
        assertEquals(0, TrainPhysicsMath.powerLimitedSpeed(0, 1_000, 0.001, 3.8));
        assertEquals(0, TrainPhysicsMath.powerLimitedSpeed(100_000, 0, 0.001, 3.8));
    }

    @Test
    void matchesDragOnlyClosedForm() {
        double speed = TrainPhysicsMath.powerLimitedSpeed(200_000, 1_000, 0, 4);
        assertEquals(Math.cbrt(100_000), speed, 1.0e-9);
    }

    @Test
    void satisfiesPowerBalanceWithRollingResistance() {
        double power = 200_000;
        double mass = 20_000;
        double coefficient = 0.001;
        double drag = 3.7926;
        double speed = TrainPhysicsMath.powerLimitedSpeed(power, mass, coefficient, drag);
        double requiredPower = (coefficient * mass * 9.81 + 0.5 * drag * speed * speed) * speed;

        assertTrue(speed > 0);
        assertEquals(power, requiredPower, power * 1.0e-10);
    }

    @Test
    void powerLimitedForceIsFiniteAtRestAndRespectsTickEnergy() {
        double power = 200_000;
        double mass = 20_000;
        double seconds = 1d / 20d;
        double force = TrainPhysicsMath.powerLimitedForce(power, mass, 0, seconds);
        double finalVelocity = force / mass * seconds;
        double averagePower = force * finalVelocity / 2;

        assertTrue(Double.isFinite(force));
        assertTrue(force > 0);
        assertEquals(power, averagePower, power * 1.0e-12);
    }
}
