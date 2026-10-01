package dev.jayms.net;

import java.util.ArrayDeque;

/** Render 100 ms behind received state, interpolating positions, yaw and walk cycle. */
public final class RemotePlayer {
    private record Sample(Protocol.Pose pose, long time) {}

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    public final String name;

    public RemotePlayer(String name) {
        this.name = name;
    }

    public void accept(Protocol.Pose pose, long received) {
        samples.addLast(new Sample(pose, received));
        while (samples.size() > 32) samples.removeFirst();
    }

    public Protocol.Pose sample(long now) {
        long target = now - 100_000_000L;
        while (samples.size() > 2
                && samples.stream().skip(1).findFirst().orElseThrow().time <= target)
            samples.removeFirst();
        Sample first = samples.peekFirst();
        if (first == null) return null;
        Sample second = samples.stream().skip(1).findFirst().orElse(first);
        if (first == second || second.time <= first.time) return first.pose;
        float t =
                Math.max(
                        0, Math.min(1, (float) (target - first.time) / (second.time - first.time)));
        var a = first.pose;
        var b = second.pose;
        return new Protocol.Pose(
                b.id(),
                lerp(a.x(), b.x(), t),
                lerp(a.y(), b.y(), t),
                lerp(a.z(), b.z(), t),
                a.yaw() + shortestAngle(a.yaw(), b.yaw()) * t,
                lerp(a.pitch(), b.pitch(), t),
                lerp(a.walkPhase(), b.walkPhase(), t),
                lerp(a.walkAmount(), b.walkAmount(), t),
                b.flying());
    }

    public static float shortestAngle(float a, float b) {
        return ((b - a) % 360 + 540) % 360 - 180;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
