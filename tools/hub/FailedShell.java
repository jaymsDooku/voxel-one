package dev.jayms.launcher;

/** A valid executable shell JAR that fails before acknowledging startup. Synthetic tests only. */
final class Launcher {
    public static void main(String[] args) { System.exit(7); }
}
