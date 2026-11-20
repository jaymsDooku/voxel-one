package dev.jayms.combat;

import java.util.List;
import java.util.Objects;

/** Slot order is stable, zero based, and never auto-sorted. Empty slots are explicit. */
public record KitProfile(String id, List<Stack> slots, List<Armor> armor) {
    public enum Item { EMPTY, SWORD, BOW, ARROW, HEAL_SPLASH, SPEED_DRINK, FIRE_RESIST_DRINK,
        FOOD, PEARL, SLOWNESS_SPLASH, POISON_SPLASH }
    public enum ArmorSlot { HEAD, CHEST, LEGS, FEET }
    public record Stack(Item item, int count) {
        public static final Stack EMPTY = new Stack(Item.EMPTY, 0);
        public Stack {
            Objects.requireNonNull(item);
            if (count < 0 || count > 64 || (item == Item.EMPTY) != (count == 0)
                    || ((item == Item.SWORD || item == Item.BOW) && count != 1))
                throw new IllegalArgumentException("Invalid stack");
        }
    }
    /** Protection is additive across intact slots, capped at 80%; no hidden enchant formula. */
    public record Armor(ArmorSlot slot, double protection, int durability) {
        public Armor {
            Objects.requireNonNull(slot);
            if (!Double.isFinite(protection) || protection < 0 || protection > .8
                    || durability < 0 || durability > 100000) throw new IllegalArgumentException("Invalid armor");
        }
    }
    public KitProfile {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Kit id");
        slots = List.copyOf(slots); armor = List.copyOf(armor);
        if (slots.isEmpty() || slots.size() > 36 || armor.size() > 4
                || armor.stream().map(Armor::slot).distinct().count() != armor.size())
            throw new IllegalArgumentException("Invalid kit size or duplicate armor slot");
    }
    public static KitProfile practice() {
        return new KitProfile("finite-practice", List.of(new Stack(Item.SWORD, 1),
                new Stack(Item.HEAL_SPLASH, 16), new Stack(Item.PEARL, 8),
                new Stack(Item.SPEED_DRINK, 2), new Stack(Item.FIRE_RESIST_DRINK, 2),
                new Stack(Item.FOOD, 8), new Stack(Item.BOW, 1), new Stack(Item.ARROW, 32)),
                List.of(new Armor(ArmorSlot.HEAD, .08, 100), new Armor(ArmorSlot.CHEST, .24, 200),
                        new Armor(ArmorSlot.LEGS, .20, 180), new Armor(ArmorSlot.FEET, .08, 100)));
    }
}
