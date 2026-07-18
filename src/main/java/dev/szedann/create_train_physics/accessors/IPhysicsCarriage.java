package dev.szedann.create_train_physics.accessors;

import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface IPhysicsCarriage {
    @Nullable Integer railways$getMass();
    void railways$setMass(int mass);
    @Nullable Integer trainphys$getEngineCount();
    void trainphys$setEngineCount(int engineCount);
    @Nullable Integer trainphys$getElectricEngineCount();
    void trainphys$setElectricEngineCount(int engineCount);
    @Nullable Integer trainphys$getUnverifiedElectricEngineCount();
    void trainphys$setUnverifiedElectricEngineCount(int engineCount);
    @Nullable Set<Block> trainphys$getEngineBlocks();
    void trainphys$setEngineBlocks(Set<Block> engineBlocks);
    void trainphys$markEngineCountsForRefresh();
}