package dev.jayms.net;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;

/** Authenticated headless server; world mutations and queued broadcasts have one total order. */
public final class MultiplayerServer implements AutoCloseable {
    private final ServerSocket listener;
    private final AccountStore accounts;
    private final Map<Integer, Peer> peers = new HashMap<>();
    private final Set<String> sessions = new HashSet<>();
    private final Map<String, Protocol.Edit> edits = new LinkedHashMap<>();
    private final Map<String, AttemptWindow> attempts = new HashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final Semaphore connections = new Semaphore(64), hashing = new Semaphore(4);
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ScheduledExecutorService saves = Executors.newSingleThreadScheduledExecutor();
    private final Path save;
    private volatile boolean running = true;

    private record AttemptWindow(long start, int count) {}

    public MultiplayerServer(
            String bind, int port, Path save, AccountStore accounts, SSLContext tls)
            throws IOException {
        this.save = save;
        this.accounts = accounts;
        if (save != null && Files.exists(save)) {
            try (var in = new DataInputStream(Files.newInputStream(save))) {
                if (in.readInt() != Protocol.MAGIC) throw new IOException("Invalid world save");
                int count = in.readInt();
                if (count < 0 || count > 2000000) throw new IOException("Invalid edit count");
                for (int i = 0; i < count; i++) {
                    var e = Protocol.Edit.read(in);
                    if (!e.valid()) throw new IOException("Invalid saved block");
                    edits.put(e.key(), e);
                }
            }
        }
        listener = tls.getServerSocketFactory().createServerSocket();
        ((SSLServerSocket) listener).setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        listener.bind(new InetSocketAddress(bind, port));
        saves.scheduleAtFixedRate(
                () -> {
                    try {
                        persist();
                    } catch (IOException e) {
                        System.err.println("World save failed: " + e.getMessage());
                    }
                },
                60,
                60,
                TimeUnit.SECONDS);
    }

    public int port() {
        return listener.getLocalPort();
    }

    public void run() throws IOException {
        while (running) {
            try {
                Socket socket = listener.accept();
                if (!connections.tryAcquire()) {
                    socket.close();
                    continue;
                }
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(15000);
                workers.submit(
                        () -> {
                            try {
                                handle(socket);
                            } finally {
                                connections.release();
                            }
                        });
            } catch (SocketException e) {
                if (running) throw e;
            } catch (RejectedExecutionException e) {
                if (running) throw e;
            }
        }
    }

    private synchronized boolean allowAttempt(String address) {
        long now = System.nanoTime();
        attempts.entrySet().removeIf(e -> now - e.getValue().start > 300_000_000_000L);
        if (attempts.size() >= 4096 && !attempts.containsKey(address)) return false;
        AttemptWindow window = attempts.getOrDefault(address, new AttemptWindow(now, 0));
        attempts.put(address, new AttemptWindow(window.start, window.count + 1));
        return window.count < 20;
    }

    private void authReply(DataOutputStream out, boolean success, String message)
            throws IOException {
        out.writeInt(Protocol.MAGIC);
        out.writeInt(Protocol.VERSION);
        out.writeBoolean(success);
        out.writeUTF(message);
        out.flush();
    }

    private void handle(Socket socket) {
        Peer peer = null;
        String username = null;
        boolean reserved = false;
        try (socket) {
            var in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            if (in.readInt() != Protocol.MAGIC || in.readInt() != Protocol.VERSION) {
                authReply(out, false, "Client version mismatch; download the latest client.");
                return;
            }
            if (!allowAttempt(socket.getInetAddress().getHostAddress()) || !hashing.tryAcquire()) {
                authReply(out, false, "Too many login attempts. Try again later.");
                return;
            }
            boolean authenticated;
            try {
                int action = in.readUnsignedByte();
                username = Protocol.readText(in, 16);
                char[] password = Protocol.readText(in, 128).toCharArray();
                try {
                    authenticated =
                            action == Protocol.REGISTER
                                    ? accounts.register(username, password)
                                    : action == Protocol.LOGIN
                                            && accounts.authenticate(username, password);
                } catch (IOException e) {
                    authReply(out, false, e.getMessage());
                    return;
                } finally {
                    Arrays.fill(password, '\0');
                }
            } finally {
                hashing.release();
            }
            if (!authenticated) {
                authReply(out, false, "Login failed or username unavailable.");
                return;
            }
            username = AccountStore.normalize(username);
            Map<String, Protocol.Edit> snapshot;
            List<Peer> initialPlayers;
            synchronized (this) {
                if (!running || sessions.size() >= 32) {
                    authReply(out, false, "Server full or stopping.");
                    return;
                }
                if (!sessions.add(username)) {
                    authReply(out, false, "This account is already playing.");
                    return;
                }
                reserved = true;
                peer = new Peer(socket, out, username, safeSpawn(ids.incrementAndGet()));
                snapshot = new LinkedHashMap<>(edits);
                initialPlayers = new ArrayList<>(peers.values());
            }
            authReply(out, true, "Welcome " + username);
            out.writeInt(peer.pose.id());
            peer.pose.write(out);
            out.writeUTF(username);
            out.writeInt(snapshot.size());
            for (var e : snapshot.values()) e.write(out);
            out.writeInt(initialPlayers.size());
            for (var p : initialPlayers) {
                p.pose.write(out);
                out.writeUTF(p.name);
            }
            out.flush();
            if (in.readUnsignedByte() != Protocol.READY) throw new IOException("Expected ready");
            synchronized (this) {
                peer.enqueue(new Event(Protocol.READY, null, null, null, 0, false));
                for (var e : edits.values())
                    if (!e.equals(snapshot.get(e.key())))
                        peer.enqueue(new Event(Protocol.BLOCK, null, e, null, 0, true));
                for (var p : peers.values())
                    peer.enqueue(new Event(Protocol.JOIN, p.pose, null, p.name, 0, true));
                peers.put(peer.pose.id(), peer);
                broadcast(new Event(Protocol.JOIN, peer.pose, null, peer.name, 0, true));
                peer.startWriter();
            }
            long second = System.nanoTime();
            int messages = 0;
            while (running) {
                int type = in.readUnsignedByte();
                if (System.nanoTime() - second > 1000000000L) {
                    second = System.nanoTime();
                    messages = 0;
                }
                if (++messages > 120) throw new IOException("Message rate exceeded");
                Protocol.Pose pose = type == Protocol.MOVE ? Protocol.Pose.read(in) : null;
                Protocol.BlockRequest request =
                        type == Protocol.BLOCK ? Protocol.BlockRequest.read(in) : null;
                synchronized (this) {
                    if (type == Protocol.MOVE) {
                        checkPose(peer, pose);
                        peer.pose = pose;
                        broadcast(new Event(type, pose, null, null, 0, true));
                    } else if (type == Protocol.BLOCK) {
                        // The edit carries the exact pose when clicked, ordered ahead of this
                        // mutation.
                        checkPose(peer, request.pose());
                        peer.pose = request.pose();
                        var e = request.edit();
                        var p = peer.pose;
                        int current = e.valid() ? block(e.x(), e.y(), e.z()) : 0;
                        double distance =
                                Math.pow(e.x() + .5 - p.x(), 2)
                                        + Math.pow(e.y() + .5 - p.y() - 1.6, 2)
                                        + Math.pow(e.z() + .5 - p.z(), 2);
                        boolean accepted =
                                e.valid()
                                        && distance <= 49
                                        && (e.type() == 0 || current == 0)
                                        && (e.type() == 0
                                                || peers.values().stream()
                                                        .noneMatch(
                                                                other -> overlaps(other.pose, e)));
                        if (accepted) {
                            edits.put(e.key(), e);
                            broadcast(new Event(Protocol.BLOCK, null, e, null, 0, true));
                            broadcast(new Event(Protocol.MOVE, peer.pose, null, null, 0, true));
                        }
                        peer.enqueue(
                                new Event(
                                        Protocol.EDIT_RESULT,
                                        null,
                                        new Protocol.Edit(
                                                e.x(), e.y(), e.z(), accepted ? e.type() : current),
                                        null,
                                        request.requestId(),
                                        accepted));
                    } else throw new IOException("Unknown message");
                }
            }
        } catch (IOException e) {
            if (running && !(e instanceof EOFException))
                System.out.println("Connection closed: " + e.getMessage());
        } finally {
            synchronized (this) {
                if (reserved) sessions.remove(username);
                if (peer != null && peers.remove(peer.pose.id()) != null)
                    broadcast(new Event(Protocol.LEAVE, peer.pose, null, null, 0, true));
            }
        }
    }

    private void checkPose(Peer peer, Protocol.Pose pose) throws IOException {
        if (!pose.valid() || pose.id() != peer.pose.id()) throw new IOException("Invalid movement");
    }

    private int block(int x, int y, int z) {
        var edit = edits.get(x + "," + y + "," + z);
        return edit == null ? Protocol.terrain(x, y, z) : edit.type();
    }

    private Protocol.Pose safeSpawn(int id) throws IOException {
        for (int x = 8; x < 16; x++)
            for (int z = 24; z < 32; z++) {
                int surface = -32;
                for (int y = 47; y >= -32; y--)
                    if (block(x, y, z) != 0) {
                        surface = y;
                        break;
                    }
                if (surface <= 45)
                    return new Protocol.Pose(id, x + .5f, surface + 1.01f, z + .5f, -90, 0);
            }
        throw new IOException("Spawn area is blocked; clear space before joining.");
    }

    private static boolean overlaps(Protocol.Pose p, Protocol.Edit e) {
        return p.x() + .3 > e.x()
                && p.x() - .3 < e.x() + 1
                && p.y() + 1.8 > e.y()
                && p.y() < e.y() + 1
                && p.z() + .3 > e.z()
                && p.z() - .3 < e.z() + 1;
    }

    private void broadcast(Event event) {
        for (var p : peers.values()) p.enqueue(event);
    }

    private record Event(
            int type,
            Protocol.Pose pose,
            Protocol.Edit edit,
            String name,
            int request,
            boolean accepted) {}

    private final class Peer {
        final Socket socket;
        final DataOutputStream out;
        final String name;
        volatile Protocol.Pose pose;
        final BlockingQueue<Event> queue = new ArrayBlockingQueue<>(512);

        Peer(Socket socket, DataOutputStream out, String name, Protocol.Pose pose) {
            this.socket = socket;
            this.out = out;
            this.name = name;
            this.pose = pose;
        }

        void enqueue(Event e) {
            if (!queue.offer(e)) disconnect();
        }

        void startWriter() {
            workers.submit(
                    () -> {
                        try {
                            while (!socket.isClosed()) {
                                Event e = queue.poll(1, TimeUnit.SECONDS);
                                if (e == null) continue;
                                out.writeByte(e.type);
                                switch (e.type) {
                                    case Protocol.MOVE -> e.pose.write(out);
                                    case Protocol.BLOCK -> e.edit.write(out);
                                    case Protocol.JOIN -> {
                                        e.pose.write(out);
                                        out.writeUTF(e.name);
                                    }
                                    case Protocol.LEAVE -> out.writeInt(e.pose.id());
                                    case Protocol.EDIT_RESULT -> {
                                        out.writeInt(e.request);
                                        out.writeBoolean(e.accepted);
                                        e.edit.write(out);
                                    }
                                    case Protocol.READY -> {}
                                    default -> throw new IOException("Unknown queued event");
                                }
                                out.flush();
                            }
                        } catch (IOException | InterruptedException e) {
                            disconnect();
                        }
                    });
        }

        void disconnect() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private synchronized void persist() throws IOException {
        if (save == null) return;
        Path absolute = save.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try (var out = new DataOutputStream(Files.newOutputStream(temp))) {
            out.writeInt(Protocol.MAGIC);
            out.writeInt(edits.size());
            for (var e : edits.values()) e.write(out);
        }
        try {
            Files.move(
                    temp,
                    absolute,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!running) return;
        running = false;
        listener.close();
        for (var p : peers.values()) p.disconnect();
        workers.shutdownNow();
        saves.shutdownNow();
        persist();
    }

    public static void main(String[] args) throws Exception {
        String bind = "0.0.0.0", create = null;
        int port = Protocol.PORT;
        Path save = Path.of("world.dat"),
                accountFile = Path.of("accounts.db"),
                tlsDirectory = Path.of("tls");
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--bind" -> bind = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--world" -> save = Path.of(args[++i]);
                case "--accounts" -> accountFile = Path.of(args[++i]);
                case "--tls-dir" -> tlsDirectory = Path.of(args[++i]);
                case "--create-account" -> create = args[++i];
                default ->
                        throw new IllegalArgumentException(
                                "Usage: --bind ADDRESS --port PORT --world FILE --accounts FILE"
                                    + " --tls-dir DIRECTORY --create-account NAME");
            }
        AccountStore accounts = new AccountStore(accountFile);
        if (create != null) {
            byte[] bytes = new byte[15];
            new SecureRandom().nextBytes(bytes);
            char[] password =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toCharArray();
            try {
                if (!accounts.register(create, password))
                    throw new IOException("Account already exists");
                System.out.println("Account: " + create + "\nPassword: " + new String(password));
            } finally {
                Arrays.fill(password, '\0');
            }
            return;
        }
        var identity = SecureTransport.server(tlsDirectory);
        try (var server = new MultiplayerServer(bind, port, save, accounts, identity.context())) {
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        try {
                                            server.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }));
            System.out.println("Voxel One TLS server listening on " + bind + ":" + server.port());
            System.out.println("Certificate SHA-256: " + identity.fingerprint());
            server.run();
        }
    }
}
