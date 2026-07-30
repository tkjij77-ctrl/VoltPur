
package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class VoltPurConfig {
    private static File CONFIG_FILE;
    public static YamlConfiguration config;
    public static boolean backupEnabled = true;
    public static int backupIntervalMinutes = 5;
    public static boolean discordWebhookEnabled = false;
    public static String discordWebhookUrl = "";
    public static boolean aikarFlagsAuto = true;
    public static boolean connectionStability = true;
    public static boolean antiExploit = true;
    public static boolean perWorldPlugin = true;
    public static boolean padminWebUI = true;
    public static int padminPort = 25567;
    public static boolean performanceEnabled = true;
    public static int maxItemsPerWorld = 500;
    public static boolean tpsMonitor = true;
    public static boolean hopperOptimization = true;
    public static boolean entityLimiter = true;

    public static void init() {
        CONFIG_FILE = new File("voltpur.yml");
        config = new YamlConfiguration();
        try {
            if (CONFIG_FILE.exists()) {
                config.load(CONFIG_FILE);
            }
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.WARNING, "[VoltPur] Could not load voltpur.yml", ex);
        }
        // Defaults
        config.addDefault("version", 1);
        config.addDefault("modules.backup.enabled", backupEnabled);
        config.addDefault("modules.backup.interval-minutes", backupIntervalMinutes);
        config.addDefault("modules.discord-webhook.enabled", discordWebhookEnabled);
        config.addDefault("modules.discord-webhook.url", discordWebhookUrl);
        config.addDefault("modules.aikar-flags-auto.enabled", aikarFlagsAuto);
        config.addDefault("modules.connection-stability.enabled", connectionStability);
        config.addDefault("modules.anti-exploit.enabled", antiExploit);
        config.addDefault("modules.per-world-plugin.enabled", perWorldPlugin);
        config.addDefault("modules.padmin-webui.enabled", padminWebUI);
        config.addDefault("modules.padmin-webui.port", padminPort);
        config.addDefault("modules.performance.enabled", performanceEnabled);
        config.addDefault("modules.performance.max-items-per-world", maxItemsPerWorld);
        config.addDefault("modules.performance.tps-monitor", tpsMonitor);
        config.addDefault("modules.performance.hopper-optimization", hopperOptimization);
        config.addDefault("modules.performance.entity-limiter", entityLimiter);
        config.options().copyDefaults(true);
        
        backupEnabled = config.getBoolean("modules.backup.enabled", backupEnabled);
        backupIntervalMinutes = config.getInt("modules.backup.interval-minutes", backupIntervalMinutes);
        discordWebhookEnabled = config.getBoolean("modules.discord-webhook.enabled", discordWebhookEnabled);
        discordWebhookUrl = config.getString("modules.discord-webhook.url", discordWebhookUrl);
        aikarFlagsAuto = config.getBoolean("modules.aikar-flags-auto.enabled", aikarFlagsAuto);
        connectionStability = config.getBoolean("modules.connection-stability.enabled", connectionStability);
        antiExploit = config.getBoolean("modules.anti-exploit.enabled", antiExploit);
        perWorldPlugin = config.getBoolean("modules.per-world-plugin.enabled", perWorldPlugin);
        padminWebUI = config.getBoolean("modules.padmin-webui.enabled", padminWebUI);
        padminPort = config.getInt("modules.padmin-webui.port", padminPort);
        performanceEnabled = config.getBoolean("modules.performance.enabled", performanceEnabled);
        maxItemsPerWorld = config.getInt("modules.performance.max-items-per-world", maxItemsPerWorld);
        tpsMonitor = config.getBoolean("modules.performance.tps-monitor", tpsMonitor);
        hopperOptimization = config.getBoolean("modules.performance.hopper-optimization", hopperOptimization);
        entityLimiter = config.getBoolean("modules.performance.entity-limiter", entityLimiter);

        try { config.save(CONFIG_FILE); } catch (IOException e) { Bukkit.getLogger().warning("[VoltPur] Save failed: " + e.getMessage()); }
    }
}
