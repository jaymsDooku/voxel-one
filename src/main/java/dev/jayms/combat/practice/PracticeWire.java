package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.*;
import dev.jayms.combat.practice.PracticeMatch.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Independent practice protocol. Legacy City and Sandbox frames use their existing protocol. */
final class PracticeWire {
    static final int MAGIC = 0x56505241, LEGACY_VERSION = 1, POSE_VERSION = 2, CLICK_VERSION = 3,
            WALL_VERSION = 4, IMPULSE_VERSION = 5, SWING_VERSION = 6, KNOCKBACK_LAB_VERSION = 7,
            VERSION = KNOCKBACK_LAB_VERSION;
    static final float MAX_VELOCITY = 3.9f; // Paper 1.7.10 entity velocity packet component bound.
    static final int CONTROL = 1, READY = 2, SWAP = 3, ACTION = 4;
    // SWAP with an out-of-range destination is ignored by older arena servers.
    static final int QUICK_MOVE = 255;

    private PracticeWire() { }

    static void writeIntent(DataOutputStream out, Intent i, int version) throws IOException {
        out.writeByte(CONTROL);
        out.writeLong(i.sequence());
        out.writeFloat(i.yaw());
        out.writeFloat(i.pitch());
        out.writeFloat(i.forward());
        out.writeFloat(i.strafe());
        out.writeByte((i.jump() ? 1 : 0) | (i.sprint() ? 2 : 0) | (i.attack() ? 4 : 0) | (i.use() ? 8 : 0));
        out.writeByte(i.slot());
        if (version >= CLICK_VERSION && i.attack()) {
            AttackView shown = i.attackView();
            out.writeBoolean(shown != null);
            if (shown != null) {
                out.writeInt(shown.targetId());
                writePoseView(out, shown.attacker());
                writePoseView(out, shown.target());
            }
        }
    }

    static Intent readIntent(DataInputStream in, int version) throws IOException {
        long sequence = in.readLong();
        float yaw = finite(in), pitch = finite(in), forward = finite(in), strafe = finite(in);
        int flags = in.readUnsignedByte(), slot = in.readUnsignedByte();
        if (sequence < 0 || Math.abs(yaw) > 360 || Math.abs(pitch) > 89 || Math.abs(forward) > 1
                || Math.abs(strafe) > 1 || flags > 15 || slot > 8) throw new IOException("Invalid combat input");
        boolean attack = (flags & 4) != 0;
        AttackView shown = null;
        if (version >= CLICK_VERSION && attack && in.readBoolean()) {
            try {
                shown = new AttackView(in.readInt(), readPoseView(in), readPoseView(in));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Invalid attack view", invalid);
            }
        }
        return new Intent(sequence, yaw, pitch, forward, strafe, (flags & 1) != 0, (flags & 2) != 0,
                attack, (flags & 8) != 0, slot, shown);
    }

    private static void writePoseView(DataOutputStream out, PoseView pose) throws IOException {
        out.writeLong(pose.first());
        out.writeLong(pose.last());
        out.writeDouble(pose.tick());
    }

    private static PoseView readPoseView(DataInputStream in) throws IOException {
        return new PoseView(in.readLong(), in.readLong(), in.readDouble());
    }

    static void writeView(DataOutputStream out, View v, int version) throws IOException {
        out.writeByte(v.phase().ordinal());
        out.writeByte(v.kit().ordinal());
        out.writeByte(v.result().ordinal());
        out.writeInt(v.winner());
        out.writeShort(v.ticksLeft());
        out.writeInt(v.spectatorId());
        out.writeLong(v.tick());
        out.writeByte(v.fighters().size());
        for (Fighter f : v.fighters()) {
            out.writeInt(f.id());
            out.writeUTF(f.name());
            out.writeFloat(f.x());
            out.writeFloat(f.y());
            out.writeFloat(f.z());
            out.writeFloat(f.yaw());
            out.writeFloat(f.pitch());
            out.writeFloat(f.health());
            out.writeBoolean(f.moving());
            out.writeBoolean(f.swinging());
            out.writeBoolean(f.ready());
            out.writeByte(f.held().ordinal());
            out.writeInt(f.hits());
            out.writeInt(f.combo());
            out.writeInt(f.used());
            out.writeInt(f.remaining());
            out.writeByte(f.side());
            out.writeUTF(f.role());
            if (version >= POSE_VERSION) {
                out.writeFloat(f.vx());
                out.writeFloat(f.vy());
                out.writeFloat(f.vz());
                out.writeLong(f.hurtTick());
                out.writeLong(f.motionEpoch());
            }
            if (version >= WALL_VERSION) out.writeLong(f.lastHorizontalWallTick());
            if (version >= IMPULSE_VERSION) {
                out.writeLong(f.impulseTick());
                out.writeFloat(f.impulseX());
                out.writeFloat(f.impulseY());
                out.writeFloat(f.impulseZ());
            }
            if (version >= SWING_VERSION) out.writeLong(f.swingTick());
        }
        out.writeByte(v.inventory().size());
        for (Stack stack : v.inventory()) {
            out.writeByte(stack.item().ordinal());
            out.writeShort(stack.count());
        }
        out.writeByte(v.food());
        out.writeShort(v.cooldown());
        for (Effect effect : Effect.values()) out.writeInt(v.effects().getOrDefault(effect, 0));
        out.writeByte(v.durability().size());
        for (int value : v.durability()) out.writeShort(value);
        out.writeByte(v.missiles().size());
        for (Missile m : v.missiles()) {
            out.writeLong(m.id());
            out.writeByte(m.item().ordinal());
            out.writeFloat(m.x());
            out.writeFloat(m.y());
            out.writeFloat(m.z());
        }
        out.writeLong(v.lastHit());
        out.writeLong(v.lastSplash());
        out.writeByte(Math.min(v.status().size(), 8));
        for (int i = 0; i < v.status().size() && i < 8; i++) {
            String line = v.status().get(i);
            out.writeUTF(line.substring(0, Math.min(line.length(), 120)));
        }
    }

    static View readView(DataInputStream in, int version) throws IOException {
        Phase phase = readEnum(in, Phase.values());
        Kit kit = readEnum(in, Kit.values());
        Result result = readEnum(in, Result.values());
        int winner = in.readInt(), ticksLeft = in.readUnsignedShort(), spectatorId = in.readInt();
        long tick = in.readLong();
        int count = count(in, 4);
        List<Fighter> fighters = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = in.readInt();
            String name = in.readUTF();
            if (!name.matches("[A-Za-z0-9_ ]{1,16}")) throw new IOException("Invalid fighter name");
            float x = finite(in), y = finite(in), z = finite(in), yaw = finite(in), pitch = finite(in), health = finite(in);
            boolean moving = in.readBoolean(), swinging = in.readBoolean(), ready = in.readBoolean();
            Item held = readEnum(in, Item.values());
            int hits = in.readInt(), combo = in.readInt(), used = in.readInt(), remaining = in.readInt();
            int side = in.readByte();
            String role = in.readUTF();
            if (side < -1 || side > 1 || !role.matches("[A-Za-z]{0,12}"))
                throw new IOException("Invalid fighter affiliation");
            float vx = 0, vy = 0, vz = 0;
            long hurtTick = -1, motionEpoch = 0;
            if (version >= POSE_VERSION) {
                vx = finite(in);
                vy = finite(in);
                vz = finite(in);
                hurtTick = in.readLong();
                motionEpoch = in.readLong();
                if (hurtTick < -1 || hurtTick > tick || motionEpoch < 0)
                    throw new IOException("Invalid fighter timing");
            }
            long lastHorizontalWallTick = version >= WALL_VERSION ? in.readLong() : -1;
            if (lastHorizontalWallTick < -1 || lastHorizontalWallTick > tick)
                throw new IOException("Invalid fighter collision timing");
            long impulseTick = -1;
            float impulseX = 0, impulseY = 0, impulseZ = 0;
            if (version >= IMPULSE_VERSION) {
                impulseTick = in.readLong();
                impulseX = velocity(in);
                impulseY = velocity(in);
                impulseZ = velocity(in);
                if (impulseTick < -1 || impulseTick > tick) throw new IOException("Invalid fighter impulse timing");
            }
            long swingTick = version >= SWING_VERSION ? in.readLong() : -1;
            if (swingTick < -1 || swingTick > tick) throw new IOException("Invalid fighter swing timing");
            fighters.add(new Fighter(id, name, x, y, z, yaw, pitch, health, moving, swinging, ready,
                    held, hits, combo, used, remaining, side, role, vx, vy, vz, hurtTick, motionEpoch,
                    lastHorizontalWallTick, impulseTick, impulseX, impulseY, impulseZ, swingTick));
        }
        count = count(in, 36);
        if (count != 0 && count != 36) throw new IOException("Invalid practice inventory");
        List<Stack> inventory = new ArrayList<>(count);
        for (int i = 0; i < count; i++) inventory.add(new Stack(readEnum(in, Item.values()), in.readUnsignedShort()));
        int food = in.readUnsignedByte(), cooldown = in.readUnsignedShort();
        var effects = new EnumMap<Effect, Integer>(Effect.class);
        for (Effect effect : Effect.values()) {
            int duration = in.readInt();
            if (duration > 0) effects.put(effect, duration);
        }
        count = count(in, 4);
        var durability = new ArrayList<Integer>(count);
        for (int i = 0; i < count; i++) durability.add(in.readUnsignedShort());
        count = count(in, 64);
        var missiles = new ArrayList<Missile>(count);
        for (int i = 0; i < count; i++)
            missiles.add(new Missile(in.readLong(), readEnum(in, Item.values()), finite(in), finite(in), finite(in)));
        long hit = in.readLong(), splash = in.readLong();
        count = count(in, 8);
        var status = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            String line = in.readUTF();
            if (line.length() > 120) throw new IOException("Invalid arena status");
            status.add(line);
        }
        return new View(phase, kit, result, winner, ticksLeft, spectatorId, tick, List.copyOf(fighters),
                List.copyOf(inventory), food, cooldown, java.util.Map.copyOf(effects), List.copyOf(durability),
                List.copyOf(missiles), hit, splash, List.copyOf(status));
    }

    private static int count(DataInputStream in, int max) throws IOException {
        int value = in.readUnsignedByte();
        if (value > max) throw new IOException("Invalid practice frame");
        return value;
    }

    private static <E extends Enum<E>> E readEnum(DataInputStream in, E[] values) throws IOException {
        int ordinal = count(in, values.length - 1);
        return values[ordinal];
    }

    private static float finite(DataInputStream in) throws IOException {
        float value = in.readFloat();
        if (!Float.isFinite(value)) throw new IOException("Invalid practice coordinates");
        return value;
    }

    private static float velocity(DataInputStream in) throws IOException {
        float value = finite(in);
        if (Math.abs(value) > MAX_VELOCITY) throw new IOException("Invalid fighter impulse");
        return value;
    }
}
