package dev.szedann.create_train_physics.datagen;

import com.simibubi.create.AllBlocks;
import dev.szedann.create_train_physics.CreateTrainPhysics;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;


public class TPBlockTagProvider extends BlockTagsProvider {
    // Get parameters from GatherDataEvent.
    public TPBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, CreateTrainPhysics.MODID, existingFileHelper);
    }

    public static final TagKey<Block> MOTOR_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(CreateTrainPhysics.MODID, "train_motor")
    );
    public static final TagKey<Block> UNVERIFIED_ELECTRIC_MOTOR_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(
                    CreateTrainPhysics.MODID,
                    "unverified_electric_train_motor"
            )
    );
    public static final TagKey<Block> STEAM_ENGINE_MOUNT_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(
                    CreateTrainPhysics.MODID,
                    "steam_engine_mount"
            )
    );

    // Add your tag entries here.
    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        tag(UNVERIFIED_ELECTRIC_MOTOR_TAG)
                .addOptional(ResourceLocation.fromNamespaceAndPath("createaddition","electric_motor"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("powergrid","electric_motor"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("powergrid","constant_speed_motor"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("tfmg","electric_motor"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("tfmg","heavy_electric_motor"));

        tag(STEAM_ENGINE_MOUNT_TAG)
                .addOptional(ResourceLocation.fromNamespaceAndPath("railways", "fuel_tank"));

        tag(MOTOR_TAG)
                .add(AllBlocks.STEAM_ENGINE.get())
                .addOptionalTag(ResourceLocation.fromNamespaceAndPath("electroenergetics","train_electric_motor"))
                .addTag(UNVERIFIED_ELECTRIC_MOTOR_TAG)
                .addOptional(ResourceLocation.fromNamespaceAndPath("createdieselgenerators","diesel_engine"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("createdieselgenerators","large_diesel_engine"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("createdieselgenerators","huge_diesel_engine"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("tfmg","turbine_engine"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("tfmg","regular_engine"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("tfmg","radial_engine"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("tfmg","large_engine"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("tfmg","simple_large_engine"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("petrochem","medium_engine"))
                .addOptional(ResourceLocation.fromNamespaceAndPath("petrochem","small_engine"));

    }
}
