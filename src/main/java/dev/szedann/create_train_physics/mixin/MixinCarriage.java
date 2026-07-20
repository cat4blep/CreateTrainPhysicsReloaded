package dev.szedann.create_train_physics.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import dev.szedann.create_train_physics.accessors.IPhysicsCarriage;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static dev.szedann.create_train_physics.CreateTrainPhysics.CEE_MOTOR_TAG;
import static dev.szedann.create_train_physics.CreateTrainPhysics.MOTOR_TAG;
import static dev.szedann.create_train_physics.CreateTrainPhysics.UNVERIFIED_ELECTRIC_MOTOR_TAG;

@Mixin(value = Carriage.class, remap = false)
public abstract class MixinCarriage implements IPhysicsCarriage {
    @Unique
    private static final int TRAINPHYS_ENGINE_COUNT_VERSION = 5;

    @Shadow
    public abstract CarriageContraptionEntity anyAvailableEntity();

    @Unique
    private @Nullable Integer railways$mass;
    @Unique
    private @Nullable Integer trainphys$engineCount;
    @Unique
    private @Nullable Integer trainphys$electricEngineCount;
    @Unique
    private @Nullable Integer trainphys$unverifiedElectricEngineCount;
    @Unique
    private @Nullable Map<String, Integer> trainphys$combustionEngineCounts;
    @Unique
    private @Nullable Map<BlockPos, String> trainphys$fuelStorageNames;
    @Unique
    private boolean trainphys$engineCountsNeedRefresh;

    @Inject(method = "write", at = @At("RETURN"))
    private void writeMassAndEngineCount(
            DimensionPalette dimensions,
            HolderLookup.Provider registries,
            CallbackInfoReturnable<CompoundTag> cir
    ) {
        CompoundTag tag = cir.getReturnValue();
        Integer mass = railways$getMass();
        if (mass != null)
            tag.putInt("mass", mass);
        Integer engineCount = trainphys$getEngineCount();
        if (engineCount != null)
            tag.putInt("engineCount", engineCount);
        Integer electricEngineCount = trainphys$getElectricEngineCount();
        if (electricEngineCount != null)
            tag.putInt("electricEngineCount", electricEngineCount);
        Integer unverifiedElectricEngineCount = trainphys$getUnverifiedElectricEngineCount();
        if (unverifiedElectricEngineCount != null)
            tag.putInt("unverifiedElectricEngineCount", unverifiedElectricEngineCount);

        Map<String, Integer> combustionCounts = trainphys$getCombustionEngineCounts();
        if (combustionCounts != null)
            tag.put("combustionEngineCounts", trainphys$writeEngineCounts(combustionCounts));
        Map<BlockPos, String> storageNames = trainphys$getFuelStorageNames();
        if (storageNames != null)
            tag.put("fuelStorageNames", trainphys$writeStorageNames(storageNames));

        if (!trainphys$engineCountsNeedRefresh
                && engineCount != null
                && electricEngineCount != null
                && unverifiedElectricEngineCount != null
                && combustionCounts != null
                && storageNames != null)
            tag.putInt("engineCountVersion", TRAINPHYS_ENGINE_COUNT_VERSION);
    }

    @Unique
    private static ListTag trainphys$writeEngineCounts(Map<String, Integer> counts) {
        ListTag list = new ListTag();
        counts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putString("id", entry.getKey());
                    value.putInt("count", entry.getValue());
                    list.add(value);
                });
        return list;
    }

    @Unique
    private static ListTag trainphys$writeStorageNames(Map<BlockPos, String> names) {
        ListTag list = new ListTag();
        names.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey(Comparator.comparingLong(BlockPos::asLong)))
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putLong("pos", entry.getKey().asLong());
                    value.putString("name", entry.getValue());
                    list.add(value);
                });
        return list;
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void readMassAndEngineCount(
            CompoundTag tag,
            HolderLookup.Provider registries,
            TrackGraph graph,
            DimensionPalette dimensions,
            CallbackInfoReturnable<Carriage> cir
    ) {
        IPhysicsCarriage carriage = (IPhysicsCarriage) cir.getReturnValue();
        if (tag.contains("mass", Tag.TAG_INT))
            carriage.railways$setMass(tag.getInt("mass"));

        boolean restoredFromContraption = trainphys$restoreSerializedEngineMetadata(
                tag,
                registries,
                carriage
        );
        if (!restoredFromContraption)
            trainphys$restorePersistedMetadata(tag, carriage);

        if (restoredFromContraption || tag.contains("engineCount", Tag.TAG_INT)) {
            // Keep saved trains functional while unloaded, then refresh once a
            // carriage entity is available so current tags and names win.
            carriage.trainphys$markEngineCountsForRefresh();
        }
    }

    @Unique
    private static void trainphys$restorePersistedMetadata(
            CompoundTag tag,
            IPhysicsCarriage carriage
    ) {
        if (tag.contains("engineCount", Tag.TAG_INT)) {
            int engineCount = tag.getInt("engineCount");
            carriage.trainphys$setEngineCount(engineCount);
            carriage.trainphys$setElectricEngineCount(
                    tag.contains("electricEngineCount", Tag.TAG_INT)
                            ? tag.getInt("electricEngineCount")
                            : tag.getBoolean("CEEHasElectricMotor") ? engineCount : 0
            );
            carriage.trainphys$setUnverifiedElectricEngineCount(
                    tag.contains("unverifiedElectricEngineCount", Tag.TAG_INT)
                            ? tag.getInt("unverifiedElectricEngineCount")
                            : 0
            );
        }

        if (tag.contains("combustionEngineCounts", Tag.TAG_LIST)) {
            Map<String, Integer> counts = new HashMap<>();
            ListTag list = tag.getList("combustionEngineCounts", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag value = list.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(value.getString("id"));
                int count = value.getInt("count");
                if (id != null && count > 0 && BuiltInRegistries.BLOCK.containsKey(id))
                    counts.merge(id.toString(), count, MixinCarriage::trainphys$saturatedAdd);
            }
            carriage.trainphys$setCombustionEngineCounts(counts);
        }

        if (tag.contains("fuelStorageNames", Tag.TAG_LIST)) {
            Map<BlockPos, String> names = new HashMap<>();
            ListTag list = tag.getList("fuelStorageNames", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag value = list.getCompound(i);
                String name = value.getString("name");
                if (!name.isEmpty())
                    names.put(BlockPos.of(value.getLong("pos")), name);
            }
            carriage.trainphys$setFuelStorageNames(names);
        }
    }

    @Unique
    private static boolean trainphys$restoreSerializedEngineMetadata(
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
            Map<String, Integer> combustionCounts = new HashMap<>();
            Map<BlockPos, String> storageNames = new HashMap<>();

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
                    CompoundTag block = blockList.getCompound(i);
                    int stateId = block.getInt("State");
                    if (stateId < 0 || stateId >= palette.length)
                        return false;
                    trainphys$inspectBlock(
                            palette[stateId],
                            BlockPos.of(block.getLong("Pos")),
                            block.contains("Data", Tag.TAG_COMPOUND) ? block.getCompound("Data") : null,
                            counts,
                            combustionCounts,
                            storageNames,
                            registries
                    );
                }
            } else if (blocksTag instanceof ListTag legacyBlocks) {
                if (legacyBlocks.isEmpty())
                    return false;
                for (int i = 0; i < legacyBlocks.size(); i++) {
                    CompoundTag block = legacyBlocks.getCompound(i);
                    if (!block.contains("Block", Tag.TAG_COMPOUND))
                        return false;
                    trainphys$inspectBlock(
                            NbtUtils.readBlockState(blockLookup, block.getCompound("Block")),
                            NBTHelper.readBlockPos(block, "Pos"),
                            block.contains("Data", Tag.TAG_COMPOUND) ? block.getCompound("Data") : null,
                            counts,
                            combustionCounts,
                            storageNames,
                            registries
                    );
                }
            } else {
                return false;
            }

            carriage.trainphys$setEngineCount(counts[0]);
            carriage.trainphys$setElectricEngineCount(counts[1]);
            carriage.trainphys$setUnverifiedElectricEngineCount(counts[2]);
            carriage.trainphys$setCombustionEngineCounts(combustionCounts);
            carriage.trainphys$setFuelStorageNames(storageNames);
            return true;
        } catch (RuntimeException exception) {
            // Corrupt or foreign serialized data falls back to persisted
            // metadata and is rescanned once an entity becomes available.
            return false;
        }
    }

    @Unique
    private static void trainphys$inspectBlock(
            BlockState state,
            BlockPos pos,
            @Nullable CompoundTag blockEntityData,
            int[] counts,
            Map<String, Integer> combustionCounts,
            Map<BlockPos, String> storageNames,
            HolderLookup.Provider registries
    ) {
        if (state.is(MOTOR_TAG)) {
            counts[0]++;
            if (state.is(CEE_MOTOR_TAG)) {
                counts[1]++;
            } else if (state.is(UNVERIFIED_ELECTRIC_MOTOR_TAG)) {
                counts[2]++;
            } else {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                combustionCounts.merge(id.toString(), 1, MixinCarriage::trainphys$saturatedAdd);
            }
        }
        trainphys$rememberStorageName(pos, blockEntityData, storageNames, registries);
    }

    @Unique
    private static void trainphys$rememberStorageName(
            BlockPos pos,
            @Nullable CompoundTag blockEntityData,
            Map<BlockPos, String> storageNames,
            HolderLookup.Provider registries
    ) {
        if (blockEntityData == null || !blockEntityData.contains("CustomName", Tag.TAG_STRING))
            return;
        try {
            Component name = Component.Serializer.fromJson(
                    blockEntityData.getString("CustomName"),
                    registries
            );
            if (name != null && !name.getString().isEmpty())
                storageNames.put(pos, name.getString());
        } catch (RuntimeException ignored) {
            // Malformed external block-entity data must fail closed.
        }
    }

    @Unique
    private static int trainphys$saturatedAdd(int left, int right) {
        long sum = (long) left + right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, sum));
    }

    @Override
    public @Nullable Integer railways$getMass() {
        if (railways$mass == null || railways$mass == 0) {
            CarriageContraptionEntity entity = anyAvailableEntity();
            if (entity != null && entity.getContraption() != null)
                railways$mass = entity.getContraption().getBlocks().size();
        }
        return railways$mass;
    }

    @Override
    public void railways$setMass(int mass) {
        railways$mass = mass;
    }

    @Override
    public @Nullable Integer trainphys$getEngineCount() {
        trainphys$scanEngineMetadataIfNeeded();
        return trainphys$engineCount;
    }

    @Override
    public void trainphys$setEngineCount(int engineCount) {
        trainphys$engineCount = engineCount;
    }

    @Override
    public @Nullable Integer trainphys$getElectricEngineCount() {
        trainphys$scanEngineMetadataIfNeeded();
        return trainphys$electricEngineCount;
    }

    @Override
    public void trainphys$setElectricEngineCount(int engineCount) {
        trainphys$electricEngineCount = engineCount;
    }

    @Override
    public @Nullable Integer trainphys$getUnverifiedElectricEngineCount() {
        trainphys$scanEngineMetadataIfNeeded();
        return trainphys$unverifiedElectricEngineCount;
    }

    @Override
    public void trainphys$setUnverifiedElectricEngineCount(int engineCount) {
        trainphys$unverifiedElectricEngineCount = engineCount;
    }

    @Override
    public @Nullable Map<String, Integer> trainphys$getCombustionEngineCounts() {
        trainphys$scanEngineMetadataIfNeeded();
        return trainphys$combustionEngineCounts;
    }

    @Override
    public void trainphys$setCombustionEngineCounts(Map<String, Integer> engineCounts) {
        trainphys$combustionEngineCounts = Map.copyOf(engineCounts);
    }

    @Override
    public @Nullable Map<BlockPos, String> trainphys$getFuelStorageNames() {
        trainphys$scanEngineMetadataIfNeeded();
        return trainphys$fuelStorageNames;
    }

    @Override
    public void trainphys$setFuelStorageNames(Map<BlockPos, String> storageNames) {
        trainphys$fuelStorageNames = Map.copyOf(storageNames);
    }

    @Override
    public void trainphys$markEngineCountsForRefresh() {
        trainphys$engineCountsNeedRefresh = true;
    }

    @Unique
    private void trainphys$scanEngineMetadataIfNeeded() {
        if (trainphys$engineCount != null
                && trainphys$electricEngineCount != null
                && trainphys$unverifiedElectricEngineCount != null
                && trainphys$combustionEngineCounts != null
                && trainphys$fuelStorageNames != null
                && !trainphys$engineCountsNeedRefresh)
            return;

        CarriageContraptionEntity entity = anyAvailableEntity();
        if (entity == null || entity.getContraption() == null)
            return;

        int[] counts = new int[3];
        Map<String, Integer> combustionCounts = new HashMap<>();
        Map<BlockPos, String> storageNames = new HashMap<>();
        for (var blockInfo : entity.getContraption().getBlocks().values()) {
            trainphys$inspectBlock(
                    blockInfo.state(),
                    blockInfo.pos(),
                    blockInfo.nbt(),
                    counts,
                    combustionCounts,
                    storageNames,
                    entity.registryAccess()
            );
        }
        trainphys$engineCount = counts[0];
        trainphys$electricEngineCount = counts[1];
        trainphys$unverifiedElectricEngineCount = counts[2];
        trainphys$combustionEngineCounts = Map.copyOf(combustionCounts);
        trainphys$fuelStorageNames = Map.copyOf(storageNames);
        trainphys$engineCountsNeedRefresh = false;
    }
}
