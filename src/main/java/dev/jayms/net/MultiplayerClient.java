package dev.jayms.net;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Network reader only queues events; the render thread owns world and GPU state. */
public final class MultiplayerClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private final DataInputStream in;
    private final DataOutputStream out;
    public final int id;
    public final Protocol.Pose spawn;
    public final List<Protocol.Edit> initialEdits = new ArrayList<>();
    public final Map<Integer, Protocol.Pose> players = new HashMap<>();
    private final BlockingQueue<Runnable> events = new ArrayBlockingQueue<>(4096);
    private final BlockingQueue<Runnable> outgoing = new ArrayBlockingQueue<>(128);
    private volatile boolean connected = true;
    private volatile String error;
    private final List<Protocol.Edit> pendingEdits = new ArrayList<>();
    public MultiplayerClient(String host, int port) throws IOException {
        socket.connect(new InetSocketAddress(host, port), 5000); socket.setTcpNoDelay(true); socket.setSoTimeout(15000);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream())); out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        try {
            out.writeInt(Protocol.MAGIC); out.writeInt(Protocol.VERSION); out.flush();
            if (in.readInt() != Protocol.MAGIC || in.readInt() != Protocol.VERSION) throw new IOException("Server protocol mismatch");
            id = in.readInt(); spawn = Protocol.Pose.read(in);
            int count = in.readInt(); if (count < 0 || count > 2000000) throw new IOException("Invalid world snapshot");
            for (int i = 0; i < count; i++) initialEdits.add(Protocol.Edit.read(in));
            count = in.readInt(); if (count < 0 || count > 32) throw new IOException("Invalid player snapshot");
            for (int i = 0; i < count; i++) { var p = Protocol.Pose.read(in); players.put(p.id(), p); }
            out.writeByte(Protocol.READY); out.flush(); socket.setSoTimeout(0);
        } catch (IOException e) { socket.close(); throw e; }
        Thread reader = new Thread(this::read, "voxel-network-reader"); reader.setDaemon(true); reader.start();
        Thread writer = new Thread(() -> {
            try { while (connected) { Runnable task = outgoing.poll(1, TimeUnit.SECONDS); if (task != null) task.run(); } }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }, "voxel-network-writer"); writer.setDaemon(true); writer.start();
    }
    private void read() {
        try {
            while (connected) {
                int type = in.readUnsignedByte(); Runnable event;
                if (type == Protocol.MOVE) { var p = Protocol.Pose.read(in); event = () -> { if (p.id() != id) players.put(p.id(), p); }; }
                else if (type == Protocol.BLOCK) { var e = Protocol.Edit.read(in); event = () -> pendingEdits.add(e); }
                else if (type == Protocol.READY) { event = () -> players.clear(); }
                else if (type == Protocol.LEAVE) { int leaving = in.readInt(); event = () -> players.remove(leaving); }
                else throw new IOException("Unknown server message");
                if (!events.offer(event)) throw new IOException("Incoming queue full");
            }
        } catch (IOException e) { if (connected) error = "Disconnected: " + e.getMessage(); close(); }
    }
    public List<Protocol.Edit> poll() {
        Runnable event; while ((event = events.poll()) != null) event.run();
        var result = new ArrayList<>(pendingEdits); pendingEdits.clear(); return result;
    }
    public String status() { return connected ? "Connected | " + (players.size() + 1) + " players" : error == null ? "Disconnected" : error; }
    public void move(Protocol.Pose p) { send(() -> { out.writeByte(Protocol.MOVE); p.write(out); out.flush(); }); }
    public void edit(Protocol.Edit e) { send(() -> { out.writeByte(Protocol.BLOCK); e.write(out); out.flush(); }); }
    private interface Write { void run() throws IOException; }
    private void send(Write write) {
        if (!connected) return;
        if (!outgoing.offer(() -> { try { write.run(); } catch (IOException e) { error = "Disconnected: " + e.getMessage(); close(); } })) { error = "Disconnected: outgoing queue full"; close(); }
    }
    @Override public void close() { connected = false; try { socket.close(); } catch (IOException ignored) {} }
}
