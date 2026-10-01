package dev.jayms.net;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted password hashes persisted atomically; usernames are case insensitive. */
public final class AccountStore {
    private static final int ITERATIONS = 600_000;
    private final Path file;
    private final Properties accounts = new Properties();
    private final SecureRandom random = new SecureRandom();
    private final byte[] dummySalt = new byte[16];

    public AccountStore(Path file) throws IOException {
        this.file = file;
        random.nextBytes(dummySalt);
        if (file != null && Files.exists(file))
            try (var in = Files.newInputStream(file)) {
                accounts.load(in);
            }
    }

    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    public boolean register(String name, char[] password) throws IOException {
        if (!validName(name) || password.length < 10 || password.length > 128)
            throw new IOException("Use a 3-16 character username and a 10-128 character password.");
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String value =
                ITERATIONS
                        + ":"
                        + Base64.getEncoder().encodeToString(salt)
                        + ":"
                        + Base64.getEncoder().encodeToString(hash(password, salt, ITERATIONS));
        synchronized (this) {
            String key = normalize(name);
            if (accounts.containsKey(key)) return false;
            if (accounts.size() >= 10000) throw new IOException("Account limit reached");
            accounts.setProperty(key, value);
            try {
                persist();
            } catch (IOException e) {
                accounts.remove(key);
                throw e;
            }
            return true;
        }
    }

    public boolean authenticate(String name, char[] password) throws IOException {
        if (!validName(name) || password.length > 128) return false;
        String value;
        synchronized (this) {
            value = accounts.getProperty(normalize(name));
        }
        if (value == null) {
            hash(password, dummySalt, ITERATIONS);
            return false;
        }
        try {
            String[] fields = value.split(":");
            int rounds = Integer.parseInt(fields[0]);
            if (rounds < 100000 || rounds > 2000000) throw new IOException("Invalid account hash");
            return MessageDigest.isEqual(
                    Base64.getDecoder().decode(fields[2]),
                    hash(password, Base64.getDecoder().decode(fields[1]), rounds));
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw new IOException("Invalid account store", e);
        }
    }

    public synchronized boolean contains(String name) {
        return accounts.containsKey(normalize(name));
    }

    private byte[] hash(char[] password, byte[] salt, int rounds) throws IOException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IOException("Password hashing unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    private void persist() throws IOException {
        if (file == null) return;
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temp = Files.createTempFile(absolute.getParent(), "accounts-", ".tmp");
        try {
            privateFile(temp);
            try (var out = Files.newOutputStream(temp)) {
                accounts.store(out, "Voxel One password hashes");
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
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static void privateFile(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            /* Windows uses the user's directory ACL. */
        }
    }
}
