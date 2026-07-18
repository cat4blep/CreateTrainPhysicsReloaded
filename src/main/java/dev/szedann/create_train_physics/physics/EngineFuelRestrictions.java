package dev.szedann.create_train_physics.physics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.szedann.create_train_physics.CreateTrainPhysics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Optional, data-driven fuel restrictions for combustion engines.
 *
 * <p>Entries live in the {@code create_train_physics:engine_fuels} data map.
 * Its file path is fixed to the map's own namespace:
 * {@code data/create_train_physics/data_maps/block/engine_fuels.json}.
 * Any datapack or mod contributes by providing a file at that exact path;
 * entries from multiple sources merge.
 *
 * <p>Engines rank by their optional {@code priority} (default 0, higher
 * first): as long as any fluid of a higher-priority engine is available
 * anywhere on the train, lower-priority engines' fuels are not touched.
 * Within one priority, {@code fluids} arrays merge by index — earlier
 * entries drain first, train-wide. Each element is a fluid id or
 * {@code #tag}; portion size and burn time stay defined by Steam 'n' Rails'
 * own {@code railways_liquid_fuel} registry. {@code items} arrays order the
 * same way, but strictly after fluids: no item is consumed while any
 * entitled fluid exists anywhere on the train. A missing {@code fluids} or
 * {@code items} list means the engine accepts no fuel of that kind. The
 * literal {@code "*"} as a list element matches anything of that kind at
 * that position — e.g. {@code ["#minecraft:coals", "*"]} prefers coals,
 * then any burnable item.
 *
 * <p>Engines without an entry rank below every listed one, together
 * permitting any S&R-valid fluid and any burnable item as the last resort.
 * A pack using this feature should therefore cover all of its engines;
 * uncovered ones still work, just without a defined position in the order.
 * Only when no combustion engine aboard has an entry (or the data map is
 * empty) does the resolver return null and every fuel path behave exactly
 * as before.
 *
 * <p>If any entry in the file fails to parse, NeoForge discards the whole
 * data map with an ERROR log line and the resolver sees an empty map — all
 * restrictions silently lift. Check the log for "Could not read data map"
 * whenever behaviour looks unrestricted.
 */
public final class EngineFuelRestrictions {

    private EngineFuelRestrictions() {
    }

    /**
     * One data-map entry. A missing list means "accepts none of that kind".
     * Ids and {@code #tags} are stored as plain strings and resolved at match
     * time: data maps decode before tag contents are available, so a
     * registry-aware holder-set codec cannot be used here. An unknown id
     * simply never matches.
     * One list element: an id, a {@code #tag}, or the literal {@code "*"}
     * matching anything of that kind. Wildcards never bypass validity —
     * fluids still pass S&R's fuel check and items their burn-time check.
     */
    public record Matcher(@Nullable ExtraCodecs.TagOrElementLocation location) {
        public static final Matcher ANY = new Matcher(null);

        private static final Codec<Matcher> CODEC = Codec.STRING.comapFlatMap(
                Matcher::parse,
                Matcher::serialize
        );

        private static DataResult<Matcher> parse(String string) {
            if (string.equals("*"))
                return DataResult.success(ANY);
            boolean tag = string.startsWith("#");
            return ResourceLocation.read(tag ? string.substring(1) : string)
                    .map(id -> new Matcher(new ExtraCodecs.TagOrElementLocation(id, tag)));
        }

        private String serialize() {
            if (location == null)
                return "*";
            return (location.tag() ? "#" : "") + location.id();
        }
    }

    public record Entry(
            Optional<List<Matcher>> fluids,
            Optional<List<Matcher>> items,
            int priority
    ) {
        private static final Codec<List<Matcher>> MATCHER_LIST_CODEC =
                Codec.list(Matcher.CODEC);

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MATCHER_LIST_CODEC
                        .optionalFieldOf("fluids")
                        .forGetter(Entry::fluids),
                MATCHER_LIST_CODEC
                        .optionalFieldOf("items")
                        .forGetter(Entry::items),
                Codec.INT
                        .optionalFieldOf("priority", 0)
                        .forGetter(Entry::priority)
        ).apply(instance, Entry::new));
    }

    public static final DataMapType<Block, Entry> TYPE = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(CreateTrainPhysics.MODID, "engine_fuels"),
            Registries.BLOCK,
            Entry.CODEC
    ).build();

    /** One priority rank: merged fluid and item tiers of its engines. */
    private record Group(
            List<List<Matcher>> fluidTiers,
            List<List<Matcher>> itemTiers,
            boolean wildcard
    ) {
    }

    /**
     * Merged view of every combustion engine aboard one train, as priority
     * groups in descending order. Entry-less engines form a trailing
     * wildcard group instead of lifting the restriction.
     */
    public static final class Policy {
        private final List<Group> groups;

        private Policy(List<Group> groups) {
            this.groups = groups;
        }

        public int groupCount() {
            return groups.size();
        }

        /** Whether this group permits any S&R-valid fluid (entry-less engines). */
        public boolean isWildcardGroup(int group) {
            return groups.get(group).wildcard();
        }

        public int fluidTierCount(int group) {
            return groups.get(group).fluidTiers().size();
        }

        public boolean fluidMatchesTier(FluidStack stack, int group, int tier) {
            if (stack.isEmpty())
                return false;
            List<List<Matcher>> tiers = groups.get(group).fluidTiers();
            if (tier >= tiers.size())
                return false;
            for (Matcher matcher : tiers.get(tier))
                if (matchesFluid(stack, matcher))
                    return true;
            return false;
        }

        public int itemTierCount(int group) {
            return groups.get(group).itemTiers().size();
        }

        /** Burn-time validity is the caller's job; this only answers entitlement. */
        public boolean itemMatchesTier(ItemStack stack, int group, int tier) {
            if (stack.isEmpty())
                return false;
            List<List<Matcher>> tiers = groups.get(group).itemTiers();
            if (tier >= tiers.size())
                return false;
            for (Matcher matcher : tiers.get(tier))
                if (matchesItem(stack, matcher))
                    return true;
            return false;
        }
    }

    private static boolean matchesFluid(FluidStack stack, Matcher matcher) {
        ExtraCodecs.TagOrElementLocation location = matcher.location();
        if (location == null)
            return true;
        if (location.tag())
            return stack.is(TagKey.create(Registries.FLUID, location.id()));
        return location.id().equals(BuiltInRegistries.FLUID.getKey(stack.getFluid()));
    }

    private static boolean matchesItem(ItemStack stack, Matcher matcher) {
        ExtraCodecs.TagOrElementLocation location = matcher.location();
        if (location == null)
            return true;
        if (location.tag())
            return stack.is(TagKey.create(Registries.ITEM, location.id()));
        return location.id().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * Null means fully unrestricted: the data map is empty, the engine set is
     * not known yet (rescan pending on an unloaded legacy train), no
     * combustion engine is aboard, or none of them has an entry. Unknown
     * identity must fail open — a train may not starve because a rescan has
     * not happened yet — while a restricted train with no acceptable fuel
     * aboard correctly stays unfueled.
     */
    @Nullable
    public static Policy resolve(@Nullable Set<Block> combustionEngines) {
        if (combustionEngines == null || combustionEngines.isEmpty())
            return null;

        Map<ResourceKey<Block>, Entry> dataMap = BuiltInRegistries.BLOCK.getDataMap(TYPE);
        if (dataMap.isEmpty())
            return null;

        TreeMap<Integer, List<Entry>> byPriority = new TreeMap<>(Comparator.reverseOrder());
        boolean anyUnrestricted = false;

        for (Block block : combustionEngines) {
            Entry entry = BuiltInRegistries.BLOCK.getResourceKey(block)
                    .map(dataMap::get)
                    .orElse(null);
            if (entry == null) {
                anyUnrestricted = true;
                continue;
            }
            byPriority.computeIfAbsent(entry.priority(), key -> new ArrayList<>()).add(entry);
        }

        if (byPriority.isEmpty())
            return null;

        List<Group> groups = new ArrayList<>(byPriority.size() + 1);
        for (List<Entry> bucket : byPriority.values()) {
            List<List<Matcher>> fluidTiers = new ArrayList<>();
            List<List<Matcher>> itemTiers = new ArrayList<>();
            for (Entry entry : bucket) {
                mergeTiers(fluidTiers, entry.fluids().orElse(List.of()));
                mergeTiers(itemTiers, entry.items().orElse(List.of()));
            }
            groups.add(new Group(fluidTiers, itemTiers, false));
        }
        if (anyUnrestricted)
            groups.add(new Group(List.of(), List.of(), true));

        return new Policy(groups);
    }

    private static void mergeTiers(
            List<List<Matcher>> merged,
            List<Matcher> tiers
    ) {
        for (int tier = 0; tier < tiers.size(); tier++) {
            if (merged.size() <= tier)
                merged.add(new ArrayList<>());
            merged.get(tier).add(tiers.get(tier));
        }
    }
}