package dev.szedann.create_train_physics.compat;

import com.google.common.collect.ImmutableMap;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageWrapper;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import dev.szedann.create_train_physics.accessors.IPhysicsCarriage;
import dev.szedann.create_train_physics.physics.EngineFuelRestrictions;
import dev.szedann.create_train_physics.physics.FuelStorageNamePolicy;
import dev.szedann.create_train_physics.physics.FuelKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Owned solid-fuel scan used when engine profiles or storage names matter. */
public final class TrainItemFuelHandler {
    private TrainItemFuelHandler() {
    }

    public static Optional<ItemFuelAcquisition> acquire(
            Train train,
            EngineFuelRestrictions.Policy policy,
            Set<String> enginesNeedingFuel,
            String requiredStorageName
    ) {
        if (train == null || train.carriages.isEmpty() || enginesNeedingFuel.isEmpty())
            return Optional.empty();

        boolean reverse = train.speed < 0;
        int carriageCount = train.carriages.size();
        for (EngineFuelRestrictions.FuelCandidate candidate
                : policy.candidates(FuelKey.Kind.ITEM, enginesNeedingFuel)) {
            for (int index = 0; index < carriageCount; index++) {
                int carriageIndex = reverse ? carriageCount - 1 - index : index;
                MountedItemStorageWrapper storage = eligibleStorage(
                        train.carriages.get(carriageIndex),
                        requiredStorageName
                );
                if (storage == null)
                    continue;

                for (int slot = 0; slot < storage.getSlots(); slot++) {
                    ItemStack simulated = storage.extractItem(slot, 1, true);
                    if (!policy.matches(candidate, simulated))
                        continue;
                    int burnTime = simulated.getBurnTime(null);
                    if (burnTime <= 0)
                        continue;

                    ItemStack extracted = storage.extractItem(slot, 1, false);
                    if (extracted.isEmpty())
                        continue;
                    int fuelTicks = saturatedMultiply(burnTime, extracted.getCount());
                    ItemStack container = extracted.getCraftingRemainingItem();
                    if (!container.isEmpty())
                        ItemHandlerHelper.insertItemStacked(storage, container, false);
                    return Optional.of(new ItemFuelAcquisition(
                            candidate.engineId(),
                            FuelKey.item(BuiltInRegistries.ITEM.getKey(extracted.getItem()).toString()),
                            fuelTicks
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private static @Nullable MountedItemStorageWrapper eligibleStorage(
            Carriage carriage,
            String requiredStorageName
    ) {
        MountedItemStorageWrapper storage = carriage.storage.getFuelItems();
        if (storage == null)
            return null;
        if (FuelStorageNamePolicy.allowsAll(requiredStorageName))
            return storage;
        if (requiredStorageName == null || requiredStorageName.isEmpty())
            return null;

        Map<BlockPos, String> names = ((IPhysicsCarriage) carriage).trainphys$getFuelStorageNames();
        if (names == null)
            return null;

        ImmutableMap.Builder<BlockPos, MountedItemStorage> eligible = ImmutableMap.builder();
        for (Map.Entry<BlockPos, MountedItemStorage> entry : storage.storages.entrySet()) {
            if (FuelStorageNamePolicy.matches(requiredStorageName, names.get(entry.getKey())))
                eligible.put(entry);
        }
        ImmutableMap<BlockPos, MountedItemStorage> filtered = eligible.build();
        return filtered.isEmpty() ? null : new MountedItemStorageWrapper(filtered);
    }

    private static int saturatedMultiply(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, (long) left * right));
    }

    public record ItemFuelAcquisition(String engineId, FuelKey fuel, int fuelTicks) {
        public ItemFuelAcquisition {
            if (engineId == null || engineId.isBlank())
                throw new IllegalArgumentException("engineId cannot be blank");
            if (fuel == null)
                throw new IllegalArgumentException("fuel cannot be null");
            if (fuelTicks <= 0)
                throw new IllegalArgumentException("fuelTicks must be positive");
        }
    }
}
