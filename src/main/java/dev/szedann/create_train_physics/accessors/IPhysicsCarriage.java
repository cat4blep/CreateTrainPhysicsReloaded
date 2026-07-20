package dev.szedann.create_train_physics.accessors;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface IPhysicsCarriage {
    @Nullable Integer railways$getMass();
    void railways$setMass(int mass);
    @Nullable Integer trainphys$getEngineCount();
    void trainphys$setEngineCount(int engineCount);
    @Nullable Integer trainphys$getElectricEngineCount();
    void trainphys$setElectricEngineCount(int engineCount);
    @Nullable Integer trainphys$getUnverifiedElectricEngineCount();
    void trainphys$setUnverifiedElectricEngineCount(int engineCount);
    @Nullable Map<String, Integer> trainphys$getCombustionEngineCounts();
    void trainphys$setCombustionEngineCounts(Map<String, Integer> engineCounts);
    @Nullable Map<BlockPos, String> trainphys$getFuelStorageNames();
    void trainphys$setFuelStorageNames(Map<BlockPos, String> storageNames);
    void trainphys$markEngineCountsForRefresh();
}
