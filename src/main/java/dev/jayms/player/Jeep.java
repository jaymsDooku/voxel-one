package dev.jayms.player;

import dev.jayms.World;
import dev.jayms.net.Blocks;
import org.joml.Vector3f;
import java.io.IOException;
import java.nio.file.*;

/** Four-wheel jeep physics. Its full body is swept in small steps, including while turning. */
public final class Jeep {
    private final Vector3f position;
    private float yaw, speed, fallSpeed, wheelPhase;
    private boolean driving;
    public Jeep(Vector3f position, float yaw) {
        this.position = new Vector3f(position);
        this.yaw = yaw;
    }
    public Vector3f position() { return new Vector3f(position); }
    public float yaw() { return yaw; }
    public float speed() { return speed; }
    public float wheelPhase() { return wheelPhase; }
    public boolean driving() { return driving; }
    /** Respawn releases the seat and leaves a parked vehicle behind. */
    public void releaseDriver() { driving = false; speed = 0; }
    private Vector3f local(float x, float y, float z) {
        double a = Math.toRadians(-yaw - 90);
        return new Vector3f(position).add((float)(Math.cos(a)*x + Math.sin(a)*z), y,
                (float)(-Math.sin(a)*x + Math.cos(a)*z));
    }
    // Player hip is .75 above its pose origin; align it with the .85-high cushion.
    public Vector3f seat() { return local(-.45f, .10f, .25f); }
    public boolean enter(Player player) {
        if (driving || player.mounted() || Math.abs(speed) > .5f || player.position().distance(position) > 4) return false;
        driving = true;
        player.driveSeat(seat(), yaw);
        return true;
    }
    /** Never drop a driver into a wall or an unloaded column. */
    public boolean exit(World world, Player player) {
        if (!driving || Math.abs(speed) > .5f) return false;
        for (float side : new float[]{-1.7f, 1.7f}) {
            Vector3f candidate = local(side, .1f, .25f);
            Player probe = new Player(candidate, player.yaw(), player.pitch(), new dev.jayms.Camera());
            if (!probe.collides(world) && supported(world)
                    && world.isLoaded((int)Math.floor(candidate.x),(int)Math.floor(position.y-.1f),(int)Math.floor(candidate.z))
                    && world.getBlock((int)Math.floor(candidate.x),(int)Math.floor(position.y-.1f),(int)Math.floor(candidate.z)) != Blocks.AIR
                    && world.getBlock((int)Math.floor(candidate.x),(int)Math.floor(position.y-.1f),(int)Math.floor(candidate.z)) != Blocks.WATER) {
                driving = false;
                player.driveSeat(candidate, player.yaw());
                return true;
            }
        }
        return false;
    }
    private boolean supported(World world) {
        return collides(world, new Vector3f(position).add(0,-.08f,0));
    }
    public void mouse(World world, float degrees) {
        if (driving) turn(world, Math.max(-12, Math.min(12, degrees)));
    }
    private void turn(World world, float degrees) {
        int steps = Math.max(1, (int)Math.ceil(Math.abs(degrees)));
        for (int i=0; i<steps; i++) {
            float before = yaw;
            yaw = (yaw + degrees/steps) % 360;
            if (collides(world, position)) { yaw = before; break; }
        }
    }
    public void step(World world, float dt, float throttle, float steer, boolean boost) {
        dt = Math.max(0, Math.min(dt, .1f));
        int steps = Math.max(1, (int)Math.ceil(dt/.008f));
        float h = dt/steps;
        for (int i=0; i<steps; i++) {
            float target = driving ? throttle*(boost ? 16 : 8) : 0;
            float change = (boost ? 12 : 6)*h;
            speed += Math.max(-change, Math.min(change, target-speed));
            if (driving) turn(world, steer*75*h*Math.min(1, Math.abs(speed)/2)*(speed < 0 ? -1 : 1));
            double a = Math.toRadians(yaw);
            Vector3f next = new Vector3f(position).add((float)Math.cos(a)*speed*h, 0, (float)Math.sin(a)*speed*h);
            if (!collides(world, next)) {
                wheelPhase += speed*h/.4f;
                position.set(next);
            } else if (supported(world) && !collides(world, new Vector3f(position).add(0,1.01f,0))
                    && !collides(world, new Vector3f(next).add(0,1.01f,0))) {
                position.set(next).add(0,1.01f,0);
                fallSpeed = 0;
            } else speed = 0;
            fallSpeed = Math.max(-30, fallSpeed-24*h);
            next.set(position).add(0, fallSpeed*h, 0);
            if (!collides(world, next)) position.set(next);
            else fallSpeed = 0;
        }
    }
    public boolean collides(World world, Vector3f p) {
        // Conservative rotated footprint prevents the body/wheels clipping corners.
        double a = Math.toRadians(yaw);
        float rx = (float)(Math.abs(Math.cos(a))*1.9 + Math.abs(Math.sin(a))*1.1);
        float rz = (float)(Math.abs(Math.sin(a))*1.9 + Math.abs(Math.cos(a))*1.1);
        for (int x=(int)Math.floor(p.x-rx); x<=(int)Math.floor(p.x+rx-.001f); x++)
            for (int y=(int)Math.floor(p.y); y<=(int)Math.floor(p.y+2.5f-.001f); y++)
                for (int z=(int)Math.floor(p.z-rz); z<=(int)Math.floor(p.z+rz-.001f); z++) {
                    if (!world.isLoaded(x,y,z)) return true;
                    int type = world.getBlock(x,y,z);
                    if (type == Blocks.AIR) continue;
                    if (type == Blocks.PARTIAL) {
                        if (world.cell(x,y,z).intersects(p.x-rx-x,p.y-y,p.z-rz-z,
                                p.x+rx-x,p.y+2.5f-y,p.z+rz-z)) return true;
                    } else return true;
                }
        return false;
    }
    public static Jeep spawn(World world, Vector3f player) {
        for (int radius=4; radius<=16; radius+=2)
            for (int dx=-radius; dx<=radius; dx+=2)
                for (int dz=-radius; dz<=radius; dz+=2) {
                    if (Math.abs(dx)!=radius && Math.abs(dz)!=radius) continue;
                    float x=player.x+dx, z=player.z+dz;
                    float y=world.terrain().column((int)Math.floor(x),(int)Math.floor(z)).height()+1.02f;
                    Jeep jeep = new Jeep(new Vector3f(x,y,z),-90);
                    if (!jeep.collides(world,jeep.position)) return jeep;
                }
        // Dense trees may leave no ground-level footprint. Find clear air above
        // a nearby loaded column and let gravity settle the vehicle onto support.
        for (int rise=1; rise<=24; rise++) {
            Jeep jeep = new Jeep(new Vector3f(player).add(4,rise,0),-90);
            if (!jeep.collides(world,jeep.position)) return jeep;
        }
        throw new IllegalStateException("No clear loaded space for the jeep near spawn");
    }
    public void save(Path file) throws IOException {
        Path temp = file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temp, position.x+" "+position.y+" "+position.z+" "+yaw+"\n");
        Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
    }
    public static Jeep load(Path file, World world, Vector3f player) throws Exception {
        if (Files.exists(file)) {
            try {
                String[] values = Files.readString(file).trim().split("\\s+");
                if (values.length == 4) {
                    float x=Float.parseFloat(values[0]),y=Float.parseFloat(values[1]),z=Float.parseFloat(values[2]),a=Float.parseFloat(values[3]);
                    if (Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z)&&Float.isFinite(a)) {
                        if (!world.isLoaded((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z)))
                            world.stream(x,z,9);
                        Jeep jeep = new Jeep(new Vector3f(x,y,z),a);
                        if (!jeep.collides(world,jeep.position)) return jeep;
                    }
                }
            } catch (IllegalArgumentException ignored) { /* Recover a malformed vehicle save. */ }
        }
        return spawn(world,player);
    }
}
