package dev.szedann.create_train_physics.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import dev.szedann.create_train_physics.accessors.IPhysicsCarriage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.szedann.create_train_physics.CreateTrainPhysics.CEE_MOTOR_TAG;
import static dev.szedann.create_train_physics.CreateTrainPhysics.MOTOR_TAG;


@Mixin(value = Carriage.class, remap = false)
public abstract class MixinCarriage implements IPhysicsCarriage {
    @Unique
    private static final int TRAINPHYS_ENGINE_COUNT_VERSION = 2;

    @Shadow
    public abstract CarriageContraptionEntity anyAvailableEntity();

    @Unique
    private @Nullable Integer railways$mass = null;
    @Unique
    private @Nullable Integer trainphys$engineCount = null;
    @Unique
    private @Nullable Integer trainphys$electricEngineCount = null;
    @Unique
    private boolean trainphys$engineCountsNeedRefresh = false;

    @Inject(method = "write", at = @At("RETURN"))
    private void writeMassAndEngineCount(DimensionPalette dimensions, HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir){
        CompoundTag tag = cir.getReturnValue();
        Integer mass = railways$getMass();
        if(mass != null)
            tag.putInt("mass", mass);
        Integer engineCount = trainphys$getEngineCount();
        if(engineCount != null)
            tag.putInt("engineCount", engineCount);
        Integer electricEngineCount = trainphys$getElectricEngineCount();
        if(electricEngineCount != null)
            tag.putInt("electricEngineCount", electricEngineCount);
        if (!trainphys$engineCountsNeedRefresh
                && engineCount != null
                && electricEngineCount != null)
            tag.putInt("engineCountVersion", TRAINPHYS_ENGINE_COUNT_VERSION);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void readMassAndEngineCount(CompoundTag tag, HolderLookup.Provider registries, TrackGraph graph, DimensionPalette dimensions, CallbackInfoReturnable<Carriage> cir) {
        IPhysicsCarriage carriage = (IPhysicsCarriage) cir.getReturnValue();

        if(tag.contains("mass", CompoundTag.TAG_INT))
            carriage.railways$setMass(tag.getInt("mass"));
        if(tag.contains("engineCount", CompoundTag.TAG_INT)) {
            int engineCount = tag.getInt("engineCount");
            carriage.trainphys$setEngineCount(engineCount);
            if(tag.contains("electricEngineCount", CompoundTag.TAG_INT))
                carriage.trainphys$setElectricEngineCount(tag.getInt("electricEngineCount"));
            else
                // C:EE has persisted this boolean since its first release.
                // Treating all legacy engines as electric is a safe fallback
                // until a carriage entity is present and can be rescanned.
                carriage.trainphys$setElectricEngineCount(
                        tag.getBoolean("CEEHasElectricMotor") ? engineCount : 0
                );
            // Persisted values remain a fallback for unloaded trains, but an
            // available entity is rescanned once so datapack tag changes and
            // add-on updates affect existing consists after a restart.
            carriage.trainphys$markEngineCountsForRefresh();
        }
    }

    @Override
    public @Nullable Integer railways$getMass(){
        if(railways$mass == null || railways$mass == 0) {
            CarriageContraptionEntity entity = anyAvailableEntity();
            if(entity != null && entity.getContraption() != null)
                railways$mass = entity.getContraption().getBlocks().size();
        }
        return railways$mass;
    }
    @Override
    public void railways$setMass(int mass){
        railways$mass = mass;
    }

    @Override
    public @Nullable Integer trainphys$getEngineCount() {
        trainphys$scanEnginesIfNeeded();
        return trainphys$engineCount;
    }

    @Override
    public void trainphys$setEngineCount(int engineCount) {
        trainphys$engineCount = engineCount;
    }

    @Override
    public @Nullable Integer trainphys$getElectricEngineCount() {
        trainphys$scanEnginesIfNeeded();
        return trainphys$electricEngineCount;
    }

    @Override
    public void trainphys$setElectricEngineCount(int engineCount) {
        trainphys$electricEngineCount = engineCount;
    }

    @Override
    public void trainphys$markEngineCountsForRefresh() {
        trainphys$engineCountsNeedRefresh = true;
    }

    @Unique
    private void trainphys$scanEnginesIfNeeded() {
        if (trainphys$engineCount != null
                && trainphys$electricEngineCount != null
                && !trainphys$engineCountsNeedRefresh)
            return;

        CarriageContraptionEntity entity = anyAvailableEntity();
        if (entity == null || entity.getContraption() == null)
            return;

        int engineCount = 0;
        int electricEngineCount = 0;
        for (var blockInfo : entity.getContraption().getBlocks().values()) {
            if (blockInfo.state().is(MOTOR_TAG))
                engineCount++;
            if (blockInfo.state().is(CEE_MOTOR_TAG))
                electricEngineCount++;
        }
        trainphys$engineCount = engineCount;
        trainphys$electricEngineCount = electricEngineCount;
        trainphys$engineCountsNeedRefresh = false;
    }
}
