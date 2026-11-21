package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.net.SecureTransport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;

/** The render thread never waits for a server snapshot or a socket write. */
public final class PracticeClient implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ArrayBlockingQueue<Command> commands = new ArrayBlockingQueue<>(20);
    private final Thread reader, writer;
    private volatile PracticeMatch.View view;
    private final int id;
    private final int version;

    @FunctionalInterface private interface Command { void write(DataOutputStream out) throws IOException; }

    public PracticeClient(String host, int port, String pin, String name) throws IOException {
        this(host, port, pin, name, null, false);
    }

    public PracticeClient(String host, int port, String pin, String name, char[] password, boolean register)
            throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_ ]{1,16}") || name.isBlank())
            throw new IOException("Use a practice name of 1–16 letters, numbers, spaces, or underscores");
        if (password != null && (password.length < 10 || password.length > 128))
            throw new IOException("Use a faction password of 10–128 characters");
        Login login;
        int negotiated = PracticeWire.VERSION;
        for (;;) {
            try {
                login = login(host, port, pin, name, password, register, negotiated);
                break;
            } catch (UnsupportedVersion olderServer) {
                if (negotiated == PracticeWire.LEGACY_VERSION) throw olderServer;
                negotiated--;
            }
        }
        version = negotiated;
        socket = login.socket();
        in = login.in();
        out = login.out();
        id = login.id();
        view = login.view();
        reader = thread("practice-snapshot-reader", this::read);
        writer = thread("practice-intent-writer", this::write);
        reader.start();
        writer.start();
    }

    private record Login(Socket socket, DataInputStream in, DataOutputStream out, int id, PracticeMatch.View view) { }

    private static final class UnsupportedVersion extends EOFException {
        UnsupportedVersion() { super("Arena server closed during version handshake"); }
    }

    private static Login login(String host, int port, String pin, String name, char[] password,
                               boolean register, int version) throws IOException {
        Socket socket = SecureTransport.connect(host, port, pin);
        try {
            var in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            byte[] encoded = name.getBytes(StandardCharsets.US_ASCII);
            out.writeInt(PracticeWire.MAGIC);
            out.writeInt(version);
            out.writeByte(encoded.length);
            out.write(encoded);
            out.writeByte(password == null ? 0 : register ? 2 : 1);
            if (password != null) {
                out.writeByte(password.length);
                for (char c : password) out.writeChar(c);
            }
            out.flush();
            int id;
            try {
                id = in.readInt();
            } catch (EOFException unsupported) {
                throw new UnsupportedVersion();
            }
            if (id == -1) throw new IOException("Arena sign-in rejected. Check credentials and server mode.");
            if (id == -2) throw new IOException("Update Voxel One to join this practice mode.");
            if (id <= 0) throw new IOException("Practice lobby is full or that name is in use");
            return new Login(socket, in, out, id, PracticeWire.readView(in, version));
        } catch (IOException | RuntimeException failed) {
            socket.close();
            throw failed;
        }
    }

    public int id() { return id; }
    public PracticeMatch.View view() { return view; }
    public boolean connected() { return !socket.isClosed(); }
    public boolean input(Intent intent) { return offer(out -> PracticeWire.writeIntent(out, intent, version)); }
    public boolean ready() { return offer(out -> out.writeByte(PracticeWire.READY)); }
    public boolean action(int action) {
        if (action < 0 || action > 255) return false;
        return offer(out -> { out.writeByte(PracticeWire.ACTION); out.writeByte(action); });
    }
    public boolean swap(int first, int second) {
        if (first < 0 || first >= 36 || second < 0 || second >= 36) return false;
        return inventoryCommand(first, second);
    }

    public boolean quickMove(int source) {
        return source >= 0 && source < 36 && inventoryCommand(source, PracticeWire.QUICK_MOVE);
    }

    private boolean inventoryCommand(int first, int second) {
        return offer(out -> {
            out.writeByte(PracticeWire.SWAP);
            out.writeByte(first);
            out.writeByte(second);
        });
    }

    private boolean offer(Command command) { return connected() && commands.offer(command); }

    private void read() {
        try {
            while (connected()) view = PracticeWire.readView(in, version);
        } catch (IOException | IllegalArgumentException closed) {
            close();
        }
    }

    private void write() {
        try {
            while (connected()) {
                commands.take().write(out);
                out.flush();
            }
        } catch (IOException stopped) {
            close();
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    @Override public void close() {
        try { socket.close(); } catch (IOException ignored) { }
        reader.interrupt();
        writer.interrupt();
    }

    private static Thread thread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }
}
