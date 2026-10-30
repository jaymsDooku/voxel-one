package dev.jayms;

/** Synthetic process lifecycle fixture; no game or rendering qualification. */
final class Main {
    public static void main(String[] args) throws Exception {
        var lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getProperty("user.home"), "exit-control.txt"));
        Thread.sleep(Long.parseLong(lines.get(0)));
        System.exit(Integer.parseInt(lines.get(1)));
    }
}
