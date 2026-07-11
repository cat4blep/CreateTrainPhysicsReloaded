package dev.szedann.create_train_physics.physics;

public final class TractionPolicy {
    private TractionPolicy() {
    }

    public static boolean requiresTraction(double speed, double targetSpeed) {
        if (speed == targetSpeed)
            return false;
        if (speed == 0)
            return targetSpeed != 0;
        return speed > 0 ? targetSpeed > speed : targetSpeed < speed;
    }

    public static double targetForTick(double speed, double requestedTarget, boolean tractionAvailable) {
        boolean reversing = speed > 0 && requestedTarget < 0
                || speed < 0 && requestedTarget > 0;
        double stepTarget = reversing ? 0 : requestedTarget;
        if (!tractionAvailable && requiresTraction(speed, stepTarget))
            return speed;
        return stepTarget;
    }
}
