package dev.jayms.combat.hcf;

import dev.jayms.combat.duel.DuelSession;
import java.util.Objects;

/** Inputs only. No client position, target, damage, team mutation or administrative command. */
public record HcfCommand(long season,long sequence,long frame,Op op,double forward,double strafe,
                         double yaw,double pitch,boolean jump,boolean sprint,boolean attack,
                         boolean use,boolean release,int slot,int choice) {
    public enum Op { INPUT, EQUIP, DEPLOY, HOME, SAFE_LOGOUT, ABILITY, RESUPPLY, CLAIM, RAID, LOOT, REFILL }
    public HcfCommand {
        Objects.requireNonNull(op);
        new DuelSession.Command(season,sequence,frame,DuelSession.Op.INPUT,forward,strafe,yaw,pitch,jump,sprint,attack,use,release,slot,choice);
    }
    public static HcfCommand operation(long season,long sequence,Op op,int choice) {
        return new HcfCommand(season,sequence,0,op,0,0,0,0,false,false,false,false,false,0,choice);
    }
}
