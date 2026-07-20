package dev.szedann.create_train_physics.physics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure, immutable ordering of the combustion engine types aboard a train.
 *
 * <p>Configured types are ordered by descending priority, with the registry id
 * as a stable tie breaker. Types without a configured rule remain distinct and
 * form the trailing unrestricted portion of the plan.
 */
public final class EngineFuelPlan {
    private static final Comparator<EngineRule> RULE_ORDER =
            Comparator.comparing(EngineRule::unrestricted)
                    .thenComparing(EngineRule::priority, Comparator.reverseOrder())
                    .thenComparing(EngineRule::engineId);

    private final List<EngineRule> rules;
    private final Set<String> engineIds;

    private EngineFuelPlan(List<EngineRule> rules) {
        this.rules = List.copyOf(rules);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (EngineRule rule : this.rules)
            ids.add(rule.engineId());
        this.engineIds = Collections.unmodifiableSet(ids);
    }

    /**
     * Resolve one rule per positive engine count. A missing priority means that
     * the engine has no data-driven restriction and must be considered only
     * after every configured engine, regardless of numeric priority values.
     */
    public static EngineFuelPlan resolve(
            Map<String, Integer> engineCounts,
            Map<String, Integer> configuredPriorities
    ) {
        Objects.requireNonNull(engineCounts, "engineCounts");
        Objects.requireNonNull(configuredPriorities, "configuredPriorities");

        List<EngineRule> resolved = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : engineCounts.entrySet()) {
            String engineId = entry.getKey();
            Integer count = entry.getValue();
            if (engineId == null || count == null || count <= 0)
                continue;

            Integer priority = configuredPriorities.get(engineId);
            resolved.add(new EngineRule(
                    engineId,
                    count,
                    priority == null ? 0 : priority,
                    priority == null
            ));
        }
        resolved.sort(RULE_ORDER);
        return new EngineFuelPlan(resolved);
    }

    public List<EngineRule> rules() {
        return rules;
    }

    public Set<String> engineIds() {
        return engineIds;
    }

    public int totalEngineCount() {
        long count = 0;
        for (EngineRule rule : rules)
            count += rule.engineCount();
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    public record EngineRule(
            String engineId,
            int engineCount,
            int priority,
            boolean unrestricted
    ) {
        public EngineRule {
            Objects.requireNonNull(engineId, "engineId");
            if (engineId.isBlank())
                throw new IllegalArgumentException("engineId cannot be blank");
            if (engineCount <= 0)
                throw new IllegalArgumentException("engineCount must be positive");
        }
    }
}
