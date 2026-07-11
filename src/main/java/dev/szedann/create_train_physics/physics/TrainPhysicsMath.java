package dev.szedann.create_train_physics.physics;

/** Numerically safe, unit-testable train-physics equations. */
public final class TrainPhysicsMath {
    private static final double GRAVITY = 9.81d;

    private TrainPhysicsMath() {
    }

    /**
     * Solve P = (rollingForce + 0.5 * dragConstant * v^2) * v for v.
     * Power is in watts, mass in kilograms, and the result in metres/second.
     */
    public static double powerLimitedSpeed(double power, double mass,
                                           double rollingCoefficient,
                                           double dragConstant) {
        if (!(power > 0) || !(mass > 0) || !(dragConstant > 0)
                || !Double.isFinite(power) || !Double.isFinite(mass)
                || !Double.isFinite(rollingCoefficient)
                || !Double.isFinite(dragConstant))
            return 0;

        double rollingForce = Math.max(0, rollingCoefficient) * mass * GRAVITY;
        double p = 2 * rollingForce / dragConstant;
        double q = -2 * power / dragConstant;
        double discriminant = q * q / 4 + p * p * p / 27;
        double root = Math.sqrt(Math.max(0, discriminant));
        double speed = Math.cbrt(-q / 2 + root) + Math.cbrt(-q / 2 - root);
        return Double.isFinite(speed) && speed > 0 ? speed : 0;
    }

    /**
     * Maximum constant tractive force over one time step without exceeding
     * the available average power. Unlike P/v, this remains finite at rest.
     */
    public static double powerLimitedForce(double power, double mass,
                                           double velocity, double seconds) {
        if (!(power > 0) || !(mass > 0) || !(seconds > 0)
                || !Double.isFinite(power) || !Double.isFinite(mass)
                || !Double.isFinite(velocity) || !Double.isFinite(seconds))
            return 0;

        double initialVelocity = Math.max(0, Math.abs(velocity));
        double finalVelocityAtFullPower = Math.sqrt(
                initialVelocity * initialVelocity + 2 * power * seconds / mass
        );
        double denominator = initialVelocity + finalVelocityAtFullPower;
        return denominator > 0 ? 2 * power / denominator : 0;
    }
}
