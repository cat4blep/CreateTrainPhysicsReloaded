package dev.szedann.create_train_physics.physics;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineFuelPlanTest {
    private static final String DIESEL = "test:diesel_engine";
    private static final String GASOLINE = "test:gasoline_engine";
    private static final String STEAM = "test:steam_engine";

    @Test
    void keepsEveryEngineTypeAndItsCountSeparate() {
        EngineFuelPlan plan = EngineFuelPlan.resolve(
                Map.of(STEAM, 3, DIESEL, 1),
                Map.of(STEAM, 10, DIESEL, 20)
        );

        assertEquals(List.of(DIESEL, STEAM), ids(plan));
        assertEquals(1, plan.rules().get(0).engineCount());
        assertEquals(3, plan.rules().get(1).engineCount());
        assertEquals(4, plan.totalEngineCount());
    }

    @Test
    void unrestrictedTypesAlwaysFollowConfiguredTypes() {
        EngineFuelPlan plan = EngineFuelPlan.resolve(
                Map.of(STEAM, 1, DIESEL, 1),
                Map.of(STEAM, Integer.MIN_VALUE)
        );

        assertEquals(List.of(STEAM, DIESEL), ids(plan));
        assertFalse(plan.rules().get(0).unrestricted());
        assertTrue(plan.rules().get(1).unrestricted());
    }

    @Test
    void registryIdBreaksEqualPriorityTiesDeterministically() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        counts.put(STEAM, 1);
        counts.put(GASOLINE, 1);
        counts.put(DIESEL, 1);

        EngineFuelPlan plan = EngineFuelPlan.resolve(
                counts,
                Map.of(STEAM, 5, GASOLINE, 5, DIESEL, 5)
        );

        assertEquals(List.of(DIESEL, GASOLINE, STEAM), ids(plan));
    }

    @Test
    void ignoresNonPositiveAndMissingCounts() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        counts.put(STEAM, 0);
        counts.put(DIESEL, -2);
        counts.put(GASOLINE, 2);
        counts.put(null, 4);

        EngineFuelPlan plan = EngineFuelPlan.resolve(counts, Map.of());

        assertEquals(List.of(GASOLINE), ids(plan));
        assertEquals(2, plan.totalEngineCount());
    }

    private static List<String> ids(EngineFuelPlan plan) {
        return plan.rules().stream().map(EngineFuelPlan.EngineRule::engineId).toList();
    }
}
