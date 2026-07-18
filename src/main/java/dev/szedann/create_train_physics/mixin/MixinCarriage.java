package dev.szedann.create_train_physics.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import dev.szedann.create_train_physics.accessors.IPhysicsCarriage;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

import static dev.szedann.create_train_physics.CreateTrainPhysics.CEE_MOTOR_TAG;
import static dev.szedann.create_train_physics.CreateTrainPhysics.MOTOR_TAG;
import static dev.szedann.create_train_physics.CreateTrainPhysics.UNVERIFIED_ELECTRIC_MOTOR_TAG;


@Mixin(value = Carriage.class, remap = false)
public abstract class MixinCarriage implements IPhysicsCarriage {
    @Unique
    private static final int TRAINPHYS_ENGINE_COUNT_VERSION = 4;

    @Shadow
    public abstract CarriageContraptionEntity anyAvailableEntity();

    @Unique
    private @Nullable Integer railways$mass = null;
    @Unique
    private @Nullable Integer trainphys$engineCount = null;
    @Unique
    private @Nullable Integer trainphys$electricEngineCount = null;
    @Unique
    private @Nullable Integer trainphys$unverifiedElectricEngineCount = null;
    @Unique
    private @Nullable Set<Block> trainphys$combustionEngineBlocks = null;
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
        Integer unverifiedElectricEngineCount = trainphys$getUnverifiedElectricEngineCount();
        if(unverifiedElectricEngineCount != null)
            tag.putInt("unverifiedElectricEngineCount", unverifiedElectricEngineCount);
        Set<Block> engineBlocks = trainphys$getEngineBlocks();
        if (engineBlocks != null) {
            ListTag engineBlockList = new ListTag();
            for (Block block : engineBlocks)
                BuiltInRegistries.BLOCK.getResourceKey(block).ifPresent(key ->
                        engineBlockList.add(StringTag.valueOf(key.location().toString())));
            tag.put("combustionEngineBlocks", engineBlockList);
        }
        if (!trainphys$engineCountsNeedRefresh
                && engineCount != null
                && electricEngineCount != null
                && unverifiedElectricEngineCount != null
                && engineBlocks != null)
            tag.putInt("engineCountVersion", TRAINPHYS_ENGINE_COUNT_VERSION);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void readMassAndEngineCount(CompoundTag tag, HolderLookup.Provider registries, TrackGraph graph, DimensionPalette dimensions, CallbackInfoReturnable<Carriage> cir) {
        IPhysicsCarriage carriage = (IPhysicsCarriage) cir.getReturnValue();

        if(tag.contains("mass", CompoundTag.TAG_INT))
            carriage.railways$setMass(tag.getInt("mass"));
        boolean restoredFromContraption = trainphys$restoreSerializedEngineCounts(
                tag,
                registries,
                carriage
        );
        if(!restoredFromContraption && tag.contains("engineCount", CompoundTag.TAG_INT)) {
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
            carriage.trainphys$setUnverifiedElectricEngineCount(
                    tag.contains("unverifiedElectricEngineCount", CompoundTag.TAG_INT)
                            ? tag.getInt("unverifiedElectricEngineCount")
                            : 0
            );
            if (tag.contains("combustionEngineBlocks", CompoundTag.TAG_LIST)) {
                ListTag engineBlockList = tag.getList("combustionEngineBlocks", Tag.TAG_STRING);
                Set<Block> engineBlocks = new HashSet<>();
                for (int i = 0; i < engineBlockList.size(); i++) {
                    ResourceLocation id = ResourceLocation.tryParse(engineBlockList.getString(i));
                    // An engine whose mod was removed also vanishes from the
                    // contraption itself; the entity rescan settles any drift.
                    if (id != null && BuiltInRegistries.BLOCK.containsKey(id))
                        engineBlocks.add(BuiltInRegistries.BLOCK.get(id));
                }
                carriage.trainphys$setEngineBlocks(engineBlocks);
            }
        }
        if(restoredFromContraption || tag.contains("engineCount", CompoundTag.TAG_INT)) {
            // Exact serialized counts keep legacy automated trains moving even
            // while their chunks are unloaded. Rescan once an entity appears
            // so live datapack tag changes still take effect.
            carriage.trainphys$markEngineCountsForRefresh();
        }
    }

    @Unique
    private static boolean trainphys$restoreSerializedEngineCounts(
            CompoundTag carriageTag,
            HolderLookup.Provider registries,
            IPhysicsCarriage carriage
    ) {
        try {
            Tag blocksTag = carriageTag.getCompound("Entity")
                    .getCompound("Contraption")
                    .get("Blocks");
            HolderGetter<Block> blockLookup = registries.lookupOrThrow(Registries.BLOCK);
            int[] counts = new int[3];
            Set<Block> combustionEngines = new HashSet<>();

            if (blocksTag instanceof CompoundTag palettedBlocks) {
                if (!palettedBlocks.contains("Palette", Tag.TAG_LIST)
                        || !palettedBlocks.contains("BlockList", Tag.TAG_LIST))
                    return false;

                ListTag paletteTag = palettedBlocks.getList("Palette", Tag.TAG_COMPOUND);
                ListTag blockList = palettedBlocks.getList("BlockList", Tag.TAG_COMPOUND);
                if (paletteTag.isEmpty() || blockList.isEmpty())
                    return false;

                BlockState[] palette = new BlockState[paletteTag.size()];
                for (int i = 0; i < paletteTag.size(); i++)
                    palette[i] = NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i));

                for (int i = 0; i < blockList.size(); i++) {
                    int stateId = blockList.getCompound(i).getInt("State");
                    if (stateId < 0 || stateId >= palette.length)
                        return false;
                    trainphys$countEngineState(palette[stateId], counts, combustionEngines);
                }
            } else if (blocksTag instanceof ListTag legacyBlocks) {
                if (legacyBlocks.isEmpty())
                    return false;
                for (int i = 0; i < legacyBlocks.size(); i++) {
                    CompoundTag block = legacyBlocks.getCompound(i);
                    if (!block.contains("Block", Tag.TAG_COMPOUND))
                        return false;
                    trainphys$countEngineState(
                            NbtUtils.readBlockState(blockLookup, block.getCompound("Block")),
                            counts,
                            combustionEngines
                    );
                }
            } else {
                return false;
            }

            carriage.trainphys$setEngineCount(counts[0]);
            carriage.trainphys$setElectricEngineCount(counts[1]);
            carriage.trainphys$setUnverifiedElectricEngineCount(counts[2]);
            carriage.trainphys$setEngineBlocks(combustionEngines);
            return true;
        } catch (RuntimeException exception) {
            // Corrupt or foreign serialized data falls back to persisted v2/v3
            // counts and is rescanned when a carriage entity becomes available.
            return false;
        }
    }

    @Unique
    private static void trainphys$countEngineState(
            BlockState state,
            int[] counts,
            Set<Block> combustionEngines
    ) {
        if (!state.is(MOTOR_TAG))
            return;
        counts[0]++;
        if (state.is(CEE_MOTOR_TAG))
            counts[1]++;
        else if (state.is(UNVERIFIED_ELECTRIC_MOTOR_TAG))
            counts[2]++;
        else
            combustionEngines.add(state.getBlock());
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
    public @Nullable Integer trainphys$getUnverifiedElectricEngineCount() {
        trainphys$scanEnginesIfNeeded();
        return trainphys$unverifiedElectricEngineCount;
    }

    @Override
    public void trainphys$setUnverifiedElectricEngineCount(int engineCount) {
        trainphys$unverifiedElectricEngineCount = engineCount;
    }

    @Override
    public @Nullable Set<Block> trainphys$getEngineBlocks() {
        trainphys$scanEnginesIfNeeded();
        return trainphys$combustionEngineBlocks;
    }

    @Override
    public void trainphys$setEngineBlocks(Set<Block> engineBlocks) {
        trainphys$combustionEngineBlocks = engineBlocks;
    }

    @Override
    public void trainphys$markEngineCountsForRefresh() {
        trainphys$engineCountsNeedRefresh = true;
    }

    @Unique
    private void trainphys$scanEnginesIfNeeded() {
        if (trainphys$engineCount != null
                && trainphys$electricEngineCount != null
                && trainphys$unverifiedElectricEngineCount != null
                && trainphys$combustionEngineBlocks != null
                && !trainphys$engineCountsNeedRefresh)
            return;

        CarriageContraptionEntity entity = anyAvailableEntity();
        if (entity == null || entity.getContraption() == null)
            return;

        int engineCount = 0;
        int electricEngineCount = 0;
        int unverifiedElectricEngineCount = 0;
        Set<Block> combustionEngineBlocks = new HashSet<>();
        for (var blockInfo : entity.getContraption().getBlocks().values()) {
            if (!blockInfo.state().is(MOTOR_TAG))
                continue;
            engineCount++;
            if (blockInfo.state().is(CEE_MOTOR_TAG)) {
                electricEngineCount++;
            } else if (blockInfo.state().is(UNVERIFIED_ELECTRIC_MOTOR_TAG)) {
                unverifiedElectricEngineCount++;
            } else {
                combustionEngineBlocks.add(blockInfo.state().getBlock());
            }
        }
        trainphys$engineCount = engineCount;
        trainphys$electricEngineCount = electricEngineCount;
        trainphys$unverifiedElectricEngineCount = unverifiedElectricEngineCount;
        trainphys$combustionEngineBlocks = combustionEngineBlocks;
        trainphys$engineCountsNeedRefresh = false;
    }
}