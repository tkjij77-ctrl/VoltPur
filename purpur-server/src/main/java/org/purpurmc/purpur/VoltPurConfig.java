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

    // ---- Real: Dynamic Optimizer (VoltCore signature feature) ----
    // Reads live server state (hoppers, entities, chunks, TPS) and applies the
    // best spigot.yml tuning automatically. Opt-in, measured via benchmark.
    public static boolean optimizerEnabled = true;

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

    // ---- Real: PAdmin WebUI security (default: disabled) ----
    public static boolean padminEnabled = false; // WebUI off unless enabled
    public static String padminUser = "admin";
    public static String padminPassword = "";    // if empty, WebUI stays off for safety
    public static int padminPort = 25567;

    // ---- Real: Discord Webhook (opt-in, default OFF) ----
    public static boolean discordEnabled = false;
    public static String discordWebhookUrl = "";
    public static boolean discordAnnouncePlayers = true; // join / leave
    public static boolean discordAnnounceServer = true;  // start / stop

    // ---- Real: World Backup (opt-in, default OFF) ----
    public static boolean backupEnabled = false;
    public static int backupIntervalMinutes = 60;
    public static int backupKeep = 5;
    public static String backupFolder = "backups";

    // ---- Real: Resource Pack HTTP (opt-in, default OFF) ----
    public static boolean resourcePackEnabled = false;
    public static int resourcePackPort = 25568;
    public static String resourcePackFile = "resourcepack.zip";

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
        config.addDefault("modules.performance.optimizer-enabled", optimizerEnabled);
        config.addDefault("modules.performance.hopper-sleep.enabled", hopperSleepEnabled);
        config.addDefault("modules.performance.hopper-sleep.cooldown", hopperSleepCooldown);
        config.addDefault("modules.hardware.report-enabled", hardwareReport);
        config.addDefault("modules.hardware.auto-tune", hardwareAutoTune);
        config.addDefault("modules.hardware.warn-incompatible", hardwareWarn);
        config.addDefault("update.github-token", githubToken);
        config.addDefault("modules.padmin.enabled", padminEnabled);
        config.addDefault("modules.padmin.user", padminUser);
        config.addDefault("modules.padmin.password", padminPassword);
        config.addDefault("modules.padmin.port", padminPort);
        config.addDefault("update.auto-backup", true);
        config.addDefault("modules.discord.enabled", discordEnabled);
        config.addDefault("modules.discord.webhook-url", discordWebhookUrl);
        config.addDefault("modules.discord.announce-players", discordAnnouncePlayers);
        config.addDefault("modules.discord.announce-server", discordAnnounceServer);
        config.addDefault("modules.backup.enabled", backupEnabled);
        config.addDefault("modules.backup.interval-minutes", backupIntervalMinutes);
        config.addDefault("modules.backup.keep", backupKeep);
        config.addDefault("modules.backup.folder", backupFolder);
        config.addDefault("modules.resource-pack.enabled", resourcePackEnabled);
        config.addDefault("modules.resource-pack.port", resourcePackPort);
        config.addDefault("modules.resource-pack.file", resourcePackFile);
        config.options().copyDefaults(true);

        performanceEnabled = config.getBoolean("modules.performance.enabled", performanceEnabled);
        maxItemsPerWorld = config.getInt("modules.performance.max-items-per-world", maxItemsPerWorld);
        tpsMonitor = config.getBoolean("modules.performance.tps-monitor", tpsMonitor);
        entityLimiter = config.getBoolean("modules.performance.entity-limiter", entityLimiter);
        chunkOptimization = config.getBoolean("modules.performance.chunk-optimization", chunkOptimization);
        optimizerEnabled = config.getBoolean("modules.performance.optimizer-enabled", optimizerEnabled);
        hopperSleepEnabled = config.getBoolean("modules.performance.hopper-sleep.enabled", hopperSleepEnabled);
        hopperSleepCooldown = Math.max(1, config.getInt("modules.performance.hopper-sleep.cooldown", hopperSleepCooldown));
        hardwareReport = config.getBoolean("modules.hardware.report-enabled", hardwareReport);
        hardwareAutoTune = config.getBoolean("modules.hardware.auto-tune", hardwareAutoTune);
        hardwareWarn = config.getBoolean("modules.hardware.warn-incompatible", hardwareWarn);
        githubToken = config.getString("update.github-token", githubToken);
        // Support reading the token from an environment variable (e.g. ${GITHUB_TOKEN})
        // so secrets are never stored in plaintext in voltpur.yml.
        githubToken = resolveSecret(githubToken);
        padminEnabled = config.getBoolean("modules.padmin.enabled", padminEnabled);
        padminUser = config.getString("modules.padmin.user", padminUser);
        padminPassword = resolveSecret(config.getString("modules.padmin.password", padminPassword));
        padminPort = config.getInt("modules.padmin.port", padminPort);
        discordEnabled = config.getBoolean("modules.discord.enabled", discordEnabled);
        discordWebhookUrl = config.getString("modules.discord.webhook-url", discordWebhookUrl);
        discordAnnouncePlayers = config.getBoolean("modules.discord.announce-players", discordAnnouncePlayers);
        discordAnnounceServer = config.getBoolean("modules.discord.announce-server", discordAnnounceServer);
        backupEnabled = config.getBoolean("modules.backup.enabled", backupEnabled);
        backupIntervalMinutes = Math.max(5, config.getInt("modules.backup.interval-minutes", backupIntervalMinutes));
        backupKeep = Math.max(1, config.getInt("modules.backup.keep", backupKeep));
        backupFolder = config.getString("modules.backup.folder", backupFolder);
        resourcePackEnabled = config.getBoolean("modules.resource-pack.enabled", resourcePackEnabled);
        resourcePackPort = config.getInt("modules.resource-pack.port", resourcePackPort);
        resourcePackFile = config.getString("modules.resource-pack.file", resourcePackFile);

        try { config.save(CONFIG_FILE); } catch (IOException e) { Bukkit.getLogger().warning("[VoltPur] Save failed: " + e.getMessage()); }
    }

    /**
     * If a config value looks like ${ENV_VAR}, read it from the environment.
     * This keeps secrets (tokens/passwords) out of plaintext files.
     */
    private static String resolveSecret(String value) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String env = value.substring(2, value.length() - 1);
            String resolved = System.getenv(env);
            if (resolved == null) {
                Bukkit.getLogger().warning("[VoltPur] Env var " + env + " not set; using empty value.");
                return "";
            }
            return resolved;
        }
        return value;
    }
}
