package dev.jayms;

/** Synthetic command receiver for catalog selection tests. This is not a game client. */
final class Main {
    public static void main(String[] args) throws Exception {
        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("user.home"), "promotion-client-command.txt"),
                "command-fixture\n" + String.join("\n", args) + "\n", java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
