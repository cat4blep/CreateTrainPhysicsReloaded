package dev.szedann.create_train_physics.physics;

public final class AutomaticHandbrakePolicy {
    private AutomaticHandbrakePolicy() {
    }

    public enum Action {
        NONE,
        HOLD,
        SERVICE_BRAKE
    }

    public static Action actionFor(
            boolean enabled,
            boolean wasManuallyControlled,
            boolean unattended,
            boolean navigationRequestsStop,
            boolean stoppedBeforePassiveForces
    ) {
        if (!enabled || wasManuallyControlled)
            return Action.NONE;
        if ((unattended || navigationRequestsStop) && stoppedBeforePassiveForces)
            return Action.HOLD;
        return unattended ? Action.SERVICE_BRAKE : Action.NONE;
    }
}
