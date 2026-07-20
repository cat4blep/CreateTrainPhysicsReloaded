package dev.szedann.create_train_physics.physics;

/** Allows one fuel acquisition/update pass for a train in each server tick. */
public final class TrainFuelUpdateGate {
    private boolean initialized;
    private int lastTick;

    public boolean enter(int serverTick) {
        if (initialized && lastTick == serverTick)
            return false;
        initialized = true;
        lastTick = serverTick;
        return true;
    }
}
