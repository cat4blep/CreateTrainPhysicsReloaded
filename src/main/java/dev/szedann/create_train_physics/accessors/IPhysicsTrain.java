package dev.szedann.create_train_physics.accessors;

import dev.szedann.create_train_physics.physics.EngineFuelState;

public interface IPhysicsTrain {
    void trainphys$setEngineFuelState(EngineFuelState state);
    int trainphys$getPowerForSync();
    void trainphys$setSyncedPower(int powerWatts);
}
