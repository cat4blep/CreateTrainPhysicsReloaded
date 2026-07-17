package dev.szedann.create_train_physics.physics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainFuelSelectionTest {
    @Test
    void drainsOnlyTheFirstUsableSource() {
        List<Integer> visited = new ArrayList<>();

        int result = TrainFuelSelection.drainFirst(4, false, index -> {
            visited.add(index);
            return index == 1 ? 80 : index == 2 ? 120 : 0;
        });

        assertEquals(80, result);
        assertEquals(List.of(0, 1), visited);
    }

    @Test
    void searchesFromTheBackWhenTheTrainIsReversed() {
        List<Integer> visited = new ArrayList<>();

        int result = TrainFuelSelection.drainFirst(4, true, index -> {
            visited.add(index);
            return index == 2 ? 60 : 0;
        });

        assertEquals(60, result);
        assertEquals(List.of(3, 2), visited);
    }

    @Test
    void solidFuelIsUsedOnlyAsFallback() {
        assertTrue(TrainFuelSelection.mayUseSolidFuel(0));
        assertTrue(TrainFuelSelection.mayUseSolidFuel(-1));
        assertFalse(TrainFuelSelection.mayUseSolidFuel(1));
    }
}
