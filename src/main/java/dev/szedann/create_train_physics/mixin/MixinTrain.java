package dev.szedann.create_train_physics.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.szedann.create_train_physics.Config;
import dev.szedann.create_train_physics.accessors.IPhysicsCarriage;
import dev.szedann.create_train_physics.accessors.IPhysicsTrain;
import dev.szedann.create_train_physics.compat.ElectroEnergeticsCompat;
import dev.szedann.create_train_physics.compat.SteamNRailsCompat;
import dev.szedann.create_train_physics.physics.TractionPolicy;
import dev.szedann.create_train_physics.physics.TrainFuelLedger;
import dev.szedann.create_train_physics.physics.TrainPhysicsMath;
import dev.szedann.create_train_physics.physics.TrainPowerPolicy;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = Train.class, remap = false, priority = 1100)
public abstract class MixinTrain implements IPhysicsTrain {
    @Shadow
    public boolean derailed;

    @Shadow
    public List<Carriage> carriages;

    @Shadow
    public TrackGraph graph;

    @Shadow
    public double speed;

    @Shadow
    public double targetSpeed;

    @Shadow
    public abstract void leaveStation();

    @Shadow
    public boolean manualTick;

    @Shadow
    public Navigation navigation;

    @Shadow
    public int fuelTicks;

    @Shadow
    public abstract float maxSpeed();

    @Shadow
    public abstract float acceleration();

    @Shadow
    public UUID currentStation;

    @Shadow
    public UUID id;

    @Shadow
    public abstract void crash();

    @Unique private double railways$rollingResistanceCoefficient(){ return 0.001; }
    @Unique private double railways$frictionCoefficient(){
        return 0.4;
//        return carriages.getFirst().leadingBogey().leading().edge.getTrackMaterial().trackType
//                == CRTrackMaterials.CRTrackType.MONORAIL
//                ? 0.8
//                : 0.4;
    }
    @Unique private double railways$powerUsage = 0;
    //    @Unique private boolean railways$isRaining = false;
    @Unique private double railways$energyUsed = 0;
    @Unique private boolean trainphys$combustionFuelActive = false;
    @Unique private int trainphys$isolatedCombustionFuelTicks = 0;
    @Unique private int trainphys$pendingElectricLease = -1;

    @WrapMethod(method = "collideWithOtherTrains")
    public void collideWithOtherTrains(Level level, Carriage carriage, Operation<Void> original) {
        if (railways$isHandcar()) {
            original.call(level, carriage);
            return;
        }

        if (derailed)
            return;

        TravellingPoint trailingPoint = carriage.getTrailingPoint();
        TravellingPoint leadingPoint = carriage.getLeadingPoint();


        if (leadingPoint.node1 == null || trailingPoint.node1 == null
                || leadingPoint.edge == null || trailingPoint.edge == null)
            return;
        ResourceKey<Level> dimension = leadingPoint.node1.getLocation().dimension;
        if (!dimension.equals(trailingPoint.node1.getLocation().dimension))
            return;

        Vec3 start = (speed < 0 ? trailingPoint : leadingPoint).getPosition(graph);
        Vec3 end = (speed < 0 ? leadingPoint : trailingPoint).getPosition(graph);

        Pair<Carriage, Vec3> collision = railways$findCollidingCarriage(level, start, end, dimension);
        if (collision == null)
            return;

        Train train = collision.getFirst().train;

        // Steam 'n' Rails injects its handcar item/drop behavior into Create's
        // original collision method. Delegate when the other train is a
        // handcar so that integration is not bypassed by this wrapper.
        if (SteamNRailsCompat.isHandcar(train)) {
            original.call(level, carriage);
            return;
        }

        Carriage otherCarriage = collision.getFirst();

        double yawDiff = Math.abs(railways$getCarriageYaw(carriage)-railways$getCarriageYaw(otherCarriage));

        int directionMultiplier = (yawDiff > Math.PI/2 && yawDiff < Math.PI*1.5) ? -1 : 1;

        double relativeSpeed = Math.abs(directionMultiplier * speed - train.speed) * 20;

        if (relativeSpeed > 6 || (yawDiff%Math.PI>Math.PI/4 && yawDiff%Math.PI<Math.PI*0.75)) {
            Vec3 v = collision.getSecond();
            level.explode(null, v.x, v.y, v.z, (float) Math.min(3 * relativeSpeed, 5), Level.ExplosionInteraction.NONE);
            crash();
            train.crash();
            return;
        }

        double m1 = railways$getMass();
        double m2 = Math.max(1, train.carriages.stream()
                .mapToLong(this::railways$getCarriageMass)
                .sum());

        double u1 = directionMultiplier * speed * 20;
        double u2 = train.speed * 20;

        // Coefficient of restitution
        double e = 0.5;

        double v1 = (m1*u1 + m2*u2 + m2*e*(u2-u1)) / (m1+m2);
        double v2 = (m1*u1 + m2*u2 + m1*e*(u1-u2)) / (m1+m2);

        speed = directionMultiplier * v1 / 20;
        train.speed = v2 / 20;


    }

    @Inject(method = "tickPassiveSlowdown", at=@At("HEAD"), cancellable = true)
    public void tickPassiveSlowdown(CallbackInfo ci) {
        if (railways$isHandcar())
            return;

        boolean wasManuallyControlled = manualTick;
        ci.cancel();
        // Vanilla clears this flag at the end of this method. Keeping that
        // side effect is essential: Navigation intentionally ignores signals
        // while manualTick is true.
        manualTick = false;

        if (!Double.isFinite(speed))
            speed = 0;

        if(currentStation != null)
            return;

        double speedBeforePassiveForces = speed;
        double gravityAcceleration = railways$getGravityAcceleration();
        double aerodynamicAcceleration = -railways$forceToAcceleration(railways$getAerodynamicDrag());
        double rollingFrictionAcceleration = -railways$forceToAcceleration(railways$getRollingFriction())
                * Math.signum(speed);
//        Railways.LOGGER.info("gravity {}, aerodynamic {}, rollingFriction {}",
//                String.format("%.2f", gravityAcceleration*20),
//                String.format("%.2f", aerodynamicAcceleration*20),
//                String.format("%.2f", rollingFrictionAcceleration*20));

        double resistedSpeed = speed + aerodynamicAcceleration + rollingFrictionAcceleration;
        if (speed != 0 && Math.signum(resistedSpeed) != Math.signum(speed))
            resistedSpeed = 0;
        speed = resistedSpeed + gravityAcceleration;

        boolean unattended = navigation == null || navigation.destination == null;
        boolean navigationHolding = !unattended && Mth.equal(targetSpeed, 0);
        if (Config.automaticHandbrake && !wasManuallyControlled) {
            // Static parking friction holds a stopped train even on slopes.
            boolean parkingRequested = unattended && Mth.equal(targetSpeed, 0);
            if (parkingRequested
                    || ((unattended || navigationHolding) && Mth.equal(speedBeforePassiveForces, 0))) {
                speed = 0;
            } else if (unattended) {
                // Once moving, use the configured service-brake rate. Active
                // navigation already applied this brake in approachTargetSpeed.
                double brakingAcceleration = acceleration();
                if (Math.abs(speed) <= brakingAcceleration)
                    speed = 0;
                else
                    speed -= Math.copySign(brakingAcceleration, speed);
            }
        }
//        carriages.forEach(carriage -> carriage.bogeys.stream().filter(Objects::nonNull).forEach(carriageBogey -> railways$applyWheelSlip(carriageBogey,.1)));
    }

    @Unique
    private int railways$getCarriageMass(Carriage carriage){
        Integer carriageMass = ((IPhysicsCarriage) carriage).railways$getMass();
        if(carriageMass == null) carriageMass = 1;
        AtomicInteger cargoMass = new AtomicInteger();
//        CombinedInvWrapper storageItems =  carriage.storage.getAllItems();
//        if(storageItems != null)
//            storageItems.(storage-> cargoMass.addAndGet((int) (storage.getAmount() * 10)));
        long mass = (long) Math.max(1, carriageMass) * 500L + cargoMass.get();
        return (int) Math.min(Integer.MAX_VALUE, mass);
    }

    @Unique
    private int railways$getMass(){
        long mass = 0;
        for(Carriage carriage : carriages) mass += railways$getCarriageMass(carriage);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, mass));
    }

    @Unique
    private int railways$getCarriageEngineCount(Carriage carriage){
        Integer engineCount = ((IPhysicsCarriage)carriage).trainphys$getEngineCount();
        if(engineCount == null) engineCount = 0;
        return Math.max(0, engineCount);
    }

    @Unique
    private int railways$getCarriageElectricEngineCount(Carriage carriage){
        Integer engineCount = ((IPhysicsCarriage)carriage).trainphys$getElectricEngineCount();
        if(engineCount == null) engineCount = 0;
        return Math.max(0, engineCount);
    }

    @Unique
    private int railways$getEngineCount(){
        long count = carriages.stream().mapToLong(this::railways$getCarriageEngineCount).sum();
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    @Unique
    private int railways$getElectricEngineCount(){
        long count = carriages.stream().mapToLong(this::railways$getCarriageElectricEngineCount).sum();
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    @Unique
    private int railways$getPower(){
        int allEngines = railways$getEngineCount();
        int electricEngines = Math.min(railways$getElectricEngineCount(), allEngines);
        boolean electricPowered = electricEngines > 0
                && ElectroEnergeticsCompat.isPowered(railways$self());
        return TrainPowerPolicy.availablePowerWatts(
                allEngines,
                electricEngines,
                Config.requireFuel,
                railways$hasCombustionFuel(),
                electricPowered,
                Config.enginePower,
                Config.fueledEnginePower
        );
    }

    @Unique
    private int railways$getCombustionPower() {
        int combustionEngines = Math.max(0,
                railways$getEngineCount() - railways$getElectricEngineCount());
        return TrainPowerPolicy.availablePowerWatts(
                combustionEngines,
                0,
                Config.requireFuel,
                railways$hasCombustionFuel(),
                false,
                Config.enginePower,
                Config.fueledEnginePower
        );
    }

    @Override
    public void trainphys$setCombustionFuelActive(boolean active) {
        trainphys$combustionFuelActive = active;
    }

    @Override
    public void trainphys$setFuelEnergyDebt(double joules) {
        railways$energyUsed = Double.isFinite(joules) ? Math.max(0, joules) : 0;
    }

    @Override
    public void trainphys$setIsolatedCombustionFuelTicks(int ticks) {
        trainphys$isolatedCombustionFuelTicks = Math.max(0, ticks);
    }

    @Unique
    private boolean railways$hasCombustionFuel() {
        if (fuelTicks > TrainPowerPolicy.CEE_FUEL_TICK_LEASE)
            trainphys$combustionFuelActive = true;
        if (fuelTicks <= 0 && trainphys$isolatedCombustionFuelTicks <= 0)
            trainphys$combustionFuelActive = false;
        return trainphys$combustionFuelActive
                && (fuelTicks > 0 || trainphys$isolatedCombustionFuelTicks > 0);
    }

    @Unique
    private Train railways$self() {
        return (Train) (Object) this;
    }

    @Unique
    private boolean railways$isHandcar() {
        return SteamNRailsCompat.isHandcar(railways$self());
    }

    /**
     * @see <a href="https://en.wikipedia.org/wiki/Adhesion_railway#Effect_of_adhesion_limits">Effect of adhesion limits</a>
     */
    @Unique
    private double railways$getMaxTractiveEffort(){
        double friction = railways$frictionCoefficient();
        return carriages.stream().mapToDouble(carriage -> friction*railways$getCarriageMass(carriage)*9.81).sum();
    }


    @Unique
    private double railways$getMaxSpeed(){
        int availablePower = railways$getPower();
        if (availablePower <= 0)
            return 0;

        double configuredTopSpeed = AllConfigs.server().trains.poweredTrainTopSpeed.getF();
        double physicalTopSpeed = TrainPhysicsMath.powerLimitedSpeed(
                availablePower,
                railways$getMass(),
                railways$rollingResistanceCoefficient(),
                railways$getDragConstant()
        );
        return Math.min(physicalTopSpeed, configuredTopSpeed) / 20;
    }

    @Inject(method = "maxSpeed", at = @At("RETURN"), cancellable = true)
    public void maxSpeed(CallbackInfoReturnable<Float> cir) {
        if (!railways$isHandcar())
            cir.setReturnValue((float) railways$getMaxSpeed());
    }

    @Inject(method = "maxTurnSpeed", at = @At("RETURN"), cancellable = true)
    public void maxTurnSpeed(CallbackInfoReturnable<Float> cir) {
        if (railways$isHandcar())
            return;

        double limitingSpeed = Double.POSITIVE_INFINITY;
        Set<BezierConnection> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Carriage carriage : carriages) {
            limitingSpeed = Math.min(limitingSpeed,
                    railways$getTurnSpeed(carriage.leadingBogey().leading(), visited));
            limitingSpeed = Math.min(limitingSpeed,
                    railways$getTurnSpeed(carriage.leadingBogey().trailing(), visited));
            if (carriage.isOnTwoBogeys()) {
                limitingSpeed = Math.min(limitingSpeed,
                        railways$getTurnSpeed(carriage.trailingBogey().leading(), visited));
                limitingSpeed = Math.min(limitingSpeed,
                        railways$getTurnSpeed(carriage.trailingBogey().trailing(), visited));
            }
        }

        float trainMaxSpeed = maxSpeed();
        float configuredTurnSpeed = AllConfigs.server().trains.poweredTrainTurningTopSpeed.getF() / 20;
        float turnCap = Math.min(trainMaxSpeed, configuredTurnSpeed);
        if (!Double.isFinite(limitingSpeed)) {
            cir.setReturnValue(turnCap);
            return;
        }
        cir.setReturnValue((float) Math.min(turnCap, limitingSpeed));
    }

    @Unique
    private double railways$getTurnSpeed(TravellingPoint point, Set<BezierConnection> visited) {
        if (point == null || point.edge == null)
            return Double.POSITIVE_INFINITY;
        BezierConnection turn = point.edge.getTurn();
        if (turn == null || !visited.add(turn))
            return Double.POSITIVE_INFINITY;

        double radius = turn.getRadius();
        if (!Double.isFinite(radius) || radius <= 0)
            radius = railways$estimateMinimumRadius(turn);
        if (!Double.isFinite(radius) || radius <= 0)
            return Double.POSITIVE_INFINITY;

        double friction = railways$frictionCoefficient();
        double speed = Math.sqrt(9.81 * radius * (friction / (1 - friction))) / 20;
        return Double.isFinite(speed) && speed >= 0 ? speed : Double.POSITIVE_INFINITY;
    }

    /** Estimate the tightest horizontal radius of a non-circular (S) curve. */
    @Unique
    private double railways$estimateMinimumRadius(BezierConnection turn) {
        final int samples = 64;
        double minimumRadius = Double.POSITIVE_INFINITY;
        Vec3 previous = turn.getPosition(0);
        Vec3 current = turn.getPosition(1d / samples);

        for (int i = 2; i <= samples; i++) {
            Vec3 next = turn.getPosition(i / (double) samples);
            double ab = Math.hypot(current.x - previous.x, current.z - previous.z);
            double bc = Math.hypot(next.x - current.x, next.z - current.z);
            double ac = Math.hypot(next.x - previous.x, next.z - previous.z);
            double cross = Math.abs(
                    (current.x - previous.x) * (next.z - previous.z)
                            - (current.z - previous.z) * (next.x - previous.x)
            );

            if (ab > 1.0e-6 && bc > 1.0e-6 && ac > 1.0e-6 && cross > 1.0e-8) {
                double radius = ab * bc * ac / (2 * cross);
                if (Double.isFinite(radius) && radius > 0)
                    minimumRadius = Math.min(minimumRadius, radius);
            }
            previous = current;
            current = next;
        }
        return minimumRadius;
    }

    @Inject(method = "acceleration", at = @At("HEAD"), cancellable = true)
    public void acceleration(CallbackInfoReturnable<Float> cir) {
        if (railways$isHandcar())
            return;
        float configured = railways$getPower() > 0
                ? AllConfigs.server().trains.poweredTrainAcceleration.getF()
                : AllConfigs.server().trains.trainAcceleration.getF();
        cir.setReturnValue(configured / (20 * 20));
    }

    @Inject(method = "approachTargetSpeed", at = @At("HEAD"), cancellable = true)
    public void approachTargetSpeed(float accelerationMod, CallbackInfo ci) {
        if (railways$isHandcar())
            return;

        ci.cancel();
        railways$powerUsage = 0;

        if (!Double.isFinite(speed))
            speed = 0;
        double requestedTarget = Double.isFinite(targetSpeed) ? targetSpeed : 0;
        int availablePower = railways$getPower();
        int combustionPower = railways$getCombustionPower();
        if (!railways$hasCombustionFuel())
            combustionPower = 0;
        double actualTarget = TractionPolicy.targetForTick(
                speed,
                requestedTarget,
                availablePower > 0
        );
        if (Mth.equal(actualTarget, speed))
            return;
        if (manualTick)
            leaveStation();

        boolean traction = TractionPolicy.requiresTraction(speed, actualTarget);
        double speedBeforeAcceleration = speed;
        double velocity = Math.abs(speed*20); // velocity in m/s
        double forceForTarget = Math.abs(actualTarget-speed)*railways$getMass()*(20*20);
        double configuredEffort = acceleration() * railways$getMass() * (20 * 20)
                * Math.max(0, accelerationMod);
        double effortForTick = Math.min(railways$getMaxTractiveEffort(), configuredEffort);
        double force;
        if(traction) {
            double powerLimitedForce = TrainPhysicsMath.powerLimitedForce(
                    availablePower,
                    railways$getMass(),
                    velocity,
                    1d / 20d
            );
            force = Math.min(Math.min(powerLimitedForce, effortForTick), forceForTarget);
        } else {
            force = Math.min(effortForTick, forceForTarget);
        }

        if (!Double.isFinite(force) || force <= 0)
            return;
        double acceleration = railways$forceToAcceleration(force);
        if (speed < actualTarget)
            speed = Math.min(speed + acceleration, actualTarget);
        else if (speed > actualTarget)
            speed = Math.max(speed - acceleration, actualTarget);

        if (traction) {
            double averageVelocity = (Math.abs(speedBeforeAcceleration) + Math.abs(speed)) * 10;
            double totalPowerUsage = Math.min(availablePower, force * averageVelocity);
            railways$powerUsage = availablePower > 0
                    ? totalPowerUsage * combustionPower / availablePower
                    : 0;
        }
    }

    @Unique private double railways$getGravityAcceleration(){
        if(derailed || graph == null || carriages.isEmpty()) return  0;
        TravellingPoint leadingPoint = carriages.getFirst().getLeadingPoint();
        TravellingPoint trailingPoint = carriages.getLast().getTrailingPoint();
        if (leadingPoint == null || trailingPoint == null
                || leadingPoint.node1 == null || trailingPoint.node1 == null
                || leadingPoint.edge == null || trailingPoint.edge == null)
            return 0;
        Vec3 leading = leadingPoint.getPosition(graph);
        Vec3 trailing = trailingPoint.getPosition(graph);
        double horizontalDistance = Math.sqrt(Math.pow(leading.x - trailing.x, 2) + Math.pow(leading.z - trailing.z, 2));
        double verticalDistance = leading.y - trailing.y;
        double incline = Math.atan2(verticalDistance, horizontalDistance); // (-0.5pi,0): decline, (0, 0.5pi): incline 0: no incline
        double gravityPerTick = -9.81 / (20 * 20); // 1s = 20t
        double gravityMultiplier = 1;
        return gravityMultiplier * gravityPerTick * Math.sin(incline);
    }

    @Unique private double railways$getDragConstant(){
        double airDensity = 1.204;
        double dragCoefficient = 0.35;
        double area = 9;
        return airDensity * dragCoefficient * area;
    }

    @Unique private double railways$getAerodynamicDrag(){
        double velocity = speed * 20;
        return railways$getDragConstant() * 0.5 * Math.pow(velocity, 2) * (speed > 0 ? 1 : -1);
    }

    @Unique private double railways$getRollingFriction(){
        return (railways$rollingResistanceCoefficient() * (railways$getMass()*9.81));
    }

//    @Unique private void railways$applyWheelSlip(CarriageBogey bogey, double distance){
//        CRPackets.PACKETS.sendTo(PlayerSelection.all(), new WheelslipPacket(bogey, distance));
//    }

    @Unique double railways$forceToAcceleration(double force){
        return force / railways$getMass() / (20*20);
    }

    @Inject(method = "tick", at=@At("TAIL"))
    public void tick(CallbackInfo ci) {
        double powerUsedThisTick = railways$powerUsage;
        railways$powerUsage = 0;
        if (Double.isFinite(powerUsedThisTick) && powerUsedThisTick > 0)
            railways$energyUsed += powerUsedThisTick / 20;
//        double vmax = maxTurnSpeed();
//        carriages.forEach(c->c.forEachPresentEntity(cce->cce.getPassengers().forEach(p->{
//            if(!(p instanceof Player player)) return;
//            player.displayClientMessage(Component.literal(String.format("%.0fW P - %.0fb/s v - %.0fb/s vmax - %dkg mass - %d fticks",
//                    railways$powerUsage, speed*20, vmax*20, railways$getMass(), fuelTicks)), true);
//        })));
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void trainphys$writeCombustionFuelState(
            DimensionPalette dimensions,
            HolderLookup.Provider registries,
            CallbackInfoReturnable<CompoundTag> cir
    ) {
        if (trainphys$combustionFuelActive
                && (fuelTicks > 0 || trainphys$isolatedCombustionFuelTicks > 0))
            cir.getReturnValue().putBoolean("TrainPhysicsCombustionFuel", true);
        if (railways$energyUsed > 0 && Double.isFinite(railways$energyUsed))
            cir.getReturnValue().putDouble("TrainPhysicsFuelEnergyDebt", railways$energyUsed);
        if (trainphys$isolatedCombustionFuelTicks > 0)
            cir.getReturnValue().putInt(
                    "TrainPhysicsIsolatedCombustionFuel",
                    trainphys$isolatedCombustionFuelTicks
            );
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void trainphys$readCombustionFuelState(
            CompoundTag tag,
            HolderLookup.Provider registries,
            Map<UUID, TrackGraph> trackNetworks,
            DimensionPalette dimensions,
            CallbackInfoReturnable<Train> cir
    ) {
        IPhysicsTrain train = (IPhysicsTrain) cir.getReturnValue();
        train.trainphys$setCombustionFuelActive(tag.getBoolean("TrainPhysicsCombustionFuel"));
        train.trainphys$setFuelEnergyDebt(tag.getDouble("TrainPhysicsFuelEnergyDebt"));
        train.trainphys$setIsolatedCombustionFuelTicks(
                tag.getInt("TrainPhysicsIsolatedCombustionFuel")
        );
    }

    @Unique
    private void trainphys$consumeCombustionFuel(int electricEngines) {
        boolean isolated = trainphys$isolatedCombustionFuelTicks > 0;
        int availableTicks = isolated ? trainphys$isolatedCombustionFuelTicks : fuelTicks;
        TrainFuelLedger.Consumption consumption = TrainFuelLedger.consume(
                availableTicks,
                railways$energyUsed,
                15000 // rough estimate based on coal
        );
        railways$energyUsed = consumption.energyJoules();

        if (isolated) {
            trainphys$isolatedCombustionFuelTicks = consumption.fuelTicks();
            if (trainphys$isolatedCombustionFuelTicks <= 0)
                trainphys$combustionFuelActive = false;
            return;
        }

        fuelTicks = consumption.fuelTicks();
        // Keep the final real-fuel ticks outside C:EE's shared field so its
        // 1 -> 10 electrical lease refresh cannot turn them into free fuel.
        if (electricEngines > 0
                && fuelTicks > 0
                && fuelTicks <= TrainPowerPolicy.CEE_FUEL_TICK_LEASE) {
            trainphys$isolatedCombustionFuelTicks = fuelTicks;
            fuelTicks = 0;
        } else if (fuelTicks <= 0) {
            trainphys$combustionFuelActive = false;
        }
    }

    @Inject(method = "burnFuel", at = @At("HEAD"), cancellable = true)
    public void burnFuel(CallbackInfo ci) {
        trainphys$pendingElectricLease = -1;
        if (railways$isHandcar()) {
            ci.cancel();
            return;
        }

        int allEngines = railways$getEngineCount();
        int electricEngines = railways$getElectricEngineCount();
        int combustionEngines = Math.max(0, allEngines - electricEngines);

        // An electric-only train must never consume an inventory fuel item.
        // Still let Create decrement C:EE's short compatibility lease.
        if (combustionEngines == 0) {
            if (electricEngines == 0
                    || fuelTicks <= 0
                    || fuelTicks > TrainPowerPolicy.CEE_FUEL_TICK_LEASE)
                ci.cancel();
            return;
        }

        if (fuelTicks > TrainPowerPolicy.CEE_FUEL_TICK_LEASE)
            trainphys$combustionFuelActive = true;

        if (trainphys$combustionFuelActive
                && (fuelTicks > 0 || trainphys$isolatedCombustionFuelTicks > 0)) {
            trainphys$consumeCombustionFuel(electricEngines);
            ci.cancel();
            return;
        }

        // C:EE stores electrical availability in the same field Create uses
        // for real fuel. Temporarily hide that lease so vanilla (and S&R's
        // fluid-fuel injection) can scan a mixed train's fuel inventories.
        trainphys$pendingElectricLease = electricEngines > 0
                && fuelTicks > 0
                && fuelTicks <= TrainPowerPolicy.CEE_FUEL_TICK_LEASE
                ? fuelTicks
                : 0;
        fuelTicks = 0;
    }

    @Inject(method = "burnFuel", at = @At("RETURN"))
    public void trainphys$finishFuelInventoryScan(CallbackInfo ci) {
        if (trainphys$pendingElectricLease < 0)
            return;

        if (fuelTicks > 0) {
            trainphys$combustionFuelActive = true;
            int electricEngines = railways$getElectricEngineCount();
            trainphys$consumeCombustionFuel(electricEngines);
            if (electricEngines > 0
                    && fuelTicks == 0
                    && (trainphys$isolatedCombustionFuelTicks > 0
                    || !trainphys$combustionFuelActive))
                fuelTicks = Math.max(0, trainphys$pendingElectricLease - 1);
            trainphys$pendingElectricLease = -1;
            return;
        }

        // Match vanilla's one-tick lease decay when no real fuel was found.
        fuelTicks = Math.max(0, trainphys$pendingElectricLease - 1);
        trainphys$pendingElectricLease = -1;
    }

    @Unique
    public double railways$getCarriageYaw(Carriage carriage) {
        Vec3 diff = carriage.getLeadingPoint().getPosition(carriage.train.graph)
                .subtract(carriage.getTrailingPoint().getPosition(carriage.train.graph)).normalize();
        return Math.atan2(diff.x, diff.z);
    }

    @Unique
    public Pair<Carriage, Vec3> railways$findCollidingCarriage(Level level, Vec3 start, Vec3 end, ResourceKey<Level> dimension) {
        Vec3 diff = end.subtract(start);
        double maxDistanceSqr = Math.pow(AllConfigs.server().trains.maxAssemblyLength.get(), 2.0);


        Trains: for (Train train : Create.RAILWAYS.sided(level).trains.values()) {
            if (train.id == this.id)
                continue;
            if (train.graph != null && train.graph != graph)
                continue;

            Vec3 lastPoint = null;

            for (Carriage otherCarriage : train.carriages) {
                for (boolean betweenBits : Iterate.trueAndFalse) {
                    if (betweenBits && lastPoint == null)
                        continue;

                    TravellingPoint otherLeading = otherCarriage.getLeadingPoint();
                    TravellingPoint otherTrailing = otherCarriage.getTrailingPoint();
                    if (otherLeading.edge == null || otherTrailing.edge == null)
                        continue;
                    ResourceKey<Level> otherDimension = otherLeading.node1.getLocation().dimension;
                    if (!otherDimension.equals(otherTrailing.node1.getLocation().dimension))
                        continue;
                    if (!otherDimension.equals(dimension))
                        continue;

                    Vec3 start2 = otherLeading.getPosition(train.graph);
                    Vec3 end2 = otherTrailing.getPosition(train.graph);

                    if (Math.min(start2.distanceToSqr(start), end2.distanceToSqr(start)) > maxDistanceSqr)
                        continue Trains;

                    if (betweenBits) {
                        end2 = start2;
                        start2 = lastPoint;
                    }

                    lastPoint = end2;

                    if ((end.y < end2.y - 3 || end2.y < end.y - 3)
                            && (start.y < start2.y - 3 || start2.y < start.y - 3))
                        continue;

                    Vec3 diff2 = end2.subtract(start2);
                    Vec3 normedDiff = diff.normalize();
                    Vec3 normedDiff2 = diff2.normalize();
                    double[] intersect = VecHelper.intersect(start, start2, normedDiff, normedDiff2, Direction.Axis.Y);

                    if (intersect == null) {
                        Vec3 intersectSphere = VecHelper.intersectSphere(start2, normedDiff2, start, .125f);
                        if (intersectSphere == null)
                            continue;
                        if (!Mth.equal(normedDiff2.dot(intersectSphere.subtract(start2)
                                .normalize()), 1))
                            continue;
                        intersect = new double[2];
                        intersect[0] = intersectSphere.distanceTo(start) - .125;
                        intersect[1] = intersectSphere.distanceTo(start2) - .125;
                    }

                    if (intersect[0] > diff.length())
                        continue;
                    if (intersect[1] > diff2.length())
                        continue;
                    if (intersect[0] < 0)
                        continue;
                    if (intersect[1] < 0)
                        continue;


                    return Pair.of(otherCarriage, start.add(normedDiff.scale(intersect[0])));
                }
            }
        }
        return null;
    }
}
