package dev.jayms.combat.practice;

import dev.jayms.net.SecureTransport;
import dev.jayms.net.AccountStore;
import dev.jayms.combat.faction.FactionMatch;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** A dedicated, TLS-pinned practice lobby. Guests have ephemeral arena-only identities. */
public final class PracticeServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 25567;
    private final SSLServerSocket listener;
    private final ArenaMatch match;
    private final AccountStore accounts;
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final Semaphore capacity = new Semaphore(12);
    private final Semaphore hashing = new Semaphore(2);
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private final Set<Peer> peers = ConcurrentHashMap.newKeySet();

    private record Peer(int id, ArrayBlockingQueue<PracticeMatch.View> updates) { }

    public PracticeServer(String host, int port, SSLContext tls, PracticeMatch.Kit kit) throws IOException {
        this(host, port, tls, new PracticeMatch(kit, false), null);
    }

    public PracticeServer(String host, int port, SSLContext tls, ArenaMatch match, AccountStore accounts) throws IOException {
        this.match = match;
        this.accounts = accounts;
        listener = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket();
        try {
            listener.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            listener.bind(new InetSocketAddress(InetAddress.getByName(host), port));
        } catch (IOException | RuntimeException failure) {
            listener.close();
            clock.shutdownNow();
            workers.shutdownNow();
            throw failure;
        }
        clock.scheduleAtFixedRate(this::tick, 0, 50, TimeUnit.MILLISECONDS);
        workers.execute(this::accept);
    }

    public int port() { return listener.getLocalPort(); }

    private void accept() {
        while (!listener.isClosed()) {
            try {
                Socket socket = listener.accept();
                if (!capacity.tryAcquire()) {
                    socket.close();
                    continue;
                }
                connections.add(socket);
                try {
                    workers.execute(() -> handle(socket));
                } catch (java.util.concurrent.RejectedExecutionException shutdown) {
                    connections.remove(socket);
                    capacity.release();
                    socket.close();
                }
            } catch (IOException failure) {
                if (!listener.isClosed()) System.err.println("Practice server accept failed: " + failure.getMessage());
                return;
            }
        }
    }

    private void tick() {
        match.tick();
        for (Peer peer : peers) {
            var view = match.view(peer.id());
            peer.updates().poll();
            peer.updates().offer(view);
        }
    }

    private void handle(Socket socket) {
        Peer peer = null;
        try (socket) {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(8000);
            var in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            if (in.readInt() != PracticeWire.MAGIC || in.readInt() != PracticeWire.VERSION) return;
            int length = in.readUnsignedByte();
            if (length < 1 || length > 16) return;
            byte[] name = new byte[length];
            in.readFully(name);
            String username = new String(name, java.nio.charset.StandardCharsets.US_ASCII);
            int mode = in.readUnsignedByte();
            if (!authenticate(in, username, mode)) {
                out.writeInt(-1);
                out.flush();
                return;
            }
            int id = match.join(accounts == null ? username : AccountStore.normalize(username));
            if (id != 0) peer = new Peer(id, new ArrayBlockingQueue<>(1));
            out.writeInt(id);
            out.flush();
            if (peer == null) return;
            Peer connected = peer;
            peers.add(connected);
            workers.execute(() -> write(socket, out, connected));
            long period = System.nanoTime();
            int messages = 0;
            while (!socket.isClosed()) {
                int action = in.readUnsignedByte();
                long now = System.nanoTime();
                if (now - period >= 1_000_000_000L) { period = now; messages = 0; }
                if (++messages > 120) return;
                switch (action) {
                    case PracticeWire.CONTROL -> match.input(id, PracticeWire.readIntent(in));
                    case PracticeWire.READY -> match.ready(id);
                    case PracticeWire.SWAP -> {
                        int source = in.readUnsignedByte(), target = in.readUnsignedByte();
                        if (target == PracticeWire.QUICK_MOVE) match.quickMove(id, source);
                        else match.swap(id, source, target);
                    }
                    case PracticeWire.ACTION -> match.action(id, in.readUnsignedByte());
                    default -> throw new IOException("Unknown practice message");
                }
            }
        } catch (IOException ignored) {
            // A disconnected or incompatible guest releases the seat.
        } finally {
            if (peer != null) {
                peers.remove(peer);
                match.leave(peer.id());
            }
            connections.remove(socket);
            capacity.release();
        }
    }

    private boolean authenticate(DataInputStream in, String name, int mode) throws IOException {
        if (accounts == null) return mode == 0;
        if ((mode != 1 && mode != 2) || !AccountStore.validName(name)) return false;
        int size = in.readUnsignedByte();
        if (size < 10 || size > 128 || !hashing.tryAcquire()) return false;
        char[] password = new char[size];
        try {
            for (int i = 0; i < size; i++) password[i] = in.readChar();
            return mode == 1 ? accounts.authenticate(name, password) : accounts.register(name, password);
        } finally {
            Arrays.fill(password, '\0');
            hashing.release();
        }
    }

    private static void write(Socket socket, DataOutputStream out, Peer peer) {
        try {
            while (!socket.isClosed()) {
                var view = peer.updates().poll(250, TimeUnit.MILLISECONDS);
                if (view != null) {
                    PracticeWire.writeView(out, view);
                    out.flush();
                }
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch (IOException failed) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    @Override public void close() throws IOException {
        try {
            listener.close();
            for (Socket socket : connections) try { socket.close(); } catch (IOException ignored) { }
        } finally {
            clock.shutdownNow();
            workers.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        String bind = "127.0.0.1";
        int port = DEFAULT_PORT;
        Path tlsDirectory = Path.of("practice-tls");
        Path factionSave = null;
        String season = null;
        PracticeMatch.Kit kit = PracticeMatch.Kit.NO_DEBUFF;
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (i + 1 == args.length) throw new IllegalArgumentException("Missing value for " + key);
            String value = args[++i];
            switch (key) {
                case "--bind" -> bind = value;
                case "--port" -> port = Integer.parseInt(value);
                case "--tls" -> tlsDirectory = Path.of(value);
                case "--kit" -> kit = PracticeMatch.Kit.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
                case "--factions" -> factionSave = Path.of(value);
                case "--season" -> season = value;
                default -> throw new IllegalArgumentException("Unknown practice server option " + key);
            }
        }
        if (season != null && (factionSave == null || !Set.of("begin", "end").contains(season)))
            throw new IllegalArgumentException("--season begin|end requires --factions FILE");
        if (factionSave == null) {
            serve(bind, port, tlsDirectory, new PracticeMatch(kit, false), null);
            return;
        }
        factionSave = factionSave.toAbsolutePath().normalize();
        Files.createDirectories(factionSave.getParent());
        Path lock = factionSave.resolveSibling(factionSave.getFileName() + ".lock");
        try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock owner = channel.tryLock()) {
            if (owner == null) throw new IOException("This faction save is already in use");
            FactionMatch factions = new FactionMatch(factionSave);
            if ("begin".equals(season)) factions.beginSeason();
            if ("end".equals(season)) factions.endSeason();
            serve(bind, port, tlsDirectory, factions,
                    new AccountStore(factionSave.resolveSibling(factionSave.getFileName() + ".accounts")));
        }
    }

    private static void serve(String bind, int port, Path tlsDirectory, ArenaMatch match, AccountStore accounts)
            throws Exception {
        var identity = SecureTransport.server(tlsDirectory);
        try (var server = new PracticeServer(bind, port, identity.context(), match, accounts)) {
            System.out.println("Basalt Courtyard practice server on port " + server.port());
            System.out.println("Client --fingerprint " + identity.fingerprint());
            new java.util.concurrent.CountDownLatch(1).await();
        }
    }
}
