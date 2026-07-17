package dev.szedann.create_train_physics.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static dev.szedann.create_train_physics.CreateTrainPhysics.STEAM_ENGINE_MOUNT_TAG;

/** Allows datapack-defined train tanks to carry a steam-engine block. */
@Mixin(value = SteamEngineBlock.class, remap = false)
public abstract class MixinSteamEngineBlock {
    @ModifyReturnValue(method = "canAttach", at = @At("RETURN"))
    private static boolean trainphys$allowTaggedTrainMounts(
            boolean original,
            LevelReader level,
            BlockPos enginePos,
            Direction direction
    ) {
        return original || level.getBlockState(enginePos.relative(direction))
                .is(STEAM_ENGINE_MOUNT_TAG);
    }
}
