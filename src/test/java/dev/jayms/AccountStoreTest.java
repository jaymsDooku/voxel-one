package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.AccountStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;

class AccountStoreTest {
    @TempDir Path temp;

    @Test
    void persistsSaltedHashesAndValidatesCredentials() throws Exception {
        Path file = temp.resolve("accounts.db");
        var store = new AccountStore(file);
        assertTrue(store.register("jayms", "secret-password-123".toCharArray()));
        assertTrue(store.register("another", "secret-password-123".toCharArray()));
        assertFalse(store.register("JAYMS", "different-password".toCharArray()));
        assertFalse(Files.readString(file).contains("secret-password-123"));
        java.util.Properties hashes = new java.util.Properties();
        try (var in = Files.newInputStream(file)) {
            hashes.load(in);
        }
        assertNotEquals(hashes.getProperty("jayms"), hashes.getProperty("another"));
        store = new AccountStore(file);
        assertTrue(store.authenticate("JAYMS", "secret-password-123".toCharArray()));
        assertFalse(store.authenticate("jayms", "wrong-password".toCharArray()));
        assertFalse(store.authenticate("missing", "secret-password-123".toCharArray()));
    }

    @Test
    void rejectsInvalidNamesAndShortPasswords() throws Exception {
        var store = new AccountStore(null);
        assertThrows(
                IOException.class,
                () -> store.register("bad name", "secret-password".toCharArray()));
        assertThrows(IOException.class, () -> store.register("jayms", "short".toCharArray()));
    }
}
