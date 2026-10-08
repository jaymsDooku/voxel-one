package dev.jayms.net;

import dev.jayms.net.city.*;
import dev.jayms.net.model.*;

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
    private final Map<String, Inventory> inventories = new HashMap<>();
    private final Map<String, Integer> health = new HashMap<>();
    private final Map<Integer, ItemDrop> drops = new LinkedHashMap<>();
    private Terrain terrain;
    private WorldVoxels voxels;
    private ModelLibrary models = new ModelLibrary();
    private int nextDrop;
    private CitySimulation city;
    private Path citySave;
    private long lastTick = System.nanoTime();
    private int cityTicks;
    private final Map<String, AttemptWindow> attempts = new HashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final Semaphore connections = new Semaphore(64), hashing = new Semaphore(4);
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ScheduledExecutorService saves = Executors.newSingleThreadScheduledExecutor();
    private final Path save;
    private volatile boolean running = true;
    /** Fixed transport codes for isolated integration diagnostics; never includes account data. */
    public enum TransportFailure { READ_TIMEOUT, INVALID_MOVEMENT, MESSAGE_RATE, UNKNOWN_MESSAGE, OUTPUT_QUEUE, WRITE_IO, READ_IO }
    private final java.util.concurrent.atomic.AtomicReference<TransportFailure> transportFailure = new java.util.concurrent.atomic.AtomicReference<>();
    public TransportFailure transportFailure() { return transportFailure.get(); }
    private void recordTransportFailure(TransportFailure reason) { transportFailure.compareAndSet(null, reason); }

    private record AttemptWindow(long start, int count) {}

    public MultiplayerServer(
            String bind, int port, Path save, AccountStore accounts, SSLContext tls)
            throws IOException {
        this(bind, port, save, accounts, tls, Terrain.DEFAULT_SEED);
    }

    public MultiplayerServer(
            String bind, int port, Path save, AccountStore accounts, SSLContext tls, long seed)
            throws IOException {
        this(bind, port, save, accounts, tls, seed, GameConfig.sandbox());
    }

    public MultiplayerServer(
            String bind,
            int port,
            Path save,
            AccountStore accounts,
            SSLContext tls,
            long seed,
            GameConfig game)
            throws IOException {
        this(bind, port, save, accounts, tls, seed, game, ProductionCatalog.cityGame());
    }

    public MultiplayerServer(
            String bind,
            int port,
            Path save,
            AccountStore accounts,
            SSLContext tls,
            long seed,
            GameConfig game,
            ProductionCatalog production)
            throws IOException {
        terrain = new Terrain(seed);
        this.save = save;
        this.accounts = accounts;
        if (save != null && Files.exists(save)) {
            try (var in = new DataInputStream(Files.newInputStream(save))) {
                if (in.readInt() != Protocol.MAGIC) throw new IOException("Invalid world save");
                int count = in.readInt();
                terrain = new Terrain(seed, Terrain.LEGACY_VERSION);
                boolean versioned = count == -7;
                boolean colored = count == -6 || versioned;
                boolean fractional = count == -5 || colored;
                boolean withModels = count == -4 || fractional;
                boolean modern = count == -3 || withModels;
                if (modern) {
                    long storedSeed = in.readLong();
                    int generator = versioned ? in.readInt() : Terrain.LEGACY_VERSION;
                    try {
                        terrain = new Terrain(storedSeed, generator);
                    } catch (IllegalArgumentException e) {
                        throw new IOException("Unsupported terrain generator", e);
                    }
                    if (withModels) models = ModelLibrary.read(in);
                    count = in.readInt();
                }
                if (count < 0 || count > 2000000) throw new IOException("Invalid edit count");
                for (int i = 0; i < count; i++) {
                    var e =
                            colored
                                    ? Protocol.Edit.read(in)
                                    : fractional
                                            ? Protocol.Edit.readV7(in)
                                            : Protocol.Edit.readLegacy(in);
                    if (!e.valid() || !models.has(e.type()))
                        throw new IOException("Invalid saved block");
                    edits.put(e.key(), e);
                }
                if (modern) {
                    int players = in.readInt();
                    if (players < 0 || players > 10000)
                        throw new IOException("Invalid player save");
                    for (int i = 0; i < players; i++) {
                        String name = Protocol.readText(in, 16);
                        Inventory inventory = Inventory.read(in);
                        for (int slot = 0; slot < Inventory.SIZE; slot++)
                            if (!models.has(inventory.type(slot)))
                                throw new IOException("Missing inventory model");
                        inventories.put(name, inventory);
                        int hp = in.readUnsignedByte();
                        if (hp > 20) throw new IOException("Invalid health");
                        health.put(name, hp);
                    }
                    int items = in.readInt();
                    if (items < 0 || items > 100000) throw new IOException("Invalid drops");
                    for (int i = 0; i < items; i++) {
                        ItemDrop d = ItemDrop.read(in);
                        if (!models.has(d.type())) throw new IOException("Missing dropped model");
                        drops.put(d.id(), d);
                        nextDrop = Math.max(nextDrop, d.id());
                    }
                }
            }
        }
        voxels = new WorldVoxels(terrain);
        edits.values().forEach(voxels::apply);
        citySave = save == null ? null : save.resolveSibling(save.getFileName() + ".city");
        CityFrame savedCity = CitySimulation.load(citySave);
        city =
                new CitySimulation(
                        savedCity == null ? game : savedCity.config(),
                        new CitySimulation.Ground() {
                            public int type(int x, int y, int z) {
                                return block(x, y, z);
                            }

                            public boolean playerOccupied(
                                    int x, int y, int z, int width, int depth) {
                                return peers.values().stream()
                                        .anyMatch(
                                                p ->
                                                        p.pose.x() + .3 > x
                                                                && p.pose.x() - .3 < x + width
                                                                && p.pose.z() + .3 > z
                                                                && p.pose.z() - .3 < z + depth
                                                                && p.pose.y() + 1.8 > y
                                                                && p.pose.y() < y + 7);
                            }

                            public boolean occupied(int x, int y, int z, int width, int depth) {
                                return playerOccupied(x, y, z, width, depth)
                                        || city != null
                                                && CityOccupancy.overlaps(
                                                        city.frame(), x, y, z, width, 7, depth);
                            }

                            public void apply(List<Protocol.Edit> batch) {
                                applyCityEdits(batch);
                            }
                        },
                        terrain,
                        savedCity,
                        production);
        listener = tls.getServerSocketFactory().createServerSocket();
        ((SSLServerSocket) listener).setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        listener.bind(new InetSocketAddress(bind, port));
        saves.scheduleAtFixedRate(this::tickItems, 100, 100, TimeUnit.MILLISECONDS);
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
            Map<Integer, ItemDrop> initialDrops;
            ModelLibrary initialModels;
            Inventory initialInventory;
            int initialHealth;
            CityFrame initialCity;
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
                inventories.computeIfAbsent(username, k -> new Inventory());
                health.putIfAbsent(username, 20);
                snapshot = new LinkedHashMap<>(edits);
                initialPlayers = new ArrayList<>(peers.values());
                initialDrops = new LinkedHashMap<>(drops);
                initialModels = models.copy();
                initialInventory = inventories.get(username).copy();
                initialHealth = health.get(username);
                initialCity = city.frame();
            }
            authReply(out, true, "Welcome " + username);
            out.writeInt(peer.pose.id());
            peer.pose.write(out);
            out.writeUTF(username);
            out.writeLong(terrain.seed);
            out.writeInt(terrain.version);
            initialModels.write(out);
            initialInventory.write(out);
            out.writeByte(initialHealth);
            out.writeInt(snapshot.size());
            for (var e : snapshot.values()) e.write(out);
            out.writeInt(initialPlayers.size());
            for (var p : initialPlayers) {
                p.pose.write(out);
                out.writeUTF(p.name);
            }
            out.writeInt(initialDrops.size());
            for (ItemDrop drop : initialDrops.values()) drop.write(out);
            initialCity.write(out);
            out.flush();
            if (in.readUnsignedByte() != Protocol.READY) throw new IOException("Expected ready");
            synchronized (this) {
                peer.enqueue(new Event(Protocol.READY, null, null, null, 0, false));
                for (var model : models.entries())
                    if (initialModels.get(model.id()) == null) peer.enqueue(modelEvent(model));
                for (var e : edits.values())
                    if (!e.equals(snapshot.get(e.key())))
                        peer.enqueue(new Event(Protocol.BLOCK, null, e, null, 0, true));
                for (var p : peers.values())
                    peer.enqueue(new Event(Protocol.JOIN, p.pose, null, p.name, 0, true));
                sendInventory(peer);
                for (ItemDrop drop : drops.values())
                    if (!drop.equals(initialDrops.get(drop.id())))
                        peer.enqueue(
                                new Event(Protocol.DROP, null, null, null, 0, true, null, drop, 0));
                for (ItemDrop drop : initialDrops.values())
                    if (!drops.containsKey(drop.id()))
                        peer.enqueue(
                                new Event(
                                        Protocol.DROP,
                                        null,
                                        null,
                                        null,
                                        0,
                                        true,
                                        null,
                                        new ItemDrop(
                                                drop.id(),
                                                drop.type(),
                                                0,
                                                drop.x(),
                                                drop.y(),
                                                drop.z()),
                                        0));
                peers.put(peer.pose.id(), peer);
                broadcast(new Event(Protocol.JOIN, peer.pose, null, peer.name, 0, true));
                peer.enqueue(cityState());
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
                if (++messages > 120) { recordTransportFailure(TransportFailure.MESSAGE_RATE); throw new IOException("Message rate exceeded"); }
                Protocol.Pose pose = type == Protocol.MOVE ? Protocol.Pose.read(in) : null;
                Protocol.BlockRequest request =
                        type == Protocol.BLOCK ? Protocol.BlockRequest.read(in) : null;
                int swapA = type == Protocol.SWAP ? in.readUnsignedByte() : -1;
                int swapB = type == Protocol.SWAP ? in.readUnsignedByte() : -1;
                int modelRequest = type == Protocol.MODEL_CREATE ? in.readInt() : 0;
                ModelDefinition model =
                        type == Protocol.MODEL_CREATE ? ModelDefinition.read(in) : null;
                int recipe = type == Protocol.CRAFT ? in.readInt() : -1;
                Protocol.Pose craftPose = type == Protocol.CRAFT ? Protocol.Pose.read(in) : null;
                CityCommand cityCommand =
                        type == Protocol.CITY_COMMAND ? CityCommand.read(in) : null;
                synchronized (this) {
                    if (type == Protocol.CITY_COMMAND) {
                        String result;
                        long now = System.nanoTime();
                        if (now - peer.lastCity < 500_000_000L)
                            result = "Wait briefly before another city command";
                        else {
                            peer.lastCity = now;
                            result = city.command(cityCommand, peer.pose.id(), peer.pose);
                        }
                        peer.enqueue(new Event(Protocol.CITY_RESULT, null, null, result, 0, true));
                        broadcast(cityState());
                    } else if (type == Protocol.MOVE) {
                        checkPose(peer, pose);
                        updateHealth(peer, pose);
                        city.riderMoved(peer.pose.id(), peer.pose);
                        broadcast(new Event(type, peer.pose, null, null, 0, true));
                    } else if (type == Protocol.BLOCK) {
                        // The edit carries the exact pose when clicked, ordered ahead of this
                        // mutation.
                        checkPose(peer, request.pose());
                        peer.pose = request.pose();
                        var e = request.edit();
                        var p = peer.pose;
                        int current = e.valid() ? voxels.region(e) : 0;
                        double distance =
                                Math.pow(e.minX() + e.size() / 2 - p.x(), 2)
                                        + Math.pow(e.minY() + e.size() / 2 - p.y() - 1.6, 2)
                                        + Math.pow(e.minZ() + e.size() / 2 - p.z(), 2);
                        boolean accepted =
                                e.valid()
                                        && models.has(e.type())
                                        && distance <= 49
                                        && (e.type() != 0 || drops.size() < 100000)
                                        && (e.type() == 0
                                                ? current != 0 && current != Blocks.PARTIAL
                                                : current == 0)
                                        && (e.type() == 0
                                                || request.slot() >= 0
                                                        && request.slot() < Inventory.HOTBAR
                                                        && inventories
                                                                        .get(peer.name)
                                                                        .type(request.slot())
                                                                == e.type()
                                                        && inventories
                                                                        .get(peer.name)
                                                                        .count(request.slot())
                                                                > 0)
                                        && (e.type() == 0
                                                || peers.values().stream()
                                                        .noneMatch(
                                                                other -> overlaps(other.pose, e)))
                                        && (e.type() == 0
                                                || !CityOccupancy.overlaps(city.frame(), e));
                        if (accepted) {
                            if (e.type() != 0)
                                inventories.get(peer.name).take(request.slot(), e.type());
                            else {
                                ItemDrop drop =
                                        new ItemDrop(
                                                ++nextDrop,
                                                current,
                                                1,
                                                e.minX() + e.size() / 2,
                                                e.minY() + .35f,
                                                e.minZ() + e.size() / 2);
                                drops.put(drop.id(), drop);
                                broadcast(
                                        new Event(
                                                Protocol.DROP,
                                                null,
                                                null,
                                                null,
                                                0,
                                                true,
                                                null,
                                                drop,
                                                0));
                            }
                            sendInventory(peer);
                            voxels.apply(e);
                            WorldVoxels.remember(edits, e);
                            broadcast(new Event(Protocol.BLOCK, null, e, null, 0, true));
                            broadcast(new Event(Protocol.MOVE, peer.pose, null, null, 0, true));
                        }
                        peer.enqueue(
                                new Event(
                                        Protocol.EDIT_RESULT,
                                        null,
                                        e.withType(accepted ? e.type() : current),
                                        null,
                                        request.requestId(),
                                        accepted,
                                        null,
                                        null,
                                        0,
                                        null,
                                        new Protocol.CellState(
                                                e.x(),
                                                e.y(),
                                                e.z(),
                                                voxels.cell(e.x(), e.y(), e.z()).copy().freeze())));
                    } else if (type == Protocol.SWAP) {
                        if (swapA >= Inventory.SIZE || swapB >= Inventory.SIZE)
                            throw new IOException("Invalid inventory slot");
                        inventories.get(peer.name).swap(swapA, swapB);
                        sendInventory(peer);
                    } else if (type == Protocol.CRAFT) {
                        checkPose(peer, craftPose);
                        peer.pose = craftPose;
                        var result = Crafting.prepare(inventories.get(peer.name), recipe);
                        boolean accepted =
                                result.accepted()
                                        && (result.excess() == 0 || drops.size() < 100000);
                        if (accepted) {
                            inventories.put(peer.name, result.inventory());
                            if (result.excess() > 0) {
                                var drop =
                                        new ItemDrop(
                                                ++nextDrop,
                                                result.output(),
                                                result.excess(),
                                                peer.pose.x() + .4f,
                                                peer.pose.y() + .35f,
                                                peer.pose.z());
                                drops.put(drop.id(), drop);
                                broadcast(
                                        new Event(
                                                Protocol.DROP,
                                                null,
                                                null,
                                                null,
                                                0,
                                                true,
                                                null,
                                                drop,
                                                0));
                            }
                            sendInventory(peer);
                        }
                        String message =
                                result.accepted() && !accepted
                                        ? "Too many ground items: collect some before crafting."
                                        : result.message();
                        peer.enqueue(
                                new Event(
                                        Protocol.CRAFT_RESULT,
                                        null,
                                        null,
                                        message,
                                        recipe,
                                        accepted));
                    } else if (type == Protocol.MODEL_CREATE) {
                        String message;
                        boolean accepted = false;
                        try {
                            if (System.nanoTime() - peer.lastModel < 2_000_000_000L)
                                throw new IOException(
                                        "Wait two seconds before creating another model item");
                            peer.lastModel = System.nanoTime();
                            int existing = models.find(model);
                            int id = existing < 0 ? models.nextId() : existing;
                            if (!inventories.get(peer.name).hasSpace(id))
                                throw new IOException("Inventory full: make an empty slot first");
                            var entry = models.register(model, peer.name);
                            if (existing < 0) broadcast(modelEvent(entry));
                            inventories.get(peer.name).add(entry.id(), 1);
                            sendInventory(peer);
                            accepted = true;
                            message =
                                    "Created "
                                            + entry.definition().name()
                                            + ". Close the editor and select its hotbar slot to"
                                            + " place it.";
                        } catch (IOException e) {
                            message = e.getMessage();
                        }
                        peer.enqueue(
                                new Event(
                                        Protocol.MODEL_RESULT,
                                        null,
                                        null,
                                        message,
                                        modelRequest,
                                        accepted));
                    } else { recordTransportFailure(TransportFailure.UNKNOWN_MESSAGE); throw new IOException("Unknown message"); }
                }
            }
        } catch (IOException e) {
            if (running && peer != null && !(e instanceof EOFException)) {
                TransportFailure reason = e instanceof SocketTimeoutException ? TransportFailure.READ_TIMEOUT
                        : "Invalid movement".equals(e.getMessage()) ? TransportFailure.INVALID_MOVEMENT
                        : "Message rate exceeded".equals(e.getMessage()) ? TransportFailure.MESSAGE_RATE
                        : "Unknown message".equals(e.getMessage()) ? TransportFailure.UNKNOWN_MESSAGE
                        : TransportFailure.READ_IO;
                recordTransportFailure(reason);
            }
            if (running && !(e instanceof EOFException))
                System.out.println("Connection closed: " + e.getMessage());
        } finally {
            synchronized (this) {
                if (reserved) sessions.remove(username);
                if (peer != null && peers.remove(peer.pose.id()) != null) {
                    city.release(peer.pose.id());
                    broadcast(new Event(Protocol.LEAVE, peer.pose, null, null, 0, true));
                }
            }
        }
    }

    private void sendInventory(Peer peer) {
        peer.enqueue(
                new Event(
                        Protocol.INVENTORY,
                        null,
                        null,
                        null,
                        0,
                        true,
                        inventories.get(peer.name).copy(),
                        null,
                        health.get(peer.name)));
    }

    private void updateHealth(Peer peer, Protocol.Pose pose) throws IOException {
        peer.pose = pose;
        if (pose.flying()) {
            peer.fallTop = pose.y();
            return;
        }
        peer.fallTop = Math.max(peer.fallTop, pose.y());
        boolean grounded =
                solid(
                        pose.x() - .3f,
                        pose.y() - .05f,
                        pose.z() - .3f,
                        pose.x() + .3f,
                        pose.y(),
                        pose.z() + .3f);
        if (grounded) {
            int damage = Math.max(0, (int) Math.floor(peer.fallTop - pose.y() - 3));
            peer.fallTop = pose.y();
            if (damage > 0) {
                int hp = Math.max(0, health.get(peer.name) - damage);
                health.put(peer.name, hp);
                if (hp == 0) {
                    peer.pose = safeSpawn(pose.id());
                    peer.fallTop = peer.pose.y();
                    health.put(peer.name, 20);
                    peer.enqueue(new Event(Protocol.RESPAWN, peer.pose, null, null, 0, true));
                }
                sendInventory(peer);
            }
        }
    }

    private synchronized void tickItems() {
        if (!running) return;
        long now = System.nanoTime();
        city.advance((now - lastTick) / 1e9);
        lastTick = now;
        if (++cityTicks % 2 == 0) broadcast(cityState());
        var iterator = drops.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ItemDrop d = entry.getValue();
            float y = d.y();
            if (y > Terrain.MIN_Y + .3
                    && !solid(
                            d.x() - .05f,
                            y - .31f,
                            d.z() - .05f,
                            d.x() + .05f,
                            y - .3f,
                            d.z() + .05f)) {
                y = Math.max(Terrain.MIN_Y + .3f, y - .3f);
                d = new ItemDrop(d.id(), d.type(), d.count(), d.x(), y, d.z());
                entry.setValue(d);
                broadcast(new Event(Protocol.DROP, null, null, null, 0, true, null, d, 0));
            }
            for (Peer peer : peers.values()) {
                var p = peer.pose;
                double distance =
                        Math.pow(d.x() - p.x(), 2)
                                + Math.pow(d.y() - p.y() - .7, 2)
                                + Math.pow(d.z() - p.z(), 2);
                if (distance > 4) continue;
                int remaining = inventories.get(peer.name).add(d.type(), d.count());
                if (remaining == d.count()) continue;
                sendInventory(peer);
                d = new ItemDrop(d.id(), d.type(), remaining, d.x(), d.y(), d.z());
                broadcast(new Event(Protocol.DROP, null, null, null, 0, true, null, d, 0));
                if (remaining == 0) {
                    iterator.remove();
                    break;
                }
                entry.setValue(d);
            }
        }
    }

    private void checkPose(Peer peer, Protocol.Pose pose) throws IOException {
        if (!pose.valid() || pose.id() != peer.pose.id() || !models.has(pose.heldItem())) {
            recordTransportFailure(TransportFailure.INVALID_MOVEMENT);
            throw new IOException("Invalid movement");
        }
    }

    private int block(int x, int y, int z) {
        return voxels.type(x, y, z);
    }

    private Protocol.Pose safeSpawn(int id) throws IOException {
        for (int x = 8; x < 16; x++)
            for (int z = 24; z < 32; z++) {
                int surface = -32;
                for (int y = Terrain.MAX_Y; y >= Terrain.MIN_Y; y--)
                    if (block(x, y, z) != 0) {
                        surface = y;
                        break;
                    }
                if (surface <= Terrain.MAX_Y - 2)
                    return new Protocol.Pose(id, x + .5f, surface + 1.01f, z + .5f, -90, 0);
            }
        throw new IOException("Spawn area is blocked; clear space before joining.");
    }

    private boolean solid(float x0, float y0, float z0, float x1, float y1, float z1) {
        for (int x = (int) Math.floor(x0); x < Math.ceil(x1); x++)
            for (int y = (int) Math.floor(y0); y < Math.ceil(y1); y++)
                for (int z = (int) Math.floor(z0); z < Math.ceil(z1); z++) {
                    int type = block(x, y, z);
                    if (type == 0 || type == Blocks.WATER) continue;
                    var model = models.get(type);
                    if (type == Blocks.PARTIAL) {
                        if (voxels.cell(x, y, z)
                                .intersects(x0 - x, y0 - y, z0 - z, x1 - x, y1 - y, z1 - z))
                            return true;
                        continue;
                    }
                    if (model == null
                            || model.definition()
                                    .voxels()
                                    .intersects(x0 - x, y0 - y, z0 - z, x1 - x, y1 - y, z1 - z))
                        return true;
                }
        return false;
    }

    private boolean overlaps(Protocol.Pose p, Protocol.Edit e) {
        if (e.depth() > 0)
            return p.x() + .3 > e.minX()
                    && p.x() - .3 < e.minX() + e.size()
                    && p.y() + 1.8 > e.minY()
                    && p.y() < e.minY() + e.size()
                    && p.z() + .3 > e.minZ()
                    && p.z() - .3 < e.minZ() + e.size();
        var model = models.get(e.type());
        if (model != null)
            return model.definition()
                    .voxels()
                    .intersects(
                            p.x() - .3f - e.x(),
                            p.y() - e.y(),
                            p.z() - .3f - e.z(),
                            p.x() + .3f - e.x(),
                            p.y() + 1.8f - e.y(),
                            p.z() + .3f - e.z());
        return p.x() + .3 > e.x()
                && p.x() - .3 < e.x() + 1
                && p.y() + 1.8 > e.y()
                && p.y() < e.y() + 1
                && p.z() + .3 > e.z()
                && p.z() - .3 < e.z() + 1;
    }

    private Event modelEvent(ModelLibrary.Entry model) {
        return new Event(Protocol.MODEL_DEFINE, null, null, null, 0, true, null, null, 0, model);
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
            boolean accepted,
            Inventory inventory,
            ItemDrop drop,
            int health,
            ModelLibrary.Entry model,
            Protocol.CellState state,
            byte[] payload) {
        Event(
                int type,
                Protocol.Pose pose,
                Protocol.Edit edit,
                String name,
                int request,
                boolean accepted,
                Inventory inventory,
                ItemDrop drop,
                int health,
                ModelLibrary.Entry model,
                Protocol.CellState state) {
            this(
                    type, pose, edit, name, request, accepted, inventory, drop, health, model,
                    state, null);
        }

        Event(
                int type,
                Protocol.Pose pose,
                Protocol.Edit edit,
                String name,
                int request,
                boolean accepted,
                Inventory inventory,
                ItemDrop drop,
                int health,
                ModelLibrary.Entry model) {
            this(type, pose, edit, name, request, accepted, inventory, drop, health, model, null);
        }

        Event(
                int type,
                Protocol.Pose pose,
                Protocol.Edit edit,
                String name,
                int request,
                boolean accepted,
                Inventory inventory,
                ItemDrop drop,
                int health) {
            this(type, pose, edit, name, request, accepted, inventory, drop, health, null);
        }

        Event(
                int type,
                Protocol.Pose pose,
                Protocol.Edit edit,
                String name,
                int request,
                boolean accepted) {
            this(type, pose, edit, name, request, accepted, null, null, 0, null);
        }
    }

    private final class Peer {
        final Socket socket;
        final DataOutputStream out;
        final String name;
        volatile Protocol.Pose pose;
        float fallTop;
        long lastModel, lastCity;
        final BlockingQueue<Event> queue = new ArrayBlockingQueue<>(512);

        Peer(Socket socket, DataOutputStream out, String name, Protocol.Pose pose) {
            this.socket = socket;
            this.out = out;
            this.name = name;
            this.pose = pose;
            fallTop = pose.y();
        }

        void enqueue(Event e) {
            if (!queue.offer(e)) { recordTransportFailure(TransportFailure.OUTPUT_QUEUE); disconnect(); }
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
                                        e.state.write(out);
                                    }
                                    case Protocol.INVENTORY -> {
                                        e.inventory.write(out);
                                        out.writeByte(e.health);
                                    }
                                    case Protocol.DROP -> e.drop.write(out);
                                    case Protocol.RESPAWN -> e.pose.write(out);
                                    case Protocol.MODEL_DEFINE -> e.model.write(out);
                                    case Protocol.MODEL_RESULT -> {
                                        out.writeInt(e.request);
                                        out.writeBoolean(e.accepted);
                                        out.writeUTF(e.name);
                                    }
                                    case Protocol.CRAFT_RESULT -> {
                                        out.writeBoolean(e.accepted);
                                        out.writeUTF(e.name);
                                    }
                                    case Protocol.CITY_STATE, Protocol.CITY_WORLD ->
                                            out.write(e.payload);
                                    case Protocol.CITY_RESULT -> out.writeUTF(e.name);
                                    case Protocol.READY -> {}
                                    default -> throw new IOException("Unknown queued event");
                                }
                                out.flush();
                            }
                        } catch (IOException | InterruptedException e) {
                            if (running && !socket.isClosed() && e instanceof IOException) recordTransportFailure(TransportFailure.WRITE_IO);
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

    private Event cityState() {
        try {
            var bytes = new ByteArrayOutputStream();
            city.frame().write(new DataOutputStream(bytes));
            return new Event(
                    Protocol.CITY_STATE,
                    null,
                    null,
                    null,
                    0,
                    true,
                    null,
                    null,
                    0,
                    null,
                    null,
                    bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void applyCityEdits(List<Protocol.Edit> batch) {
        for (var edit : batch) {
            voxels.apply(edit);
            WorldVoxels.remember(edits, edit);
        }
        for (int start = 0; start < batch.size(); start += 8192) {
            try {
                var bytes = new ByteArrayOutputStream();
                var out = new DataOutputStream(bytes);
                int end = Math.min(start + 8192, batch.size());
                out.writeInt(end - start);
                for (int i = start; i < end; i++) batch.get(i).write(out);
                broadcast(
                        new Event(
                                Protocol.CITY_WORLD,
                                null,
                                null,
                                null,
                                0,
                                true,
                                null,
                                null,
                                0,
                                null,
                                null,
                                bytes.toByteArray()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
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
            out.writeInt(-7);
            out.writeLong(terrain.seed);
            out.writeInt(terrain.version);
            models.write(out);
            out.writeInt(edits.size());
            for (var e : edits.values()) e.write(out);
            out.writeInt(inventories.size());
            for (var e : inventories.entrySet()) {
                out.writeUTF(e.getKey());
                e.getValue().write(out);
                out.writeByte(health.getOrDefault(e.getKey(), 20));
            }
            out.writeInt(drops.size());
            for (ItemDrop drop : drops.values()) drop.write(out);
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
        city.save(citySave);
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
        long seed = Terrain.DEFAULT_SEED;
        boolean cityGame = false, cycle = true;
        double daySeconds = 1200, startHour = 8;
        Path save = Path.of("world.dat"),
                accountFile = Path.of("accounts.db"),
                tlsDirectory = Path.of("tls");
        Path productionFile = null;
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--pedestrian-spacing", "--mounted-spacing" ->
                    RoadSpacing.configure(args[i], args[++i]);
                case "--production-config" -> productionFile = Path.of(args[++i]);
                case "--game" -> {
                    String value = args[++i];
                    if (!value.equals("city") && !value.equals("sandbox"))
                        throw new IllegalArgumentException("Game must be city or sandbox");
                    cityGame = value.equals("city");
                }
                case "--day-seconds" -> daySeconds = Double.parseDouble(args[++i]);
                case "--start-hour" -> startHour = Double.parseDouble(args[++i]);
                case "--fixed-time" -> cycle = false;
                case "--bind" -> bind = args[++i];
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--world" -> save = Path.of(args[++i]);
                case "--accounts" -> accountFile = Path.of(args[++i]);
                case "--tls-dir" -> tlsDirectory = Path.of(args[++i]);
                case "--create-account" -> create = args[++i];
                default ->
                        throw new IllegalArgumentException(
                                "Usage: --bind ADDRESS --port PORT --world FILE --accounts FILE"
                                        + " --tls-dir DIRECTORY --seed NUMBER --create-account NAME"
                                        + " --production-config FILE --pedestrian-spacing BLOCKS --mounted-spacing BLOCKS");
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
        try (var server =
                new MultiplayerServer(
                        bind,
                        port,
                        save,
                        accounts,
                        identity.context(),
                        seed,
                        new GameConfig(cityGame, cityGame && cycle, daySeconds, startHour),
                        productionFile == null
                                ? ProductionCatalog.cityGame()
                                : ProductionCatalog.load(productionFile))) {
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
