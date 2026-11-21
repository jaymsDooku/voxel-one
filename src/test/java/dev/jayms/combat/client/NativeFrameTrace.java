package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.View;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.StringJoiner;

/** Buffered until the GL loop exits so filesystem latency does not enter frame pacing. */
final class NativeFrameTrace {
    private static final String FIELDS = "Id X Y Z Vx Vy Vz HurtTick MotionEpoch WallTick ImpulseTick ImpulseX ImpulseY ImpulseZ Health Hits Yaw Pitch SwingTick";
    private final StringBuilder csv = new StringBuilder();
    private StringJoiner row;
    private String remotePose, click = ",,", flags;
    private long renderNs;

    NativeFrameTrace() {
        var header = new StringJoiner(",");
        header.add("sampleNs,tick,phase");
        for (String prefix : new String[]{"local", "remote"})
            for (String field : FIELDS.split(" ")) header.add(prefix + field);
        header.add("localDisplayX,localDisplayY,localDisplayZ,localConfirmedX,localConfirmedY,localConfirmedZ");
        header.add("remoteDisplayX,remoteDisplayY,remoteDisplayZ,remoteTint,clickSequence,clickTarget,clickNs,localSwinging,remoteSwinging,remoteSwingProgress,renderNs");
        csv.append(header).append('\n');
    }

    void click(long now, Intent intent) {
        click = intent.sequence() + "," + (intent.attackView() == null ? -1 : intent.attackView().targetId()) + "," + now;
    }

    void sample(long now, View view, int local, int remote, double x, double y, double z, Vec3 confirmed) {
        finish();
        row = new StringJoiner(",");
        row.add(now + "," + view.tick() + "," + view.phase());
        Fighter a = actor(view, local), b = actor(view, remote);
        appendActor(a);
        appendActor(b);
        row.add(x + "," + y + "," + z + "," + confirmed.x() + "," + confirmed.y() + "," + confirmed.z());
        renderNs = 0;
        remotePose = ",,,";
        flags = click + "," + (a != null && a.swinging()) + "," + (b != null && b.swinging()) + ",";
        click = ",,";
    }

    void presented(float x, float y, float z, float tint, float swing) {
        remotePose = x + "," + y + "," + z + "," + tint;
        flags += swing;
        renderNs = System.nanoTime();
    }

    void rendered(long now) {
        if (renderNs == 0) renderNs = now;
    }

    private void finish() {
        if (row != null) csv.append(row.add(remotePose).add(flags).add(renderNs == 0 ? "" : Long.toString(renderNs))).append('\n');
    }

    void save(Path output) throws IOException {
        finish();
        Files.writeString(output.resolve("native-frames.csv"), csv);
    }

    static Fighter actor(View view, int id) {
        return view.fighters().stream().filter(f -> f.id() == id).findFirst().orElse(null);
    }

    private void appendActor(Fighter f) {
        if (f == null) {
            row.add(String.join(",", Collections.nCopies(FIELDS.split(" ").length, "")));
            return;
        }
        row.add(f.id() + "," + f.x() + "," + f.y() + "," + f.z() + "," + f.vx() + "," + f.vy() + "," + f.vz()
                + "," + f.hurtTick() + "," + f.motionEpoch() + "," + f.lastHorizontalWallTick() + "," + f.impulseTick()
                + "," + f.impulseX() + "," + f.impulseY() + "," + f.impulseZ() + "," + f.health() + "," + f.hits()
                + "," + f.yaw() + "," + f.pitch() + "," + f.swingTick());
    }
}
