package dev.szedann.create_train_physics.accessors;

public interface IPhysicsTrain {
    void trainphys$setCombustionFuelActive(boolean active);

    void trainphys$setFuelEnergyDebt(double joules);

    void trainphys$setIsolatedCombustionFuelTicks(int ticks);
}
