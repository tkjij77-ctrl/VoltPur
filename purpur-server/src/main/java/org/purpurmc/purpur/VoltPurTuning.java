package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * VoltPur Tuning - opt-in, reversible, comment-preserving file edits.
 *
 * HARDENING CHANGES:
 *  1. BACKUPS: every file is copied to <file>.bak-<timestamp> before being touched
 *     (the previous version silently rewrote server.properties with no way back).
 *  2. COMMENTS PRESERVED: server.properties is edited line by line, so comments,
 *     ordering and unknown keys survive. Properties.store() - used before - wipes
 *     all of them.
 *  3. HONEST MESSAGES: these files are read by the server at STARTUP only, so the
 *     log says "written - restart required" instead of claiming the change is
 *     already in effect.
 *  4. Only opt-in settings are written, and /voltpur optimize refuses to run at all
 *     unless modules.hardware.auto-tune=true.
 */
public final class VoltPurTuning {

    private static final String MODULE = "HardwareAutoTune";
    private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyyMMdd-HHmmss");

    private VoltPurTuning() {}

    /** Writes view-distance / simulation-distance / max-players from the hardware profile. */
    public static boolean applyServerProperties() {
        if (!VoltPurConfig.hardwareAutoTune) {
            Bukkit.getLogger().info("[VoltPur-Tune] Auto-tune is disabled (modules.hardware.auto-tune=false) - nothing written.");
            return false;
        }
        File file = new File("server.properties");
        if (!file.isFile()) {
            Bukkit.getLogger().warning("[VoltPur-Tune] server.properties not found - nothing written.");
            return false;
        }
        try {
            Map<String, String> changes = new LinkedHashMap<>();
            for (String line : VoltPurHardware.recommendedServerSettings()) {
                if (!line.contains("=")) continue;
                String key = line.substring(0, line.indexOf('=')).trim();
                String value = line.substring(line.indexOf('=') + 1).trim();
                // Only plain, top-level server.properties keys; skip (paper-global.yml) hints.
                if (key.contains(" ") || key.contains(":") || key.contains("(") || key.contains(".")) continue;
                if (key.equals("view-distance") || key.equals("simulation-distance") || key.equals("max-players")) {
                    changes.put(key, value);
                }
            }
            if (changes.isEmpty()) return false;

            Map<String, String> previous = readValues(file, changes.keySet());
            backup(file);
            writePreservingComments(file, changes);

            StringBuilder summary = new StringBuilder();
            for (Map.Entry<String, String> change : changes.entrySet()) {
                summary.append(change.getKey()).append(' ').append(previous.getOrDefault(change.getKey(), "?"))
                        .append(" -> ").append(change.getValue()).append("  ");
            }
            Bukkit.getLogger().info("[VoltPur-Tune] server.properties written: " + summary
                    + "(backup: " + file.getName() + ".bak-*; takes effect after a restart)");
            return true;
        } catch (Exception e) {
            VoltPurGuard.failure(MODULE, e);
            Bukkit.getLogger().warning("[VoltPur-Tune] Could not update server.properties: " + e.getMessage());
            return false;
        }
    }

    /** Writes spigot.yml hopper-check/hopper-transfer. Requires the same opt-in. */
    public static boolean applyOptimizations() {
        if (!VoltPurConfig.hardwareAutoTune) return false;
        File spigot = new File("spigot.yml");
        if (!spigot.isFile()) {
            Bukkit.getLogger().info("[VoltPur-Tune] spigot.yml not present yet - it is generated on first start.");
            return false;
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(spigot);
            int previous = config.getInt("world-settings.default.ticks-per.hopper-check", 1);
            backup(spigot);
            config.set("world-settings.default.ticks-per.hopper-check", 8);
            config.set("world-settings.default.ticks-per.hopper-transfer", 8);
            config.save(spigot);
            Bukkit.getLogger().info("[VoltPur-Tune] spigot.yml written: hopper-check " + previous
                    + " -> 8, hopper-transfer -> 8 (backup: " + spigot.getName()
                    + ".bak-*; takes effect after a restart). Note: hopper skipping by time is intended behaviour - "
                    + "high-speed redstone contraptions that rely on per-tick hopper transfers may need 1.");
            return true;
        } catch (Exception e) {
            VoltPurGuard.failure(MODULE, e);
            Bukkit.getLogger().warning("[VoltPur-Tune] Could not update spigot.yml: " + e.getMessage());
            return false;
        }
    }

    /** Writes a human-readable sheet with the recommended values and their real file locations. */
    public static void writeTuningSheet() {
        try {
            Path path = Path.of("logs", "voltpur-tuning.txt");
            Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("=== VoltPur hardware tuning sheet ===\n");
            sb.append("generated: ").append(new Date()).append('\n');
            sb.append("NOTE: values are only WRITTEN when modules.hardware.auto-tune=true. A restart is required.\n\n");
            sb.append("-- recommended values (with their real file) --\n");
            for (String line : VoltPurHardware.recommendedServerSettings()) sb.append(line).append('\n');
            sb.append("\n-- JVM start command --\n").append(VoltPurHardware.recommendedJvmArgs()).append('\n');
            sb.append("\n-- current machine --\n");
            for (String line : VoltPurHardware.hardwareReport()) sb.append(line).append('\n');
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            Bukkit.getLogger().info("[VoltPur-Tune] Sheet written to logs/voltpur-tuning.txt");
        } catch (IOException e) {
            VoltPurGuard.failure(MODULE, e);
            Bukkit.getLogger().warning("[VoltPur-Tune] Could not write the tuning sheet: " + e.getMessage());
        }
    }

    /** Called at startup for panels that pre-enable auto-tune (still opt-in). */
    public static void onServerStart() {
        boolean properties = applyServerProperties();
        boolean hopper = applyOptimizations();
        writeTuningSheet();
        Bukkit.getLogger().info("[VoltPur-Tune] Startup tuning: server.properties=" + properties
                + ", spigot.yml=" + hopper + (properties || hopper ? " (restart to apply)" : ""));
    }

    // ------------------------------------------------------------------ file helpers

    private static void backup(File file) throws IOException {
        Path backup = Path.of(file.getPath() + ".bak-" + STAMP.format(new Date()));
        Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Map<String, String> readValues(File file, Set<String> keys) throws IOException {
        Map<String, String> found = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) continue;
            int eq = trimmed.indexOf('=');
            if (eq < 0) continue;
            String key = trimmed.substring(0, eq).trim();
            if (keys.contains(key)) found.put(key, trimmed.substring(eq + 1).trim());
        }
        return found;
    }

    /**
     * Rewrites only the given keys, keeping comments, blank lines, ordering and any
     * key VoltPur does not know about exactly as they were.
     */
    static void writePreservingComments(File file, Map<String, String> changes) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        List<String> output = new ArrayList<>(lines.size() + changes.size());
        Set<String> remaining = new LinkedHashSet<>(changes.keySet());
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                output.add(line);
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                output.add(line);
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            if (changes.containsKey(key)) {
                output.add(key + "=" + changes.get(key));
                remaining.remove(key);
            } else {
                output.add(line);
            }
        }
        for (String key : remaining) output.add(key + "=" + changes.get(key));
        Files.write(file.toPath(), output, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.CREATE);
    }
}
