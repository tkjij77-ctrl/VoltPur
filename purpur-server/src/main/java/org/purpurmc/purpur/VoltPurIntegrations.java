package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.purpurmc.purpur.util.VoltPurLogger;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

/**
 * VoltPur Integrations - detects external drop-in optimization jars (FerriteCore, C2ME)
 * and manages background world pregeneration tasks.
 */
public final class VoltPurIntegrations {

    private static final AtomicLong chunksGenerated = new AtomicLong(0);

    private VoltPurIntegrations() {}

    public static void detectExternalOptimizations() {
        File[] scanDirs = new File[] { new File("plugin-pro"), new File("plugins") };
        boolean foundFerriteCore = false;
        boolean foundC2ME = false;

        for (File dir : scanDirs) {
            if (!dir.exists() || !dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".jar")) {
                    if (name.contains("ferritecore")) {
                        foundFerriteCore = true;
                    }
                    if (name.contains("c2me")) {
                        foundC2ME = true;
                    }
                }
            }
        }

        if (foundFerriteCore) {
            VoltPurModules.setStatus("MemoryOptimization", VoltPurModules.Status.ACTIVE);
            VoltPurLogger.info("VoltPur", "Detected external FerriteCore optimization jar; MemoryOptimization marked ACTIVE.");
        }
        if (foundC2ME) {
            VoltPurModules.setStatus("ChunkLoading", VoltPurModules.Status.ACTIVE);
            VoltPurLogger.info("VoltPur", "Detected external C2ME optimization jar; ChunkLoading marked ACTIVE.");
        }
    }

    public static void tickPregen() {
        if (!VoltPurConfig.pregenEnabled) {
            return;
        }

        try {
            if (Bukkit.getServer() != null && Bukkit.getOnlinePlayers() != null) {
                if (Bukkit.getOnlinePlayers().size() > VoltPurConfig.pregenMaxPlayers) {
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static long getChunksGenerated() {
        return chunksGenerated.get();
    }

    public static void resetForTesting() {
        chunksGenerated.set(0);
    }
}
