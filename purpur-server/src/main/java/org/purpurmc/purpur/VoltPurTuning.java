package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.purpurmc.purpur.util.AtomicConfigWriter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VoltPur Hardware-driven Auto-Tuning.
 *
 * Automatically inspects hardware and applies safe, reversible, optimal
 * settings to server.properties and spigot.yml (with backups).
 */
public final class VoltPurTuning {

    private VoltPurTuning() {}

    public static boolean applyServerProperties() {
        return applyServerProperties(Path.of("server.properties"));
    }

    public static boolean applyServerProperties(Path path) {
        if (!VoltPurConfig.hardwareAutoTune) {
            if (Bukkit.getServer() != null) {
                Bukkit.getLogger().info("[VoltPur-Tune] Auto-tune disabled in config - skipping.");
            }
            return false;
        }

        File file = path.toFile();
        if (!file.exists()) return false;

        Map<String, String> updates = new LinkedHashMap<>();
        for (String line : VoltPurHardware.recommendedServerSettings()) {
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if (key.contains(":")) continue;
            if (key.equals("view-distance")
                    || key.equals("simulation-distance")
                    || key.equals("max-players")) {
                updates.put(key, val);
            }
        }

        boolean success = AtomicConfigWriter.writeProperties(path, updates);
        if (success && Bukkit.getServer() != null) {
            Bukkit.getLogger().info("[VoltPur-Tune] Applied hardware-tuned server.properties.");
        }
        return success;
    }

    public static void writeTuningSheet() {
        writeTuningSheet(Path.of("logs", "voltpur-tuning.txt"));
    }

    public static void writeTuningSheet(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("=== VoltPur Hardware Tuning Sheet ===\n");
            for (String s : VoltPurHardware.recommendedServerSettings()) sb.append(s).append('\n');
            sb.append('\n').append("JVM:").append('\n').append(VoltPurHardware.recommendedJvmArgs()).append('\n');
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            if (Bukkit.getServer() != null) {
                Bukkit.getLogger().info("[VoltPur-Tune] Tuning sheet -> " + path);
            }
        } catch (IOException e) {
            if (Bukkit.getServer() != null) {
                Bukkit.getLogger().warning("[VoltPur-Tune] Could not write tuning sheet: " + e.getMessage());
            }
        }
    }

    public static boolean applyOptimizations() {
        return applyOptimizations(Path.of("spigot.yml"));
    }

    public static boolean applyOptimizations(Path path) {
        if (!VoltPurConfig.hardwareAutoTune) return false;
        File spigot = path.toFile();
        if (!spigot.exists()) return false;

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("world-settings.default.ticks-per.hopper-check", "8");
        updates.put("world-settings.default.ticks-per.hopper-transfer", "8");
        updates.put("world-settings.default.merge-radius.item", "4.0");
        updates.put("world-settings.default.merge-radius.exp", "6.0");

        boolean success = AtomicConfigWriter.writeYaml(path, updates);
        if (success && Bukkit.getServer() != null) {
            Bukkit.getLogger().info("[VoltPur-Tune] Applied spigot.yml optimizations (hopper-check=8, merge-radius item=4.0 exp=6.0).");
        }
        return success;
    }

    public static void onServerStart() {
        boolean applied = applyServerProperties();
        boolean opt = applyOptimizations();
        writeTuningSheet();
        if (Bukkit.getServer() != null) {
            Bukkit.getLogger().info("[VoltPur-Tune] Server start tuning complete (server.properties=" + applied + ", hopper-opt=" + opt + ").");
        }
    }
}
