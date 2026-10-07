package dev.jayms.audio;

import dev.jayms.net.city.*;
import dev.jayms.player.Jeep;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;
import java.util.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

/** Client-only vehicle audio. Missing output hardware never prevents play. */
public final class VehicleAudio implements AutoCloseable {
    private long device, context;
    private int engineBuffer, jetBuffer, trainBuffer, engine;
    private final Map<Integer, Integer> trains = new HashMap<>();
    private final Map<Integer, Float> phases = new HashMap<>();
    private final Set<Integer> moving = new HashSet<>();
    private double lastElapsed = Double.NaN;
    private final Map<Integer, Integer> jets = new HashMap<>();
    private boolean available;

    public VehicleAudio() { this(false); }

    // Native loopback is used by the integration harness to inspect rendered PCM.
    private VehicleAudio(boolean loopback) {
        try {
            device = loopback ? SOFTLoopback.alcLoopbackOpenDeviceSOFT((java.nio.ByteBuffer) null)
                    : alcOpenDevice((java.nio.ByteBuffer) null);
            if (device == 0) return;
            context = loopback ? alcCreateContext(device, new int[]{ALC_FREQUENCY, 22050,
                    SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT, SOFTLoopback.ALC_STEREO_SOFT,
                    SOFTLoopback.ALC_FORMAT_TYPE_SOFT, SOFTLoopback.ALC_SHORT_SOFT, 0})
                    : alcCreateContext(device, (java.nio.IntBuffer) null);
            if (context == 0 || !alcMakeContextCurrent(context)) { close(); return; }
            AL.createCapabilities(ALC.createCapabilities(device));
            engineBuffer = sound(0);
            jetBuffer = sound(1);
            trainBuffer = sound(2);
            engine = source(engineBuffer);
            available = true;
        } catch (RuntimeException | LinkageError unavailable) { close(); }
    }

    private static int sound(int kind) {
        int rate = 22050;
        var samples = BufferUtils.createShortBuffer(rate);
        Random random = new Random(17);
        double noise = 0;
        for (int i = 0; i < rate; i++) {
            double t = i / (double) rate;
            noise = .85 * noise + .15 * (random.nextDouble() * 2 - 1);
            double value = kind == 2 ? (.25 * Math.sin(2 * Math.PI * 45 * t)
                    + noise * .8 * Math.pow(.5 + .5 * Math.cos(2 * Math.PI * 4 * t), 6))
                    : kind == 1 ? noise * .8 + .12 * Math.sin(2 * Math.PI * 440 * t)
                    : .35 * Math.sin(2 * Math.PI * 60 * t) + .2 * Math.sin(2 * Math.PI * 120 * t)
                    + .1 * Math.sin(2 * Math.PI * 180 * t) + .15 * noise;
            samples.put((short) (value * 24000));
        }
        samples.flip();
        int buffer = alGenBuffers();
        alBufferData(buffer, AL_FORMAT_MONO16, samples, rate);
        return buffer;
    }

    private static int source(int buffer) {
        int source = alGenSources();
        alSourcei(source, AL_BUFFER, buffer);
        alSourcei(source, AL_LOOPING, AL_TRUE);
        alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE);
        alSourcef(source, AL_ROLLOFF_FACTOR, 0);
        return source;
    }

    /** A bounded takeoff window avoids replaying a jet throughout cruise or landing. */
    public static boolean takingOff(Aviation.Flight flight) {
        return flight.stage() == 2 && flight.clock() < 6;
    }

    public void update(Jeep car, CityFrame city, Vector3f listener, boolean muted) {
        if (!available) return;
        if (!muted && car != null && car.driving()) {
            alSourcef(engine, AL_GAIN, .28f);
            alSourcef(engine, AL_PITCH, .7f + Math.min(1.5f, Math.abs(car.speed()) / 16));
            if (alGetSourcei(engine, AL_SOURCE_STATE) != AL_PLAYING) alSourcePlay(engine);
        } else alSourceStop(engine);
        Set<Integer> active = new HashSet<>();
        if (!muted) for (var plane : Aviation.planes(city)) {
            var flight = city.aviation().flights().stream().filter(f -> f.id() == plane.id()).findFirst().orElse(null);
            if (flight == null || !takingOff(flight)) continue;
            float distance = listener.distance(plane.x(), plane.y(), plane.z());
            if (distance >= 160) continue;
            active.add(plane.id());
            int src = jets.computeIfAbsent(plane.id(), id -> source(jetBuffer));
            alSourcef(src, AL_GAIN, .45f * (1 - distance / 160));
            alSourcef(src, AL_PITCH, .8f + (float) flight.clock() / 12);
            if (alGetSourcei(src, AL_SOURCE_STATE) != AL_PLAYING) alSourcePlay(src);
        }
        if (city.elapsed() != lastElapsed) {
            if (city.elapsed() < lastElapsed) phases.clear();
            moving.clear();
            Set<Integer> seen = new HashSet<>();
            for (var train : city.railway().trains()) {
                seen.add(train.id());
                Float previous = phases.put(train.id(), train.phase());
                if (previous != null && train.phase() > previous && train.dwell() == 0)
                    moving.add(train.id());
            }
            phases.keySet().retainAll(seen);
            lastElapsed = city.elapsed();
        }
        Set<Integer> activeTrains = new HashSet<>();
        if (!muted) for (var train : city.railway().trains()) {
            if (!moving.contains(train.id()) || train.dwell() > 0) continue;
            float distance = listener.distance(train.x(), train.y(), train.z());
            if (distance >= 160) continue;
            activeTrains.add(train.id());
            int src = trains.computeIfAbsent(train.id(), id -> source(trainBuffer));
            alSourcef(src, AL_GAIN, .4f * (1 - distance / 160));
            if (alGetSourcei(src, AL_SOURCE_STATE) != AL_PLAYING) alSourcePlay(src);
        }
        trains.entrySet().removeIf(entry -> {
            if (activeTrains.contains(entry.getKey())) return false;
            alSourceStop(entry.getValue()); alDeleteSources(entry.getValue()); return true;
        });
        jets.entrySet().removeIf(entry -> {
            if (active.contains(entry.getKey())) return false;
            alSourceStop(entry.getValue()); alDeleteSources(entry.getValue()); return true;
        });
    }

    public boolean available() { return available; }
    public int engineState() { return available ? alGetSourcei(engine, AL_SOURCE_STATE) : 0; }
    public int trainSources() { return trains.size(); }
    public float enginePitch() { return available ? alGetSourcef(engine, AL_PITCH) : 0; }
    public int takeoffSources() { return jets.size(); }

    @Override public void close() {
        available = false;
        if (context != 0) {
            for (int source : trains.values()) alDeleteSources(source);
            trains.clear();
            phases.clear(); moving.clear();
            for (int source : jets.values()) alDeleteSources(source);
            jets.clear();
            if (engine != 0) alDeleteSources(engine);
            if (engineBuffer != 0) alDeleteBuffers(engineBuffer);
            if (jetBuffer != 0) alDeleteBuffers(jetBuffer);
            if (trainBuffer != 0) alDeleteBuffers(trainBuffer);
            alcMakeContextCurrent(0); alcDestroyContext(context); context = 0;
        }
        if (device != 0) { alcCloseDevice(device); device = 0; }
        engine = engineBuffer = jetBuffer = trainBuffer = 0;
    }
}
