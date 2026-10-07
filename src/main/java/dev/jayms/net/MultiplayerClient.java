package dev.jayms.net;

import dev.jayms.net.city.*;
import dev.jayms.net.model.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Socket threads queue events; the game thread alone owns players and block prediction. */
public final class MultiplayerClient implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    public final int id;
    public final int serverProtocol;
    public final String username;
    public final Protocol.Pose spawn;
    public final long seed;
    public final int generatorVersion;
    public final ModelLibrary models;
    public String modelMessage = "";
    public int modelResults;
    public Inventory inventory;
    public int health = 20;
    public CityFrame city;
    public long cityReceived;
    public final Map<Integer, RemotePlayer> citizens = new HashMap<>();
    public final Map<Integer, RemotePlayer> horses = new HashMap<>();
    public final Map<Integer, RemotePlayer> cows = new HashMap<>();
    public final Map<Integer, ItemDrop> drops = new LinkedHashMap<>();
    public Protocol.Pose respawn;
    public final List<Protocol.Edit> initialEdits = new ArrayList<>();
    public final Map<Integer, Protocol.Pose> players = new HashMap<>();
    public final Map<Integer, String> names = new HashMap<>();
    public final Map<Integer, RemotePlayer> remotePlayers = new HashMap<>();
    private final BlockingQueue<Runnable> events = new ArrayBlockingQueue<>(4096);
    private final BlockingQueue<Runnable> outgoing = new ArrayBlockingQueue<>(128);
    private final Map<Integer, Protocol.Edit> pending = new HashMap<>();
    private int nextRequest;
    private volatile boolean connected = true;
    private volatile String error;
    private String notice = "";
    private final List<Protocol.Edit> pendingEdits = new ArrayList<>();

    public MultiplayerClient(
            String host,
            int port,
            String username,
            char[] password,
            boolean register,
            String fingerprint)
            throws IOException {
        Connection connection = authenticate(host, port, username, password, register, fingerprint);
        socket = connection.socket();
        in = connection.in();
        out = connection.out();
        serverProtocol = connection.version();
        try {
            id = in.readInt();
            spawn = Protocol.Pose.read(in);
            this.username = Protocol.readText(in, 16);
            seed = in.readLong();
            generatorVersion = serverProtocol >= 17 ? in.readInt() : Terrain.LEGACY_VERSION;
            try {
                new Terrain(seed, generatorVersion);
            } catch (IllegalArgumentException e) {
                throw new IOException("Unsupported terrain generator", e);
            }
            models = ModelLibrary.read(in);
            inventory = Inventory.read(in);
            health = in.readUnsignedByte();
            int count = in.readInt();
            if (count < 0 || count > 2000000) throw new IOException("Invalid world snapshot");
            for (int i = 0; i < count; i++) {
                var e = Protocol.Edit.read(in);
                if (!e.valid() || !models.has(e.type()))
                    throw new IOException("Invalid snapshot block");
                initialEdits.add(e);
            }
            count = in.readInt();
            if (count < 0 || count > 32) throw new IOException("Invalid player snapshot");
            for (int i = 0; i < count; i++) {
                var p = Protocol.Pose.read(in);
                String name = Protocol.readText(in, 16);
                join(p, name);
            }
            count = in.readInt();
            if (count < 0 || count > 100000) throw new IOException("Invalid item snapshot");
            for (int i = 0; i < count; i++) {
                ItemDrop drop = ItemDrop.read(in);
                if (drop.count() > 0) drops.put(drop.id(), drop);
            }
            acceptCity(CityFrame.read(in, serverProtocol < 16 ? 6 : serverProtocol < 19 ? 8 : serverProtocol < 20 ? 9 : serverProtocol < 21 ? 10 : serverProtocol < 22 ? 11 : serverProtocol < 24 ? 12 : serverProtocol < 25 ? 13 : serverProtocol < 26 ? 14 : serverProtocol < 27 ? 15 : 16), System.nanoTime());
            out.writeByte(Protocol.READY);
            out.flush();
            socket.setSoTimeout(0);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        Thread reader = new Thread(this::read, "voxel-network-reader");
        reader.setDaemon(true);
        reader.start();
        Thread writer =
                new Thread(
                        () -> {
                            try {
                                while (connected) {
                                    Runnable task = outgoing.poll(1, TimeUnit.SECONDS);
                                    if (task != null) task.run();
                                }
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                        },
                        "voxel-network-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private record Connection(
            Socket socket, DataInputStream in, DataOutputStream out, int version) {}

    private static Connection authenticate(
            String host, int port, String username, char[] password, boolean register,
            String fingerprint) throws IOException {
        int requested = Protocol.VERSION;
        for (;;) {
            Socket socket = SecureTransport.connect(host, port, fingerprint);
            try {
                var in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                out.writeInt(Protocol.MAGIC);
                out.writeInt(requested);
                out.writeByte(register ? Protocol.REGISTER : Protocol.LOGIN);
                out.writeUTF(username);
                out.writeUTF(new String(password));
                out.flush();
                int magic = in.readInt();
                int version = in.readInt();
                if (magic != Protocol.MAGIC) throw new IOException("Invalid server protocol header");
                // Protocols 14/15 use frame format 6; reviewed aviation protocol 22 uses format 12.
                // Reconnect because old servers close on mismatch.
                if (requested == Protocol.VERSION && version >= Protocol.CITY_BASE_VERSION
                        && (version <= Protocol.SPECIAL_BUILDINGS_VERSION || version == 22)) {
                    if (in.readBoolean()) throw new IOException("Unexpected protocol acceptance");
                    Protocol.readText(in, 256);
                    socket.close();
                    requested = version;
                    continue;
                }
                if (version != requested)
                    throw new IOException("Server protocol mismatch (client " + requested
                            + ", server " + version + "). "
                            + (version > Protocol.VERSION
                                    ? "Restart the launcher to update the client."
                                    : "The server needs an update."));
                boolean success = in.readBoolean();
                String message = Protocol.readText(in, 256);
                if (!success) throw new IOException(message);
                return new Connection(socket, in, out, version);
            } catch (IOException e) {
                socket.close();
                throw e;
            }
        }
    }

    private void acceptCity(CityFrame frame, long time) {
        city = frame;
        cityReceived = time;
        for (var c : frame.visibleCitizens())
            citizens.computeIfAbsent(c.id(), id -> new RemotePlayer(c.name()))
                    .accept(
                            new Protocol.Pose(
                                    c.id(),
                                    c.x(),
                                    c.y(),
                                    c.z(),
                                    c.yaw(),
                                    0,
                                    c.phase(),
                                    c.activity().startsWith("Commuting")
                                                    || c.activity().startsWith("Going")
                                                    || c.activity().startsWith("Buying")
                                            ? 1
                                            : 0,
                                    false),
                            time);
        cows.keySet()
                .removeIf(id -> frame.agriculture().cows().stream().noneMatch(c -> c.id() == id));
        for (var c : frame.agriculture().cows())
            cows.computeIfAbsent(c.id(), id -> new RemotePlayer("Cow"))
                    .accept(
                            new Protocol.Pose(
                                    c.id(), c.x(), c.y(), c.z(), c.yaw(), 0, c.phase(), 1, false),
                            time);
        for (var h : frame.horses())
            horses.computeIfAbsent(h.id(), id -> new RemotePlayer("Horse"))
                    .accept(
                            new Protocol.Pose(
                                    h.id(),
                                    h.x(),
                                    h.y(),
                                    h.z(),
                                    h.yaw(),
                                    0,
                                    h.phase(),
                                    h.rider() != 0 ? 1 : 0,
                                    false),
                            time);
    }

    private final java.util.Queue<java.util.function.Consumer<String>> cityResults = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public boolean cityCommand(CityCommand command) { return cityCommand(command, null); }

    public synchronized boolean cityCommand(CityCommand command, java.util.function.Consumer<String> completion) {
        if (serverProtocol < 27 && (command.kind() == CityCommand.PARCEL
                || command.kind() == CityCommand.ZONE && command.value() >= 4)) {
            notice = "Parcel layouts require a server update.";
            return false;
        }
        if (serverProtocol < 25 && (command.kind() == CityCommand.RAIL || command.kind() == CityCommand.SPECIAL
                && (command.value() == SpecialBuildings.RAIL_STATION || command.value() == SpecialBuildings.RAIL_DEPOT))) {
            notice = "Railways require a server update.";
            return false;
        }
        if (serverProtocol < 23 && command.kind() == CityCommand.SPECIAL
                && command.value() == SpecialBuildings.PORT) {
            notice = "Coastal ports require a server update.";
            return false;
        }
        if (serverProtocol < 24 && (command.kind()==CityCommand.EDIT_ROAD || command.kind()==CityCommand.DELETE_ROAD)) {
            notice = "Road section actions require a server update.";
            return false;
        }
        if (serverProtocol < 21 && (command.kind() == CityCommand.SETTLE_DISTRICT
                || command.kind() == CityCommand.FOCUS_DISTRICT)) {
            notice = "District commands require a server update.";
            return false;
        }
        if (serverProtocol < 22 && ((command.kind() == CityCommand.RUNWAY || command.kind() == CityCommand.FLIGHT)
                || command.kind() == CityCommand.SPECIAL && command.value() == SpecialBuildings.AIRPORT)) {
            notice = "Airports require a server update.";
            return false;
        }
        if (serverProtocol < 18 && (command.kind() == CityCommand.CAPITAL
                || command.kind() == CityCommand.EXCHANGE
                || command.kind() == CityCommand.SPECIAL && command.value() == SpecialBuildings.EXCHANGE)) {
            notice = "Capital commands require a server update.";
            return false;
        }
        if (command.kind() == CityCommand.SPECIAL && serverProtocol < Protocol.SPECIAL_BUILDINGS_VERSION) {
            notice = "Special buildings require a server update.";
            return false;
        }
        java.util.function.Consumer<String> pending = result -> { if (completion != null) completion.accept(result); };
        cityResults.add(pending);
        boolean sent = send(
                () -> {
                    out.writeByte(Protocol.CITY_COMMAND);
                    command.write(out);
                    out.flush();
                });
        if (!sent) cityResults.remove(pending);
        return sent;
    }

    private void join(Protocol.Pose p, String name) {
        if (p.id() == id) return;
        players.put(p.id(), p);
        names.put(p.id(), name);
        remotePlayers
                .computeIfAbsent(p.id(), ignored -> new RemotePlayer(name))
                .accept(p, System.nanoTime());
    }

    private void read() {
        try {
            while (connected) {
                int type = in.readUnsignedByte();
                Runnable event;
                if (type == Protocol.CITY_STATE) {
                    var state = CityFrame.read(in, serverProtocol < 16 ? 6 : serverProtocol < 19 ? 8 : serverProtocol < 20 ? 9 : serverProtocol < 21 ? 10 : serverProtocol < 22 ? 11 : serverProtocol < 24 ? 12 : serverProtocol < 25 ? 13 : serverProtocol < 26 ? 14 : serverProtocol < 27 ? 15 : 16);
                    long time = System.nanoTime();
                    event = () -> acceptCity(state, time);
                } else if (type == Protocol.CITY_WORLD) {
                    int n = in.readInt();
                    if (n < 0 || n > 8192) throw new IOException("Invalid city edit batch");
                    var batch = new ArrayList<Protocol.Edit>();
                    for (int i = 0; i < n; i++) {
                        var edit = Protocol.Edit.read(in);
                        if (!edit.valid() || !models.has(edit.type()))
                            throw new IOException("Invalid city edit");
                        batch.add(edit);
                    }
                    event = () -> pendingEdits.addAll(batch);
                } else if (type == Protocol.CITY_RESULT) {
                    String message = Protocol.readText(in, 512);
                    event = () -> {
                        notice = message;
                        var completion = cityResults.poll();
                        if (completion != null) completion.accept(message);
                    };
                } else if (type == Protocol.MOVE) {
                    var p = Protocol.Pose.read(in);
                    long received = System.nanoTime();
                    event =
                            () -> {
                                if (p.id() != id) {
                                    players.put(p.id(), p);
                                    var remote = remotePlayers.get(p.id());
                                    if (remote != null) remote.accept(p, received);
                                }
                            };
                } else if (type == Protocol.JOIN) {
                    var p = Protocol.Pose.read(in);
                    String name = Protocol.readText(in, 16);
                    event = () -> join(p, name);
                } else if (type == Protocol.BLOCK) {
                    var e = Protocol.Edit.read(in);
                    event = () -> pendingEdits.add(e);
                } else if (type == Protocol.EDIT_RESULT) {
                    int request = in.readInt();
                    boolean accepted = in.readBoolean();
                    var e = Protocol.Edit.read(in);
                    var state = Protocol.CellState.read(in);
                    event =
                            () -> {
                                pending.remove(request);
                                pendingEdits.addAll(state.edits());
                                if (!accepted)
                                    notice =
                                            "Placement rejected: occupied block, player overlap, or"
                                                    + " out of reach.";
                            };
                } else if (type == Protocol.INVENTORY) {
                    Inventory state = Inventory.read(in);
                    int hp = in.readUnsignedByte();
                    if (hp > 20) throw new IOException("Invalid health");
                    event =
                            () -> {
                                inventory = state;
                                health = hp;
                            };
                } else if (type == Protocol.DROP) {
                    ItemDrop d = ItemDrop.read(in);
                    event =
                            () -> {
                                if (d.count() == 0) drops.remove(d.id());
                                else drops.put(d.id(), d);
                            };
                } else if (type == Protocol.RESPAWN) {
                    var p = Protocol.Pose.read(in);
                    event = () -> respawn = p;
                } else if (type == Protocol.MODEL_DEFINE) {
                    var model = ModelLibrary.Entry.read(in);
                    event =
                            () -> {
                                try {
                                    models.accept(model);
                                } catch (IOException e) {
                                    error = e.getMessage();
                                    close();
                                }
                            };
                } else if (type == Protocol.CRAFT_RESULT) {
                    in.readBoolean();
                    String message = Protocol.readText(in, 512);
                    event = () -> notice = message;
                } else if (type == Protocol.MODEL_RESULT) {
                    in.readInt();
                    in.readBoolean();
                    String message = Protocol.readText(in, 256);
                    event =
                            () -> {
                                modelMessage = message;
                                modelResults++;
                            };
                } else if (type == Protocol.READY) {
                    event =
                            () -> {
                                players.clear();
                                names.clear();
                                remotePlayers.clear();
                            };
                } else if (type == Protocol.LEAVE) {
                    int leaving = in.readInt();
                    event =
                            () -> {
                                players.remove(leaving);
                                names.remove(leaving);
                                remotePlayers.remove(leaving);
                            };
                } else throw new IOException("Unknown server message");
                if (!events.offer(event)) throw new IOException("Incoming queue full");
            }
        } catch (IOException e) {
            if (connected)
                error =
                        "Disconnected: "
                                + (e.getMessage() == null ? "server closed" : e.getMessage());
            close();
        }
    }

    public List<Protocol.Edit> poll() {
        Runnable event;
        while ((event = events.poll()) != null) event.run();
        if (!connected) {
            java.util.function.Consumer<String> completion;
            while ((completion = cityResults.poll()) != null) completion.accept("Disconnected: road placement not confirmed");
        }
        var result = new ArrayList<>(pendingEdits);
        pendingEdits.clear();
        return result;
    }

    public boolean connected() {
        return connected;
    }

    public boolean pending(Protocol.Edit edit) {
        return pending.values().stream().anyMatch(p -> p.key().equals(edit.key()));
    }

    public String notice() {
        return notice;
    }

    public String status() {
        return connected
                ? username + " | " + (players.size() + 1) + " players"
                : error == null ? "Disconnected" : error;
    }

    public void move(Protocol.Pose p) {
        send(
                () -> {
                    out.writeByte(Protocol.MOVE);
                    p.write(out);
                    out.flush();
                });
    }

    public boolean createModel(ModelDefinition model) {
        int request = ++nextRequest;
        return send(
                () -> {
                    out.writeByte(Protocol.MODEL_CREATE);
                    out.writeInt(request);
                    model.write(out);
                    out.flush();
                });
    }

    public void swap(int a, int b) {
        send(
                () -> {
                    out.writeByte(Protocol.SWAP);
                    out.writeByte(a);
                    out.writeByte(b);
                    out.flush();
                });
    }

    public boolean craft(int recipe, Protocol.Pose pose) {
        return send(
                () -> {
                    out.writeByte(Protocol.CRAFT);
                    out.writeInt(recipe);
                    pose.write(out);
                    out.flush();
                });
    }

    public boolean edit(Protocol.Edit e, Protocol.Pose pose) {
        return edit(e, pose, 0);
    }

    public boolean edit(Protocol.Edit e, Protocol.Pose currentPose, int slot) {
        if (!connected || pending(e)) return false;
        int request = ++nextRequest;
        pending.put(request, e);
        boolean queued =
                send(
                        () -> {
                            out.writeByte(Protocol.BLOCK);
                            new Protocol.BlockRequest(request, currentPose, e, slot).write(out);
                            out.flush();
                        });
        if (!queued) pending.remove(request);
        return queued;
    }

    private interface Write {
        void run() throws IOException;
    }

    private boolean send(Write write) {
        if (!connected) return false;
        if (!outgoing.offer(
                () -> {
                    try {
                        write.run();
                    } catch (IOException e) {
                        error = "Disconnected: " + e.getMessage();
                        close();
                    }
                })) {
            error = "Disconnected: outgoing queue full";
            close();
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        connected = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
