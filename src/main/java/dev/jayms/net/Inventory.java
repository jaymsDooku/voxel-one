package dev.jayms.net;

import java.io.*;

/** Exactly 36 slots: slots 0..8 are the hotbar. No client supplies item counts. */
public final class Inventory {
    public static final int SIZE = 36, HOTBAR = 9, STACK = 64;
    private final int[] types = new int[SIZE], counts = new int[SIZE];

    public int type(int slot) {
        return types[slot];
    }

    public int count(int slot) {
        return counts[slot];
    }

    public int add(int type, int amount) {
        if (!Blocks.valid(type) || type == 0 || amount < 0) throw new IllegalArgumentException();
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < SIZE && amount > 0; i++)
                if (pass == 0 ? types[i] == type : counts[i] == 0) {
                    int n = Math.min(STACK - counts[i], amount);
                    types[i] = type;
                    counts[i] += n;
                    amount -= n;
                }
        return amount;
    }

    public boolean hasSpace(int type) {
        for (int i = 0; i < SIZE; i++)
            if (counts[i] == 0 || types[i] == type && counts[i] < STACK) return true;
        return false;
    }

    public boolean take(int slot, int type) {
        if (slot < 0 || slot >= HOTBAR || types[slot] != type || counts[slot] == 0) return false;
        if (--counts[slot] == 0) types[slot] = 0;
        return true;
    }

    public void swap(int a, int b) {
        if (a < 0 || b < 0 || a >= SIZE || b >= SIZE) return;
        if (a == b) return;
        if (types[a] != 0 && types[a] == types[b]) {
            int n = Math.min(STACK - counts[b], counts[a]);
            counts[b] += n;
            counts[a] -= n;
            if (counts[a] == 0) types[a] = 0;
        } else {
            int t = types[a], n = counts[a];
            types[a] = types[b];
            counts[a] = counts[b];
            types[b] = t;
            counts[b] = n;
        }
    }

    public void write(DataOutputStream out) throws IOException {
        for (int i = 0; i < SIZE; i++) {
            out.writeByte(types[i]);
            out.writeByte(counts[i]);
        }
    }

    public static Inventory read(DataInputStream in) throws IOException {
        Inventory inventory = new Inventory();
        for (int i = 0; i < SIZE; i++) {
            int t = in.readUnsignedByte(), n = in.readUnsignedByte();
            if (!Blocks.valid(t) || n > STACK || (t == 0) != (n == 0))
                throw new IOException("Invalid inventory");
            inventory.types[i] = t;
            inventory.counts[i] = n;
        }
        return inventory;
    }

    public Inventory copy() {
        Inventory r = new Inventory();
        System.arraycopy(types, 0, r.types, 0, SIZE);
        System.arraycopy(counts, 0, r.counts, 0, SIZE);
        return r;
    }
}
