package dev.jayms.combat.hcf;

import dev.jayms.combat.*;
import dev.jayms.combat.KitProfile.*;
import dev.jayms.combat.KitProfile.Stack;
import java.util.*;

/** Original armor signatures; each intact slot must match. No third-party material or art system. */
public enum HcfRole {
    FIGHTER(.60,8), BARD(.32,1), ARCHER(.28,1), ROGUE(.36,1), MINER(.44,2);
    public final double protection; public final int limit;
    HcfRole(double protection,int limit) {this.protection=protection;this.limit=limit;}
    public KitProfile kit() {
        var slots=new ArrayList<Stack>(Collections.nCopies(36,Stack.EMPTY));
        slots.set(0,new Stack(Item.SWORD,1));slots.set(1,new Stack(Item.HEAL_SPLASH,1));
        slots.set(2,new Stack(Item.PEARL,4));slots.set(3,new Stack(Item.SPEED_DRINK,2));
        slots.set(4,new Stack(Item.FIRE_RESIST_DRINK,1));slots.set(5,new Stack(Item.FOOD,8));
        slots.set(6,new Stack(Item.BOW,1));slots.set(7,new Stack(Item.POISON_SPLASH,1));slots.set(8,new Stack(Item.SLOWNESS_SPLASH,1));
        slots.set(9,new Stack(Item.ARROW,this==ARCHER?32:8));
        for(int i=10;i<26;i++) slots.set(i,new Stack(Item.HEAL_SPLASH,1));
        return new KitProfile("hcf-"+name(),slots,Arrays.stream(ArmorSlot.values()).map(slot->new Armor(slot,protection/4,240)).toList());
    }
    public static Optional<HcfRole> from(List<Armor> armor) {
        if(armor.size()!=4 || armor.stream().map(Armor::slot).distinct().count()!=4 || armor.stream().anyMatch(a->a.durability()<=0)) return Optional.empty();
        return Arrays.stream(values()).filter(r->armor.stream().allMatch(a->Math.abs(a.protection()-r.protection/4)<1e-9)).findFirst();
    }
}
