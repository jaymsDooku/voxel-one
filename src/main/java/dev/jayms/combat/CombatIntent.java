package dev.jayms.combat;

import java.util.Objects;

/** Sequence is a positive per-participant high-water mark, retained across recovery. */
public record CombatIntent(long sequence, Action action) {
    public CombatIntent {
        if (sequence < 1) throw new IllegalArgumentException("Sequence must be positive");
        Objects.requireNonNull(action);
    }
    public sealed interface Action permits Swing, Sprint, Guard, Select, StartUse, ReleaseUse, CancelUse {}
    public record Swing(Vec3 direction) implements Action { public Swing { direction = direction.unit(); } }
    public record Sprint(boolean active) implements Action {}
    public record Guard(boolean active) implements Action {}
    public record Select(int slot) implements Action { public Select { if (slot < 0 || slot >= 36) throw new IllegalArgumentException("Slot"); } }
    public record StartUse(Vec3 direction) implements Action { public StartUse { direction = direction.unit(); } }
    public record ReleaseUse(Vec3 direction) implements Action { public ReleaseUse { direction = direction.unit(); } }
    public record CancelUse() implements Action {}
}
