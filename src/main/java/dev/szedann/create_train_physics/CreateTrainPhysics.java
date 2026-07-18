package dev.szedann.create_train_physics;

import com.mojang.logging.LogUtils;
import dev.szedann.create_train_physics.physics.EngineFuelRestrictions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CreateTrainPhysics.MODID)
public class CreateTrainPhysics {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "create_train_physics";
    private static final String CEE_MODID = "electroenergetics";
    private static final ResourceLocation CEE_SOUND_OVERRIDES = ResourceLocation.fromNamespaceAndPath(
            MODID,
            "resourcepacks/cee_sound_overrides"
    );
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "create_train_physics" namespace
//    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "create_train_physics" namespace
//    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);


    // Creates a new Block with the id "create_train_physics:example_block", combining the namespace and path
//    public static final DeferredBlock<Block> TRAIN_ENGINE_BLOCK = BLOCKS.registerSimpleBlock("train_engine_block", BlockBehaviour.Properties.of().mapColor(MapColor.METAL));
    // Creates a new BlockItem with the id "create_train_physics:example_block", combining the namespace and path
//    public static final DeferredItem<BlockItem> TRAIN_ENGINE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("train_engine_block", TRAIN_ENGINE_BLOCK);

    public static final TagKey<Block> MOTOR_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(CreateTrainPhysics.MODID, "train_motor")
    );

    /**
     * Public tag supplied by Create: Electro Energetics for every colour of
     * train-capable electric motor. Referencing the key is safe when C:EE is
     * absent; the tag will simply be empty.
     */
    public static final TagKey<Block> CEE_MOTOR_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(CEE_MODID, "train_electric_motor")
    );

    /**
     * Electric train motors whose assembled-train power state cannot currently
     * be queried. They retain configured power but never consume combustion
     * fuel. C:EE motors are tracked separately because their power is verified.
     */
    public static final TagKey<Block> UNVERIFIED_ELECTRIC_MOTOR_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MODID, "unverified_electric_train_motor")
    );

    /** Blocks that may carry a Create steam engine as part of a train build. */
    public static final TagKey<Block> STEAM_ENGINE_MOUNT_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MODID, "steam_engine_mount")
    );

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CreateTrainPhysics(IEventBus modEventBus, ModContainer modContainer) {

//        BLOCKS.register(modEventBus);
//        ITEMS.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(CreateTrainPhysics::addPackFinders);
        modEventBus.addListener(CreateTrainPhysics::registerDataMaps);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(EngineFuelRestrictions.TYPE);
    }

    private static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !ModList.get().isLoaded(CEE_MODID)) {
            return;
        }

        event.addPackFinders(
                CEE_SOUND_OVERRIDES,
                PackType.CLIENT_RESOURCES,
                Component.literal("Create Train Physics: quieter C:EE train sounds"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP
        );
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    // Add the example block item to the building blocks tab
//    private void addCreative(BuildCreativeModeTabContentsEvent event) {
//        if (event.getTabKey() == AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey()) event.accept(TRAIN_ENGINE_BLOCK_ITEM);
//    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
//        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
//    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//    public static class ClientModEvents {
//        @SubscribeEvent
//        public static void onClientSetup(FMLClientSetupEvent event) {
//
//        }
//    }
}