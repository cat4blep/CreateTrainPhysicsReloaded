package dev.szedann.create_train_physics.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AutomaticHandbrakePolicyTest {
    @Test
    void releasedManualControlUsesServiceBrakeOnFollowingTick() {
        assertEquals(
                AutomaticHandbrakePolicy.Action.NONE,
                AutomaticHandbrakePolicy.actionFor(
                        true,
                        true,
                        true,
                        false,
                        false
                )
        );
        assertEquals(
                AutomaticHandbrakePolicy.Action.SERVICE_BRAKE,
                AutomaticHandbrakePolicy.actionFor(
                        true,
                        false,
                        true,
                        false,
                        false
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            "false, false, true,  false, false, NONE",
            "false, false, true,  false, true,  NONE",
            "true,  true,  true,  false, true,  NONE",
            "true,  false, true,  false, true,  HOLD",
            "true,  false, false, true,  true,  HOLD",
            "true,  false, false, true,  false, NONE",
            "true,  false, false, false, true,  NONE",
            "true,  false, false, false, false, NONE"
    })
    void selectsActionForControlAndParkingState(
            boolean enabled,
            boolean wasManuallyControlled,
            boolean unattended,
            boolean navigationRequestsStop,
            boolean stoppedBeforePassiveForces,
            AutomaticHandbrakePolicy.Action expected
    ) {
        assertEquals(
                expected,
                AutomaticHandbrakePolicy.actionFor(
                        enabled,
                        wasManuallyControlled,
                        unattended,
                        navigationRequestsStop,
                        stoppedBeforePassiveForces
                )
        );
    }
}
