package dev.jayms.combat.faction;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FactionStore {
    private static final int MAGIC = 0x564F4843; // VOHC
    private static final int VERSION = 1;
    static final int MAX_ACCOUNTS = 4096;
    static final int MAX_DTR = 2;
    static final int MAX_STASH = 12;
    static final long MAX_SAVE_BYTES = 4_000_000;

    record Member(int team, long bannedUntil, long logoutUntil) {
        static final Member NEW = new Member(-1, 0, 0);
    }

    record Team(int dtr, int stash, int captures, long nextRecovery) {
        static final Team NEW = new Team(MAX_DTR, MAX_STASH, 0, 0);
    }

    record State(int season, FactionMatch.Season phase, List<Team> teams, Map<String, Member> members) {
        static State first() {
            return new State(1, FactionMatch.Season.OPENING, List.of(Team.NEW, Team.NEW), Map.of());
        }
    }

    static State read(Path file) throws IOException {
        if (file == null || Files.notExists(file)) return State.first();
        if (Files.size(file) > MAX_SAVE_BYTES) throw new IOException("Oversized faction save");
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION) {
                throw new IOException("Unsupported faction save format");
            }
            int season = in.readInt();
            int phase = in.readUnsignedByte();
            if (season < 1 || phase >= FactionMatch.Season.values().length) {
                throw new IOException("Invalid faction season");
            }
            var first = readTeam(in);
            var second = readTeam(in);
            int count = in.readInt();
            if (count < 0 || count > MAX_ACCOUNTS) throw new IOException("Invalid faction account count");
            var members = new LinkedHashMap<String, Member>();
            for (int i = 0; i < count; i++) {
                String name = in.readUTF();
                int team = in.readByte();
                long bannedUntil = in.readLong();
                long logoutUntil = in.readLong();
                if (!name.matches("[a-z0-9_ ]{1,16}") || name.isBlank() || team < -1 || team > 1
                        || bannedUntil < 0 || logoutUntil < 0 || logoutUntil > 0 && team < 0
                        || members.putIfAbsent(name, new Member(team, bannedUntil, logoutUntil)) != null) {
                    throw new IOException("Invalid faction account state");
                }
            }
            if (in.read() != -1) throw new IOException("Trailing bytes in faction save");
            return new State(season, FactionMatch.Season.values()[phase], List.of(first, second), members);
        }
    }

    private static Team readTeam(DataInputStream in) throws IOException {
        int dtr = in.readByte();
        int stash = in.readUnsignedByte();
        int captures = in.readInt();
        long nextRecovery = in.readLong();
        if (dtr < 0 || dtr > MAX_DTR || stash > MAX_STASH || captures < 0 || nextRecovery < 0
                || dtr < MAX_DTR && nextRecovery == 0 || dtr == MAX_DTR && nextRecovery != 0) {
            throw new IOException("Invalid faction team state");
        }
        return new Team(dtr, stash, captures, nextRecovery);
    }

    static void write(Path target, State state) throws IOException {
        if (target == null) return;
        Path file = target.toAbsolutePath();
        Path directory = file.getParent();
        Files.createDirectories(directory);
        Path pending = Files.createTempFile(directory, ".faction-", ".pending");
        try {
            try (var channel = FileChannel.open(pending, StandardOpenOption.WRITE);
                 var out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)))) {
                out.writeInt(MAGIC);
                out.writeByte(VERSION);
                out.writeInt(state.season());
                out.writeByte(state.phase().ordinal());
                for (Team team : state.teams()) {
                    out.writeByte(team.dtr());
                    out.writeByte(team.stash());
                    out.writeInt(team.captures());
                    out.writeLong(team.nextRecovery());
                }
                out.writeInt(state.members().size());
                for (var account : state.members().entrySet()) {
                    out.writeUTF(account.getKey());
                    out.writeByte(account.getValue().team());
                    out.writeLong(account.getValue().bannedUntil());
                    out.writeLong(account.getValue().logoutUntil());
                }
                out.flush();
                channel.force(true);
            }
            Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    private FactionStore() {}
}
