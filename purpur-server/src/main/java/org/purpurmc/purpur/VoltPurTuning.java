package org.purpurmc.purpur;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * VoltPur Tuning - applies hardware-derived settings to the running server.
 *
 * This is strictly opt-in (see voltpur.yml -> modules.hardware.auto-tune).
 * It only writes `server.properties` keys that are safe to change at runtime
 * (view-distance, simulation-distance, max-players) and logs recommended
 * Paper thread settings for the admin to apply in paper.yml.
 */
public final class VoltPurTuning {

    private VoltPurTuning() {}

    /** Returns true if tuning was applied. Never throws (safe by default). */
    public static boolean applyServerProperties() {
        if (!VoltPurConfig.hardwareAutoTune) {
            Bukkit.getLogger().info("[VoltPur-Tune] Auto-tune disabled in config - skipping.");
            return false;
        }
        try {
            File file = new File("server.properties");
            Properties props = new Properties();
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) { props.load(fis); }
            }

            // Read recommended values from the hardware detector
            String[] recommended = new String[0];
            for (String line : VoltPurHardware.recommendedServerSettings()) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                // Only touch plain keys we own; skip namespaced "paper:"/"spigot:" hints
                if (key.contains(":")) continue;
                if (key.equals("view-distance")
                        || key.equals("simulation-distance")
                        || key.equals("max-players")) {
                    props.setProperty(key, val);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                props.store(fos, "VoltPur auto-tuned from hardware profile");
            }
            Bukkit.getLogger().info("[VoltPur-Tune] Applied hardware-tuned server.properties (view-distance/simulation-distance/max-players).");
            return true;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[VoltPur-Tune] Could not apply tuning: " + e.getMessage());
            return false;
        }
    }

    /** Writes a human-readable tuning sheet for the admin (paper.yml / spigot.yml hints). */
    public static void writeTuningSheet() {
        try {
            java.nio.file.Path path = java.nio.file.Path.of("logs", "voltpur-tuning.txt");
            java.nio.file.Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("=== VoltPur Hardware Tuning Sheet ===\n");
            for (String s : VoltPurHardware.recommendedServerSettings()) sb.append(s).append('\n');
            sb.append('\n').append("JVM:").append('\n').append(VoltPurHardware.recommendedJvmArgs()).append('\n');
            java.nio.file.Files.write(path, sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Bukkit.getLogger().info("[VoltPur-Tune] Tuning sheet -> logs/voltpur-tuning.txt");
        } catch (IOException e) {
            Bukkit.getLogger().warning("[VoltPur-Tune] Could not write tuning sheet: " + e.getMessage());
        }
    }

    public static void onServerStart() {
        boolean applied = applyServerProperties();
        writeTuningSheet();
        Bukkit.getLogger().info("[VoltPur-Tune] Server start tuning complete (applied=" + applied + ").");
    }
}
