package dev.szedann.create_train_physics.physics;

import java.util.Objects;

/** Stable identity of a fuel portion after it has been removed from storage. */
public record FuelKey(Kind kind, String id) {
    public FuelKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (id.isBlank())
            throw new IllegalArgumentException("id cannot be blank");
    }

    public static FuelKey item(String id) {
        return new FuelKey(Kind.ITEM, id);
    }

    public static FuelKey fluid(String id) {
        return new FuelKey(Kind.FLUID, id);
    }

    public enum Kind {
        ITEM,
        FLUID
    }
}
