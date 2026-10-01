package dev.jayms.net;

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
    public final String username;
    public final Protocol.Pose spawn;
    public final long seed;
    public final ModelLibrary models;
    public String modelMessage = "";
    public int modelResults;
    public Inventory inventory;
    public int health = 20;
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
        socket = SecureTransport.connect(host, port, fingerprint);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        try {
            out.writeInt(Protocol.MAGIC);
            out.writeInt(Protocol.VERSION);
            out.writeByte(register ? Protocol.REGISTER : Protocol.LOGIN);
            out.writeUTF(username);
            out.writeUTF(new String(password));
            out.flush();
            if (in.readInt() != Protocol.MAGIC || in.readInt() != Protocol.VERSION)
                throw new IOException("Server protocol mismatch");
            boolean success = in.readBoolean();
            String message = Protocol.readText(in, 256);
            if (!success) throw new IOException(message);
            id = in.readInt();
            spawn = Protocol.Pose.read(in);
            this.username = Protocol.readText(in, 16);
            seed = in.readLong();
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
                if (type == Protocol.MOVE) {
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
