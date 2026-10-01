package dev.jayms.net;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Headless shared-world server. State and broadcasts are ordered under one lock. */
public final class MultiplayerServer implements AutoCloseable {
    private final ServerSocket listener;
    private final Map<Integer, Peer> peers = new HashMap<>();
    private final Map<String, Protocol.Edit> edits = new LinkedHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final Path save;
    private volatile boolean running = true;
    private static String key(Protocol.Edit e) { return e.x() + "," + e.y() + "," + e.z(); }
    public MultiplayerServer(String bind, int port, Path save) throws IOException {
        this.save = save;
        if (save != null && Files.exists(save)) {
            try (DataInputStream in = new DataInputStream(Files.newInputStream(save))) {
                if (in.readInt() != Protocol.MAGIC) throw new IOException("Invalid world save");
                int count = in.readInt();
                if (count < 0 || count > 2000000) throw new IOException("Invalid edit count");
                for (int i = 0; i < count; i++) { var e = Protocol.Edit.read(in); if (!e.valid()) throw new IOException("Invalid saved block"); edits.put(key(e), e); }
            }
        }
        listener = new ServerSocket(); listener.bind(new InetSocketAddress(bind, port));
    }
    public int port() { return listener.getLocalPort(); }
    public void run() throws IOException {
        while (running) {
            try { Socket socket = listener.accept(); socket.setTcpNoDelay(true); socket.setSoTimeout(15000); workers.submit(() -> handle(socket)); }
            catch (SocketException e) { if (running) throw e; }
        }
    }
    private void handle(Socket socket) {
        Peer peer = null;
        try (socket) {
            var in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            if (in.readInt() != Protocol.MAGIC || in.readInt() != Protocol.VERSION) throw new IOException("Protocol mismatch");
            synchronized (this) {
                if (!running || peers.size() >= 32) throw new IOException("Server full or stopping");
                int id = ids.incrementAndGet(); peer = new Peer(socket, out, new Protocol.Pose(id, 8, Protocol.spawnY(), 24, -90, 0));
                out.writeInt(Protocol.MAGIC); out.writeInt(Protocol.VERSION); out.writeInt(id);
                peer.pose.write(out); out.writeInt(edits.size()); for (var e : edits.values()) e.write(out);
                out.writeInt(peers.size()); for (var p : peers.values()) p.pose.write(out); out.flush();
            }
            // Client confirms its snapshot before joining live broadcasts.
            if (in.readUnsignedByte() != Protocol.READY) throw new IOException("Expected ready");
            synchronized (this) {
                out.writeByte(Protocol.READY);
                for (var e : edits.values()) { out.writeByte(Protocol.BLOCK); e.write(out); }
                for (var p : peers.values()) { out.writeByte(Protocol.MOVE); p.pose.write(out); }
                out.flush(); peers.put(peer.pose.id(), peer); broadcast(Protocol.MOVE, peer.pose, null);
            }
            long second = System.nanoTime(); int messages = 0;
            while (running) {
                int type = in.readUnsignedByte();
                if (System.nanoTime() - second > 1000000000L) { second = System.nanoTime(); messages = 0; }
                if (++messages > 120) throw new IOException("Message rate exceeded");
                Protocol.Pose incomingPose = type == Protocol.MOVE ? Protocol.Pose.read(in) : null;
                Protocol.Edit incomingEdit = type == Protocol.BLOCK ? Protocol.Edit.read(in) : null;
                synchronized (this) {
                    if (type == Protocol.MOVE) {
                        var p = incomingPose;
                        if (!p.valid() || p.id() != peer.pose.id()) throw new IOException("Invalid movement");
                        peer.pose = p; broadcast(type, p, null);
                    } else if (type == Protocol.BLOCK) {
                        var e = incomingEdit;
                        var p = peer.pose;
                        double distance = Math.pow(e.x() + .5 - p.x(), 2) + Math.pow(e.y() + .5 - p.y() - 1.6, 2) + Math.pow(e.z() + .5 - p.z(), 2);
                        if (!e.valid() || distance > 49) continue;
                        if (e.type() != 0 && peers.values().stream().anyMatch(other -> overlaps(other.pose, e))) continue;
                        edits.put(key(e), e); broadcast(type, null, e);
                    } else throw new IOException("Unknown message");
                }
            }
        } catch (IOException e) { if (running) System.out.println("Connection closed: " + e.getMessage()); }
        finally { synchronized (this) { if (peer != null && peers.remove(peer.pose.id()) != null) broadcast(Protocol.LEAVE, peer.pose, null); } }
    }
    private static boolean overlaps(Protocol.Pose p, Protocol.Edit e) {
        return p.x() + .3 > e.x() && p.x() - .3 < e.x() + 1 && p.y() + 1.8 > e.y() && p.y() < e.y() + 1 && p.z() + .3 > e.z() && p.z() - .3 < e.z() + 1;
    }
    private void broadcast(int type, Protocol.Pose pose, Protocol.Edit edit) {
        for (var p : peers.values()) {
            if (!p.queue.offer(new Event(type, pose, edit))) p.disconnect();
        }
    }
    private record Event(int type, Protocol.Pose pose, Protocol.Edit edit) {}
    private final class Peer {
        final Socket socket; final DataOutputStream out; Protocol.Pose pose;
        final BlockingQueue<Event> queue = new ArrayBlockingQueue<>(512);
        Peer(Socket socket, DataOutputStream out, Protocol.Pose pose) {
            this.socket = socket; this.out = out; this.pose = pose;
            workers.submit(() -> {
                try {
                    while (!socket.isClosed()) {
                        Event e = queue.poll(1, TimeUnit.SECONDS); if (e == null) continue;
                        out.writeByte(e.type);
                        if (e.type == Protocol.MOVE) e.pose.write(out); else if (e.type == Protocol.BLOCK) e.edit.write(out); else out.writeInt(e.pose.id());
                        out.flush();
                    }
                } catch (IOException | InterruptedException e) { disconnect(); }
            });
        }
        void disconnect() { try { socket.close(); } catch (IOException ignored) {} }
    }
    @Override public synchronized void close() throws IOException {
        if (!running) return;
        running = false; listener.close(); for (var p : peers.values()) p.disconnect(); workers.shutdownNow();
        if (save != null) {
            Path absolute = save.toAbsolutePath(); Files.createDirectories(absolute.getParent());
            Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
            try (var out = new DataOutputStream(Files.newOutputStream(temp))) { out.writeInt(Protocol.MAGIC); out.writeInt(edits.size()); for (var e : edits.values()) e.write(out); }
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    public static void main(String[] args) throws Exception {
        String bind = "0.0.0.0"; int port = Protocol.PORT; Path save = Path.of("world.dat");
        for (int i = 0; i < args.length; i++) switch (args[i]) {
            case "--bind" -> bind = args[++i]; case "--port" -> port = Integer.parseInt(args[++i]); case "--world" -> save = Path.of(args[++i]);
            default -> throw new IllegalArgumentException("Usage: --bind ADDRESS --port PORT --world FILE");
        }
        try (var server = new MultiplayerServer(bind, port, save)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.close(); } catch (IOException e) { e.printStackTrace(); } }));
            System.out.println("Voxel One server listening on " + bind + ":" + server.port()); server.run();
        }
    }
}
