package dev.szedann.create_train_physics.physics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.szedann.create_train_physics.CreateTrainPhysics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Data-map boundary and runtime matcher for engine-specific fuel profiles. */
public final class EngineFuelRestrictions {
    private EngineFuelRestrictions() {
    }

    public record Matcher(
            @Nullable ExtraCodecs.TagOrElementLocation location,
            boolean invalid
    ) {
        public static final Matcher ANY = new Matcher(null, false);
        public static final Matcher INVALID = new Matcher(null, true);

        public static final Codec<Matcher> CODEC = Codec.STRING.xmap(
                Matcher::parse,
                Matcher::serialize
        );

        private static Matcher parse(String value) {
            if ("*".equals(value))
                return ANY;
            boolean tag = value.startsWith("#");
            String id = tag ? value.substring(1) : value;
            ResourceLocation location = ResourceLocation.tryParse(id);
            return location == null
                    ? INVALID
                    : new Matcher(
                            new ExtraCodecs.TagOrElementLocation(location, tag),
                            false
                    );
        }

        private String serialize() {
            if (invalid)
                return "";
            if (location == null)
                return "*";
            return (location.tag() ? "#" : "") + location.id();
        }

        public boolean wildcard() {
            return location == null && !invalid;
        }
    }

    public record Entry(
            Optional<List<Matcher>> fluids,
            Optional<List<Matcher>> items,
            int priority
    ) {
        private static final Codec<JsonElement> JSON = Codec.PASSTHROUGH.xmap(
                dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
                element -> new Dynamic<>(JsonOps.INSTANCE, element)
        );

        /**
         * Decode syntactically valid JSON without returning a codec error.
         * NeoForge treats a rejected data-map value as absent, while absence
         * intentionally means an unrestricted engine here. Malformed fields
         * therefore become non-matching lists in-band and fail closed.
         */
        public static final Codec<Entry> CODEC = JSON.xmap(
                Entry::fromJson,
                Entry::toJson
        );

        public Entry {
            fluids = fluids.map(List::copyOf);
            items = items.map(List::copyOf);
        }

        public List<Matcher> matchers(FuelKey.Kind kind) {
            return (kind == FuelKey.Kind.FLUID ? fluids : items).orElse(List.of());
        }

        private static Entry fromJson(JsonElement element) {
            if (element == null || !element.isJsonObject())
                return new Entry(Optional.of(List.of()), Optional.of(List.of()), 0);

            JsonObject object = element.getAsJsonObject();
            int priority = 0;
            JsonElement priorityElement = object.get("priority");
            if (priorityElement != null && priorityElement.isJsonPrimitive()
                    && priorityElement.getAsJsonPrimitive().isNumber()) {
                try {
                    priority = priorityElement.getAsInt();
                } catch (NumberFormatException ignored) {
                    priority = 0;
                }
            }
            return new Entry(
                    readMatchers(object, "fluids"),
                    readMatchers(object, "items"),
                    priority
            );
        }

        private static Optional<List<Matcher>> readMatchers(
                JsonObject object,
                String field
        ) {
            if (!object.has(field))
                return Optional.empty();
            JsonElement value = object.get(field);
            if (value == null || !value.isJsonArray())
                return Optional.of(List.of(Matcher.INVALID));

            List<Matcher> matchers = new ArrayList<>();
            for (JsonElement matcher : value.getAsJsonArray()) {
                if (matcher != null && matcher.isJsonPrimitive()
                        && matcher.getAsJsonPrimitive().isString()) {
                    matchers.add(Matcher.parse(matcher.getAsString()));
                } else {
                    matchers.add(Matcher.INVALID);
                }
            }
            return Optional.of(validateMatchers(matchers));
        }

        private static List<Matcher> validateMatchers(List<Matcher> matchers) {
            for (int i = 0; i < matchers.size() - 1; i++) {
                if (matchers.get(i).wildcard())
                    return List.of(Matcher.INVALID);
            }
            return List.copyOf(matchers);
        }

        private static JsonElement toJson(Entry entry) {
            JsonObject object = new JsonObject();
            entry.fluids().ifPresent(matchers -> object.add(
                    "fluids",
                    writeMatchers(matchers)
            ));
            entry.items().ifPresent(matchers -> object.add(
                    "items",
                    writeMatchers(matchers)
            ));
            object.addProperty("priority", entry.priority());
            return object;
        }

        private static JsonArray writeMatchers(List<Matcher> matchers) {
            JsonArray values = new JsonArray();
            for (Matcher matcher : matchers)
                values.add(matcher.serialize());
            return values;
        }
    }

    /**
     * Profiles are atomic: if two packs define the same engine key, NeoForge's
     * standard higher-pack-wins behaviour replaces the whole {@link Entry}.
     */
    public static final DataMapType<Block, Entry> TYPE = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(CreateTrainPhysics.MODID, "engine_fuels"),
            Registries.BLOCK,
            Entry.CODEC
    ).build();

    public static Policy resolve(Map<String, Integer> engineCounts) {
        Objects.requireNonNull(engineCounts, "engineCounts");
        Map<ResourceKey<Block>, Entry> dataMap = BuiltInRegistries.BLOCK.getDataMap(TYPE);
        Map<String, Entry> entries = new LinkedHashMap<>();
        Map<String, Integer> priorities = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> engine : engineCounts.entrySet()) {
            if (engine.getKey() == null || engine.getValue() == null || engine.getValue() <= 0)
                continue;
            ResourceLocation engineId = ResourceLocation.tryParse(engine.getKey());
            if (engineId == null)
                continue;
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, engineId);
            Entry entry = dataMap.get(key);
            if (entry != null) {
                entries.put(engine.getKey(), entry);
                priorities.put(engine.getKey(), entry.priority());
            }
        }
        return new Policy(
                EngineFuelPlan.resolve(engineCounts, priorities),
                entries
        );
    }

    public static final class Policy {
        private final EngineFuelPlan plan;
        private final Map<String, Entry> entries;

        private Policy(EngineFuelPlan plan, Map<String, Entry> entries) {
            this.plan = plan;
            this.entries = Map.copyOf(entries);
        }

        public EngineFuelPlan plan() {
            return plan;
        }

        public List<FuelCandidate> candidates(
                FuelKey.Kind kind,
                Set<String> enginesNeedingFuel
        ) {
            List<FuelCandidate> candidates = new ArrayList<>();
            for (EngineFuelPlan.EngineRule rule : plan.rules()) {
                if (!enginesNeedingFuel.contains(rule.engineId()))
                    continue;
                Entry entry = entries.get(rule.engineId());
                if (entry == null) {
                    candidates.add(new FuelCandidate(rule.engineId(), Matcher.ANY));
                    continue;
                }
                for (Matcher matcher : entry.matchers(kind))
                    candidates.add(new FuelCandidate(rule.engineId(), matcher));
            }
            return List.copyOf(candidates);
        }

        public boolean matches(FuelCandidate candidate, ItemStack stack) {
            return candidate != null && matchesItem(stack, candidate.matcher());
        }

        public boolean matches(FuelCandidate candidate, FluidStack stack) {
            return candidate != null && matchesFluid(stack, candidate.matcher());
        }

        public boolean accepts(String engineId, FuelKey fuel) {
            if (!plan.engineIds().contains(engineId))
                return false;
            ResourceLocation fuelId = ResourceLocation.tryParse(fuel.id());
            if (fuelId == null)
                return false;

            Entry entry = entries.get(engineId);
            if (fuel.kind() == FuelKey.Kind.ITEM) {
                if (!BuiltInRegistries.ITEM.containsKey(fuelId))
                    return false;
                if (entry == null)
                    return true;
                Item item = BuiltInRegistries.ITEM.get(fuelId);
                ItemStack stack = new ItemStack(item);
                return entry.matchers(FuelKey.Kind.ITEM).stream()
                        .anyMatch(matcher -> matchesItem(stack, matcher));
            }
            if (!BuiltInRegistries.FLUID.containsKey(fuelId))
                return false;
            if (entry == null)
                return true;
            Fluid fluid = BuiltInRegistries.FLUID.get(fuelId);
            FluidStack stack = new FluidStack(fluid, 1);
            return entry.matchers(FuelKey.Kind.FLUID).stream()
                    .anyMatch(matcher -> matchesFluid(stack, matcher));
        }

        public boolean hasConfiguredProfiles() {
            return !entries.isEmpty();
        }
    }

    public record FuelCandidate(String engineId, Matcher matcher) {
        public FuelCandidate {
            Objects.requireNonNull(engineId, "engineId");
            Objects.requireNonNull(matcher, "matcher");
        }
    }

    private static boolean matchesFluid(FluidStack stack, Matcher matcher) {
        if (stack == null || stack.isEmpty() || matcher.invalid())
            return false;
        ExtraCodecs.TagOrElementLocation location = matcher.location();
        if (location == null)
            return true;
        if (location.tag())
            return stack.is(TagKey.create(Registries.FLUID, location.id()));
        return location.id().equals(BuiltInRegistries.FLUID.getKey(stack.getFluid()));
    }

    private static boolean matchesItem(ItemStack stack, Matcher matcher) {
        if (stack == null || stack.isEmpty() || matcher.invalid())
            return false;
        ExtraCodecs.TagOrElementLocation location = matcher.location();
        if (location == null)
            return true;
        if (location.tag())
            return stack.is(TagKey.create(Registries.ITEM, location.id()));
        return location.id().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
