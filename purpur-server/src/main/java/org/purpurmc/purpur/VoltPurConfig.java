package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * VoltPurConfig - ONLY real, functional options are exposed here.
 *
 * Removed options that were read from voltpur.yml but had no logic behind them
 * (backup, discord-webhook, connection-stability, anti-exploit, per-world-plugin,
 * padmin-webui port, aikar-flags). Dead config options confuse admins into
 * thinking a feature is active when it is not. Keeping config honest.
 */
public class VoltPurConfig {
    private static File CONFIG_FILE;
    public static YamlConfiguration config;

    // ---- Real: Performance tasks ----
    public static boolean performanceEnabled = true;
    public static int maxItemsPerWorld = 500;
    public static boolean tpsMonitor = true;
    public static boolean entityLimiter = true;
    public static boolean chunkOptimization = true;

    // ---- Real: Hopper Sleep (opt-in, default OFF) ----
    // Safe only when hopper is empty + no source above + no items in pickup
    // zone + not powered (tryMoveItems is then a guaranteed no-op).
    public static boolean hopperSleepEnabled = false; // opt-in: enable manually
    public static int hopperSleepCooldown = 3;        // ticks to rest while empty-safe

    // ---- Real: Hardware module ----
    public static boolean hardwareReport = true;
    public static boolean hardwareAutoTune = false; // opt-in
    public static boolean hardwareWarn = true;

    // ---- Real: Updater ----
    public static String githubToken = "";

    public static void init() {
        CONFIG_FILE = new File("voltpur.yml");
        config = new YamlConfiguration();
        try {
            if (CONFIG_FILE.exists()) config.load(CONFIG_FILE);
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.WARNING, "[VoltPur] Could not load voltpur.yml", ex);
        }
        config.addDefault("version", 1);
        config.addDefault("modules.performance.enabled", performanceEnabled);
        config.addDefault("modules.performance.max-items-per-world", maxItemsPerWorld);
        config.addDefault("modules.performance.tps-monitor", tpsMonitor);
        config.addDefault("modules.performance.entity-limiter", entityLimiter);
        config.addDefault("modules.performance.chunk-optimization", chunkOptimization);
        config.addDefault("modules.performance.hopper-sleep.enabled", hopperSleepEnabled);
        config.addDefault("modules.performance.hopper-sleep.cooldown", hopperSleepCooldown);
        config.addDefault("modules.hardware.report-enabled", hardwareReport);
        config.addDefault("modules.hardware.auto-tune", hardwareAutoTune);
        config.addDefault("modules.hardware.warn-incompatible", hardwareWarn);
        config.addDefault("update.github-token", githubToken);
        config.addDefault("update.auto-backup", true);
        config.options().copyDefaults(true);

        performanceEnabled = config.getBoolean("modules.performance.enabled", performanceEnabled);
        maxItemsPerWorld = config.getInt("modules.performance.max-items-per-world", maxItemsPerWorld);
        tpsMonitor = config.getBoolean("modules.performance.tps-monitor", tpsMonitor);
        entityLimiter = config.getBoolean("modules.performance.entity-limiter", entityLimiter);
        chunkOptimization = config.getBoolean("modules.performance.chunk-optimization", chunkOptimization);
        hopperSleepEnabled = config.getBoolean("modules.performance.hopper-sleep.enabled", hopperSleepEnabled);
        hopperSleepCooldown = Math.max(1, config.getInt("modules.performance.hopper-sleep.cooldown", hopperSleepCooldown));
        hardwareReport = config.getBoolean("modules.hardware.report-enabled", hardwareReport);
        hardwareAutoTune = config.getBoolean("modules.hardware.auto-tune", hardwareAutoTune);
        hardwareWarn = config.getBoolean("modules.hardware.warn-incompatible", hardwareWarn);
        githubToken = config.getString("update.github-token", githubToken);

        try { config.save(CONFIG_FILE); } catch (IOException e) { Bukkit.getLogger().warning("[VoltPur] Save failed: " + e.getMessage()); }
    }
}
