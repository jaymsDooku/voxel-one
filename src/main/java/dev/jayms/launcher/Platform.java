package dev.jayms.launcher;

import java.util.Locale;

public enum Platform {
    WINDOWS("windows"),
    LINUX("linux"),
    MAC_INTEL("mac-intel"),
    MAC_ARM("mac-arm");
    private final String id;

    Platform(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Platform current() {
        return detect(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    public static Platform detect(String os, String arch) {
        os = os.toLowerCase(Locale.ROOT);
        arch = arch.toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin"))
            return arch.equals("aarch64") || arch.equals("arm64") ? MAC_ARM : MAC_INTEL;
        if (!arch.equals("amd64") && !arch.equals("x86_64"))
            throw new IllegalArgumentException(
                    "Voxel One requires a 64-bit x86 Windows/Linux desktop or a Mac.");
        if (os.contains("windows")) return WINDOWS;
        if (os.contains("linux")) return LINUX;
        throw new IllegalArgumentException("Unsupported desktop: " + os);
    }
}
