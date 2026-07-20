package dev.szedann.create_train_physics.network;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import dev.szedann.create_train_physics.CreateTrainPhysics;
import dev.szedann.create_train_physics.accessors.IPhysicsTrain;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Periodic authoritative power snapshot used by client train HUD calculations. */
public record TrainPowerSyncPayload(Map<UUID, Integer> powerByTrain)
        implements CustomPacketPayload {
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final int MAX_TRAINS_PER_PACKET = 4_096;

    public static final Type<TrainPowerSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateTrainPhysics.MODID,
                    "train_power_sync"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TrainPowerSyncPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.map(
                            HashMap::new,
                            UUIDUtil.STREAM_CODEC,
                            ByteBufCodecs.VAR_INT,
                            MAX_TRAINS_PER_PACKET
                    ),
                    TrainPowerSyncPayload::powerByTrain,
                    TrainPowerSyncPayload::new
            );

    public TrainPowerSyncPayload {
        Objects.requireNonNull(powerByTrain, "powerByTrain");
        if (powerByTrain.size() > MAX_TRAINS_PER_PACKET)
            throw new IllegalArgumentException("too many trains in one power snapshot");

        LinkedHashMap<UUID, Integer> validated = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : powerByTrain.entrySet()) {
            UUID trainId = Objects.requireNonNull(entry.getKey(), "train id");
            Integer power = Objects.requireNonNull(entry.getValue(), "train power");
            validated.put(trainId, Math.max(0, power));
        }
        powerByTrain = Map.copyOf(validated);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the client main thread by default through PayloadRegistrar. */
    public static void handle(
            TrainPowerSyncPayload payload,
            IPayloadContext context
    ) {
        Map<UUID, Train> clientTrains = Create.RAILWAYS
                .sided(context.player().level())
                .trains;
        for (Map.Entry<UUID, Integer> entry : payload.powerByTrain().entrySet()) {
            Train train = clientTrains.get(entry.getKey());
            if (train != null)
                ((IPhysicsTrain) train).trainphys$setSyncedPower(entry.getValue());
        }
    }

    /** Broadcast after all train ticks so HUD values match authoritative state. */
    public static void broadcast(MinecraftServer server) {
        if (server == null
                || server.getTickCount() % SYNC_INTERVAL_TICKS != 0
                || server.getPlayerList().getPlayerCount() == 0)
            return;

        LinkedHashMap<UUID, Integer> powers = new LinkedHashMap<>();
        for (Train train : Create.RAILWAYS.trains.values()) {
            powers.put(
                    train.id,
                    ((IPhysicsTrain) train).trainphys$getPowerForSync()
            );
            if (powers.size() == MAX_TRAINS_PER_PACKET) {
                PacketDistributor.sendToAllPlayers(new TrainPowerSyncPayload(powers));
                powers.clear();
            }
        }
        if (!powers.isEmpty())
            PacketDistributor.sendToAllPlayers(new TrainPowerSyncPayload(powers));
    }
}
