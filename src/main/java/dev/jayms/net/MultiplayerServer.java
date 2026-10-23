package dev.jayms.net;

import dev.jayms.GameIdentity;

import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import dev.jayms.net.structure.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
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
    // City paving emits many edits. Index by cell so replacement never scans the whole world.
    private WorldVoxels.History edits = new WorldVoxels.History();
    private final StructureEngine structureEngine=new StructureEngine();
    private final java.util.concurrent.atomic.AtomicBoolean structureOperation=new java.util.concurrent.atomic.AtomicBoolean();
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
    private final ServerTickCadence tickCadence;
    private final Map<String, AttemptWindow> attempts = new HashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final Semaphore connections = new Semaphore(64), hashing = new Semaphore(4);
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ScheduledExecutorService saves = Executors.newSingleThreadScheduledExecutor();
    private final Path save;
    private volatile boolean running = true;
    /** Fixed transport codes for isolated integration diagnostics; never includes account data. */
    public enum TransportFailure { READ_TIMEOUT, INVALID_MOVEMENT, MESSAGE_RATE, UNKNOWN_MESSAGE, OUTPUT_QUEUE, WRITE_IO, READ_IO }
    public enum CityCommandStage { NONE, WAITING, RUNNING, REPLY_QUEUED }
    private volatile CityCommandStage cityCommandStage = CityCommandStage.NONE;
    public CityCommandStage cityCommandStage() { return cityCommandStage; }
    private final java.util.concurrent.atomic.AtomicReference<TransportFailure> transportFailure = new java.util.concurrent.atomic.AtomicReference<>();
    public TransportFailure transportFailure() { return transportFailure.get(); }
    private void recordTransportFailure(TransportFailure reason) { transportFailure.compareAndSet(null, reason); }

    /** Last 4096 actual tick starts/completions; milliseconds, nearest-rank percentiles.
     * Invocation lock wait excludes scheduler queue delay; intervals expose missed cadence.
     * Empty windows have zero samples/rate. Diagnostics do not qualify performance budgets. */
    public record TickDistribution(int samples,double medianMs,double p95Ms,double worstMs) {}
    public record TickTiming(long totalTicks,long failedTicks,int windowTicks,double windowSpanMs,double startsPerSecond,
                             TickDistribution intervals,TickDistribution lockWait,TickDistribution work) {}
    private final ServerTickTiming tickTiming=new ServerTickTiming();
    public TickTiming tickTiming(){return tickTiming.snapshot();}

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
            try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(save), 64 * 1024))) {
                if (in.readInt() != Protocol.MAGIC) throw new IOException("Invalid world save");
                int count = in.readInt();
                terrain = new Terrain(seed, Terrain.LEGACY_VERSION);
                boolean structureFormat=count == -9;
                boolean itemFormat = count == -8 || structureFormat;
                boolean versioned = count == -7 || itemFormat;
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
                    if (withModels) models = structureFormat?ModelLibrary.readAll(in):ModelLibrary.read(in);
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
                        Inventory inventory = Inventory.read(in,itemFormat);
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
                        ItemDrop d = ItemDrop.read(in,itemFormat);
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
        city.loadHistory(citySave);
        city.collectHistory();
        listener = tls.getServerSocketFactory().createServerSocket();
        ((SSLServerSocket) listener).setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        listener.bind(new InetSocketAddress(bind, port));
        // Target 20 Hz without catch-up bursts after slow/blocked ticks. Simulation uses
        // actual elapsed time; every reschedule leaves at least a 1 ms command window.
        tickCadence=new ServerTickCadence(System.nanoTime());
        scheduleTick(ServerTickCadence.PERIOD);
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
            initialModels.writeAll(out);
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
                var changedModels=new ArrayList<ModelLibrary.Entry>();
                for(var model:models.entries())if(initialModels.get(model.id())==null)changedModels.add(model);
                var changedCells=new LinkedHashMap<String,Protocol.CellState>();
                for(var e:edits.values())if(!e.equals(snapshot.get(e.key())))changedCells.put(e.cellKey(),new Protocol.CellState(e.x(),e.y(),e.z(),voxels.cell(e.x(),e.y(),e.z())));
                if(!changedModels.isEmpty()||!changedCells.isEmpty())peer.enqueue(structureEvent(new StructureWire.Delta(changedModels,new ArrayList<>(changedCells.values()))));
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
                if(type==Protocol.STRUCTURE_REQUEST){handleStructure(peer,in);continue;}
                Protocol.Pose pose = type == Protocol.MOVE ? Protocol.Pose.read(in) : null;
                Protocol.BlockRequest request =
                        type == Protocol.BLOCK ? Protocol.BlockRequest.read(in) : null;
                int swapA = type == Protocol.SWAP ? in.readUnsignedByte() : -1;
                int swapB = type == Protocol.SWAP ? in.readUnsignedByte() : -1;
                if(type==Protocol.HISTORY_QUERY) {
                    var historyRequest=HistoryWire.Request.read(in);
                    long now=System.nanoTime();
                    if(now-peer.lastHistory<250_000_000L || !peer.historyPending.compareAndSet(false,true))
                        throw new IOException("History query rate exceeded");
                    peer.lastHistory=now;
                    final Peer historyPeer=peer;
                    workers.submit(()->{
                        try {
                            var bytes=new ByteArrayOutputStream();var response=new DataOutputStream(bytes);
                            response.writeLong(historyRequest.request());
                            try {
                                var reply=city.history.query(historyRequest.city(),historyRequest.request(),historyRequest.ids(),historyRequest.start(),historyRequest.end(),historyRequest.resolution(),historyRequest.limit());
                                response.writeBoolean(true);HistoryWire.write(response,reply);
                            }catch(IllegalArgumentException invalid){response.writeBoolean(false);response.writeUTF(invalid.getMessage());}
                            historyPeer.enqueue(new Event(Protocol.HISTORY_REPLY,null,null,null,0,true,null,null,0,null,null,bytes.toByteArray()));
                        }catch(IOException ignored){historyPeer.disconnect();}

                    });
                    continue;
                }
                int modelRequest = type == Protocol.MODEL_CREATE ? in.readInt() : 0;
                ModelDefinition model =
                        type == Protocol.MODEL_CREATE ? ModelDefinition.read(in) : null;
                int craftRequest = type == Protocol.CRAFT || type == Protocol.DROP_ITEM ? in.readInt() : 0;
                int recipe = type == Protocol.CRAFT ? in.readInt() : -1;
                int batches = type == Protocol.CRAFT ? in.readInt() : 0;
                int dropSlot = type == Protocol.DROP_ITEM ? in.readUnsignedByte() : -1;
                Protocol.Pose craftPose = type == Protocol.CRAFT || type == Protocol.DROP_ITEM ? Protocol.Pose.read(in) : null;
                CityCommand cityCommand =
                        type == Protocol.CITY_COMMAND ? CityCommand.read(in) : null;
                if (type == Protocol.CITY_COMMAND) cityCommandStage = CityCommandStage.WAITING;
                synchronized (this) {
                    if (type == Protocol.CITY_COMMAND) {
                        cityCommandStage = CityCommandStage.RUNNING;
                        String result;
                        long now = System.nanoTime();
                        if (now - peer.lastCity < 500_000_000L)
                            result = "Wait briefly before another city command";
                        else {
                            peer.lastCity = now;
                            result = city.command(cityCommand, peer.pose.id(), peer.pose);
                        }
                        peer.enqueue(new Event(Protocol.CITY_RESULT, null, null, result, 0, true));
                        cityCommandStage = CityCommandStage.REPLY_QUEUED;
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
                            else if(!Blocks.structureModel(current)) {
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
                    } else if (type == Protocol.CRAFT || type == Protocol.DROP_ITEM) {
                        checkPose(peer, craftPose);
                        peer.pose = craftPose;
                        boolean accepted=false;
                        String message;
                        if(craftRequest<=peer.lastCraftRequest) message="Repeated inventory request.";
                        else {
                            peer.lastCraftRequest=craftRequest;
                            Inventory current=inventories.get(peer.name);
                            if(type==Protocol.DROP_ITEM) {
                                if(dropSlot>=Inventory.HOTBAR||current.count(dropSlot)==0) message="Nothing to drop.";
                                else if(drops.size()>=Crafting.MAX_DROPS||nextDrop==Integer.MAX_VALUE) message="Too many ground items.";
                                else {
                                    int item=current.type(dropSlot);
                                    var drop=new ItemDrop(++nextDrop,item,1,Items.dropX(peer.pose.x()),peer.pose.y()+.35f,peer.pose.z());
                                    current.take(dropSlot,item); drops.put(drop.id(),drop);
                                    broadcast(new Event(Protocol.DROP,null,null,null,0,true,null,drop,0));
                                    accepted=true; message="Dropped "+Items.name(item)+".";
                                }
                            } else {
                                var result=Crafting.prepare(current,recipe,batches,Crafting.MAX_DROPS-drops.size());
                                message=result.message();
                                int needed=Crafting.dropSlots(result.output(),result.excess());
                                if(result.accepted() && nextDrop>Integer.MAX_VALUE-needed) message="Ground item IDs exhausted.";
                                else if(result.accepted()) {
                                    List<ItemDrop> prepared=new ArrayList<>();
                                    int excess=result.excess();
                                    for(int i=1;excess>0;i++) {
                                        int n=Math.min(excess,Items.stackLimit(result.output()));
                                        prepared.add(new ItemDrop(nextDrop+i,result.output(),n,peer.pose.x()+.4f,peer.pose.y()+.35f,peer.pose.z()));
                                        excess-=n;
                                    }
                                    inventories.put(peer.name,result.inventory());
                                    for(var drop:prepared) {
                                        drops.put(drop.id(),drop);
                                        broadcast(new Event(Protocol.DROP,null,null,null,0,true,null,drop,0));
                                    }
                                    nextDrop+=needed; accepted=true;
                                }
                            }
                        }
                        if(accepted) sendInventory(peer);
                        peer.enqueue(new Event(Protocol.CRAFT_RESULT,null,null,message,craftRequest,accepted));
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

    private void scheduleTick(long delay) {
        saves.schedule(()->{
            tickItems(); // Preserve scheduler failure behavior: a failed callback stops ticking.
            if(running) {
                try {scheduleTick(tickCadence.delayAfterTick(System.nanoTime()));}
                catch(RejectedExecutionException e){if(running)throw e;}
            }
        },delay,TimeUnit.NANOSECONDS);
    }
    private void tickItems() {
        long requested=System.nanoTime(),started=0,finished=0;boolean executed=false,succeeded=false;
        try {
            synchronized(this) {
                if(!running)return;
                executed=true;started=System.nanoTime();
                try {tickItemsLocked();succeeded=true;}finally{finished=System.nanoTime();}
            }
        } finally {
            if(executed)tickTiming.record(requested,started,finished,succeeded);
        }
    }
    /** Caller holds this server's authority lock. */
    private void tickItemsLocked() {
        long now = System.nanoTime();
        city.advance((now - lastTick) / 1e9);
        lastTick = now;
        long publicationTime=System.nanoTime();
        if(tickCadence.broadcastDue(publicationTime))broadcast(cityState());
        // Keep item fall/pickup at a 100 ms target rather than doubling its speed.
        if(!tickCadence.itemsDue(publicationTime))return;
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

    private Event structureEvent(StructureWire.Delta delta)throws IOException {return structureEvent(delta,()->false);}
    private Event structureEvent(StructureWire.Delta delta,java.util.function.BooleanSupplier cancel)throws IOException {
        var bytes=new ByteArrayOutputStream();StructureWire.writePayload(new DataOutputStream(bytes),StructureWire.encode(delta,cancel));
        StructurePackage.cancelled(cancel);
        return new Event(Protocol.STRUCTURE_COMMIT,null,null,null,0,true,null,null,0,null,null,bytes.toByteArray());
    }
    private void structureResult(Peer peer,int request,boolean accepted,String message) {
        peer.enqueue(new Event(Protocol.STRUCTURE_RESULT,null,null,message,request,accepted));
    }
    private StructurePackage.Source structureSnapshot() {
        synchronized(this) {
            var geometry=voxels.snapshot();var library=models.copy();long terrainRevision=geometry.terrainRevision();
            return new StructurePackage.Source() {
                public dev.jayms.net.model.SparseVoxelOctree cell(int x,int y,int z){return geometry.cell(x,y,z);}
                public ModelLibrary models(){return library;}
                public Object chunkStamp(dev.jayms.ChunkPos position){return geometry.terrainRevision()==terrainRevision?geometry.chunkStamp(position):null;}
            };
        }
    }
    private StructureEngine.Authority structureAuthority() {
        return new StructureEngine.Authority() {
            public Object lock(){return MultiplayerServer.this;}
            public ModelLibrary models(){return models;}
            public SparseVoxelOctree cell(int x,int y,int z){return voxels.cell(x,y,z);}
            public Object chunkStamp(dev.jayms.ChunkPos position){return voxels.chunkStamp(position);}
            private Event publication(ModelLibrary staged,Map<StructurePackage.Position,SparseVoxelOctree> cells,long revision,java.util.function.BooleanSupplier cancel)throws IOException {
                List<ModelLibrary.Entry> definitions;
                synchronized(lock()) {
                    StructurePackage.cancelled(cancel);
                    if(models.revision()!=revision)throw new IOException("Model library changed; retry placement");
                    definitions=staged.entries().stream().filter(e->models.get(e.id())==null).toList();
                }
                var states=new ArrayList<Protocol.CellState>(cells.size());
                for(var entry:cells.entrySet()) {
                    StructurePackage.cancelled(cancel);var p=entry.getKey();states.add(new Protocol.CellState(p.x(),p.y(),p.z(),entry.getValue()));
                }
                return structureEvent(new StructureWire.Delta(definitions,states),cancel);
            }
            @Override public StructureEngine.Commit stageCommit(ModelLibrary staged,Map<StructurePackage.Position,SparseVoxelOctree> cells,long revision,java.util.function.BooleanSupplier cancel)throws IOException {
                var positions=new HashSet<dev.jayms.ChunkPos>();
                for(var p:cells.keySet()){StructurePackage.cancelled(cancel);positions.add(dev.jayms.ChunkPos.fromBlock(p.x(),p.y(),p.z()));}
                WorldVoxels.CellPatch geometry;
                synchronized(lock()) {
                    StructurePackage.cancelled(cancel);
                    if(models.revision()!=revision)throw new IOException("Model library changed; retry placement");
                    geometry=voxels.captureCellPatch(positions);
                }
                for(var entry:cells.entrySet()){StructurePackage.cancelled(cancel);var p=entry.getKey();geometry.replaceCell(p.x(),p.y(),p.z(),entry.getValue());}
                var publication=publication(staged,cells,revision,cancel);
                WorldVoxels.History base;WorldVoxels.History.CopyCursor cursor;long historyRevision;
                synchronized(lock()) {
                    StructurePackage.cancelled(cancel);base=edits;historyRevision=base.revision();cursor=base.copyCursor();
                }
                var next=copyStructureHistory(base,cursor,cancel);
                for(var entry:cells.entrySet()) {
                    StructurePackage.cancelled(cancel);var p=entry.getKey();
                    WorldVoxels.rememberCell(next,new Protocol.CellState(p.x(),p.y(),p.z(),entry.getValue()));
                }
                if(next.size()>2_000_000)throw new IOException("World persistence edit limit");
                var file=save==null?null:StagedWorldSave.stage(save,out->writeWorldPrefix(out,staged,next,cancel));
                return new StructureEngine.Commit() {
                    public void apply()throws IOException {
                        if(edits!=base||base.revision()!=historyRevision)throw new IOException("World changed while staging save; retry structure operation");
                        commitStaged(staged,cells,publication,geometry,next,file);
                    }
                    public void close()throws IOException {if(file!=null)file.close();}
                };
            }
            public void commit(ModelLibrary staged,Map<StructurePackage.Position,SparseVoxelOctree> cells)throws IOException {
                commitStaged(staged,cells,publication(staged,cells,models.revision(),()->false),null,null,null);
            }
            private void commitStaged(ModelLibrary staged,Map<StructurePackage.Position,SparseVoxelOctree> cells,Event publication,WorldVoxels.CellPatch patch,WorldVoxels.History stagedHistory,StagedWorldSave file)throws IOException {
                if(!running)throw new IOException("Server stopping");
                dev.jayms.net.structure.StructureOccupancy.validate(cells,city.frame(),peers.values().stream().map(peer->peer.pose).toList());
                var next=stagedHistory==null?WorldVoxels.History.copyOf(edits):stagedHistory;var geometry=patch==null?null:patch.merge(voxels);
                boolean restage=geometry==null;if(restage)geometry=voxels.snapshot();
                for(var entry:cells.entrySet()) {
                    var p=entry.getKey();var state=new Protocol.CellState(p.x(),p.y(),p.z(),entry.getValue());
                    if(stagedHistory==null)WorldVoxels.rememberCell(next,state);
                    if(restage)geometry.replaceCell(p.x(),p.y(),p.z(),entry.getValue());
                }
                if(next.size()>2_000_000)throw new IOException("World persistence edit limit");
                if(file==null)saveWorld(staged,next,true);else file.publish(MultiplayerServer.this::writeWorldTail);
                models=staged;edits=next;voxels=geometry;broadcast(publication);
            }
        };
    }
    /** Read bounded batches under the monitor; rebuild the detached index on the worker. */
    private WorldVoxels.History copyStructureHistory(WorldVoxels.History base,WorldVoxels.History.CopyCursor cursor,java.util.function.BooleanSupplier cancel)throws IOException {
        var next=new WorldVoxels.History();
        while(true) {
            StructurePackage.cancelled(cancel);List<Protocol.Edit> batch;
            synchronized(this) {
                StructurePackage.cancelled(cancel);
                if(edits!=base)throw new IOException("World changed while copying save history; retry structure operation");
                try{batch=cursor.nextBatch();}
                catch(ConcurrentModificationException changed){throw new IOException("World changed while copying save history; retry structure operation",changed);}
            }
            if(batch.isEmpty())return next;
            for(var edit:batch){StructurePackage.cancelled(cancel);next.put(edit.key(),edit);}
        }
    }
    private void handleStructure(Peer peer,DataInputStream in)throws IOException {
        int action=in.readUnsignedByte(),request=in.readInt();
        if(action==2){if(peer.structureActive.get()){peer.structureCancel.set(true);structureResult(peer,request,false,"Cancellation requested before commit");}else structureResult(peer,request,false,"No cancellable operation; placement may already be committed");return;}
        if(action!=0&&action!=1)throw new IOException("Invalid structure action");
        StructurePackage.Position anchor=null;int rotation=0;byte[] bytes=null;
        boolean reserved=structureOperation.compareAndSet(false,true);
        try {
            if(action==0) {
                anchor=new StructurePackage.Position(in.readInt(),in.readInt(),in.readInt());rotation=in.readUnsignedByte();
                if(!reserved) {
                    int length=in.readInt();if(length<0||length>StructurePackage.COMPRESSED)throw new IOException("Structure upload limit");in.skipNBytes(length);
                } else bytes=StructureWire.readPayload(in);
            }
            if(!reserved){structureResult(peer,request,false,"Another large placement is running; retry");return;}
            peer.structureCancel.set(false);peer.structureActive.set(true);structureResult(peer,request,false,"Validating structure; cancellation available");
            final var destination=anchor;final int orientation=rotation;final byte[] payload=bytes;
            workers.submit(()->{
                try {
                    if(action==1)structureEngine.undo(peer.name,structureAuthority(),peer.structureCancel::get);
                    else {
                        var asset=StructurePackage.decode(payload,peer.structureCancel::get,p->{});
                        try(var prepared=structureEngine.prepare(asset,destination,orientation,structureSnapshot(),peer.structureCancel::get,p->{})) {
                            prepared.commit(peer.name,structureAuthority());
                        }
                    }
                    structureResult(peer,request,true,action==1?"Structure placement undone":"Structure placed and saved");
                } catch(IOException|RuntimeException failure) {structureResult(peer,request,false,"Structure rejected: "+failure.getMessage());}
                finally {peer.structureActive.set(false);structureOperation.set(false);}
            });
        } catch(IOException|RuntimeException failure) {if(reserved)structureOperation.set(false);throw failure;}
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
        long lastModel, lastCity, lastHistory;
        final java.util.concurrent.atomic.AtomicBoolean historyPending=new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean structureCancel=new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean structureActive=new java.util.concurrent.atomic.AtomicBoolean();
        int lastCraftRequest;
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
                                    case Protocol.MODEL_RESULT,Protocol.STRUCTURE_RESULT -> {
                                        out.writeInt(e.request);
                                        out.writeBoolean(e.accepted);
                                        out.writeUTF(e.name);
                                    }
                                    case Protocol.CRAFT_RESULT -> {
                                        out.writeInt(e.request);
                                        out.writeBoolean(e.accepted);
                                        out.writeUTF(e.name);
                                    }
                                    case Protocol.CITY_STATE, Protocol.CITY_WORLD, Protocol.HISTORY_REPLY,Protocol.STRUCTURE_COMMIT ->
                                            out.write(e.payload);
                                    case Protocol.CITY_RESULT -> out.writeUTF(e.name);
                                    case Protocol.READY -> {}
                                    default -> throw new IOException("Unknown queued event");
                                }
                                out.flush();
                                if(e.type==Protocol.HISTORY_REPLY)historyPending.set(false);
                            }
                        } catch (IOException | InterruptedException e) {
                            if (running && !socket.isClosed() && e instanceof IOException) recordTransportFailure(TransportFailure.WRITE_IO);
                            disconnect();
                        }
                    });
        }

        void disconnect() {
            structureCancel.set(true);
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
        saveWorld(models,edits,false);
        if(save!=null)city.save(citySave);
    }

    private void saveWorld(ModelLibrary models,Map<String,Protocol.Edit> edits,boolean requireAtomic)throws IOException {
        if (save == null) return;
        Path absolute = save.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try (var channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                var out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel), 64 * 1024))) {
            writeWorldPrefix(out,models,edits,()->false);
            writeWorldTail(out);
            out.flush();
            channel.force(true);
        }
        WorldSavePublication.replace(temp, absolute, requireAtomic);
    }

    private void writeWorldPrefix(DataOutputStream out,ModelLibrary models,Map<String,Protocol.Edit> edits,java.util.function.BooleanSupplier cancel)throws IOException {
        StructurePackage.cancelled(cancel);
        out.writeInt(Protocol.MAGIC);
        out.writeInt(models.hasStructureModels()?-9:-8);
        out.writeLong(terrain.seed);
        out.writeInt(terrain.version);
        if(models.hasStructureModels())models.writeAll(out);else models.write(out);
        out.writeInt(edits.size());
        for (var e : edits.values()) {StructurePackage.cancelled(cancel);e.write(out);}
    }
    /** Called under the server authority lock to retain current inventories, health and drops. */
    private void writeWorldTail(DataOutputStream out)throws IOException {
        out.writeInt(inventories.size());
        for (var e : inventories.entrySet()) {
            out.writeUTF(e.getKey());
            e.getValue().write(out);
            out.writeByte(health.getOrDefault(e.getKey(), 20));
        }
        out.writeInt(drops.size());
        for (ItemDrop drop : drops.values()) drop.write(out);
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
        Integer explicitPort = null;
        long seed = Terrain.DEFAULT_SEED;
        boolean cityGame = false, cycle = true;
        double daySeconds = 1200, startHour = 8;
        String atmospherePreset="earth";double atmosphereHaze=1,atmosphereThickness=1;
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
                case "--atmosphere" -> atmospherePreset=args[++i];
                case "--atmosphere-haze" -> atmosphereHaze=Double.parseDouble(args[++i]);
                case "--atmosphere-thickness" -> atmosphereThickness=Double.parseDouble(args[++i]);
                case "--bind" -> bind = args[++i];
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--port" -> explicitPort = Integer.parseInt(args[++i]);
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
        int port = GameIdentity.of(cityGame).launchPort(explicitPort);
        var atmosphere=dev.jayms.net.atmosphere.AtmosphereConfig.preset(atmospherePreset).withHaze(atmosphereHaze).withThickness(atmosphereThickness);
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
                        new GameConfig(cityGame, cityGame && cycle, daySeconds, startHour,atmosphere),
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
