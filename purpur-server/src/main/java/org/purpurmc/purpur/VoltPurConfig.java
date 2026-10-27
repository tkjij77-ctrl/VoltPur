package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * VoltPurConfig - ONLY real, functional options are exposed here.
 *
 * Keeps configuration safe, clean, and honest.
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

    // ---- Real: Dynamic Optimizer & Autotune (§4.1, §4.3) ----
    public static boolean optimizerEnabled = true;
    public static String autotuneMode = "advisor"; // "advisor" or "active"
    public static int autotuneIntervalSeconds = 30;
    public static int autotuneMinViewDistance = 4;
    public static int autotuneMinSimulationDistance = 4;
    public static boolean autotuneSmartItemLimiter = true;
    public static int autotuneItemExemptTicks = 100;
    public static double autotuneItemExemptRadius = 8.0;

    // ---- Real: Pregeneration ----
    public static boolean pregenEnabled = false;
    public static int pregenMaxPlayers = 0;

    // ---- Real: Hopper Sleep (opt-in, default OFF) ----
    public static boolean hopperSleepEnabled = false;
    public static int hopperSleepCooldown = 3;

    // ---- Real: Hardware module ----
    public static boolean hardwareReport = true;
    public static boolean hardwareAutoTune = false;
    public static boolean hardwareWarn = true;

    // ---- Real: Updater ----
    public static String githubToken = "";

    // ---- Real: PAdmin WebUI security (default: disabled) ----
    public static boolean padminEnabled = false;
    public static String padminUser = "admin";
    public static String padminPassword = "";
    public static int padminPort = 25567;

    // ---- Real: Discord Webhook (opt-in, default OFF) ----
    public static boolean discordEnabled = false;
    public static String discordWebhookUrl = "";
    public static boolean discordAnnouncePlayers = true;
    public static boolean discordAnnounceServer = true;

    // ---- Real: World Backup (opt-in, default OFF) ----
    public static boolean backupEnabled = false;
    public static int backupIntervalMinutes = 60;
    public static int backupKeep = 5;
    public static String backupFolder = "backups";

    // ---- Real: Resource Pack HTTP (opt-in, default OFF) ----
    public static boolean resourcePackEnabled = false;
    public static int resourcePackPort = 25568;
    public static String resourcePackFile = "resourcepack.zip";

    // ---- Real: AI Practical Layer (§5) ----
    public static boolean aiEnabled = true;
    public static String aiApiKey = "";
    public static String aiProvider = "auto";
    public static String aiBaseUrl = "";
    public static String aiModel = "auto";
    public static double aiMonthlyBudgetUsd = 5.0;
    public static boolean aiPaidConfirmed = false;
    public static boolean aiIncludePlayerData = false;

    public static boolean isAutotuneActive() {
        return "active".equalsIgnoreCase(autotuneMode);
    }

    public static void setAiPaidConfirmed(boolean confirmed) {
        aiPaidConfirmed = confirmed;
        if (config != null && CONFIG_FILE != null) {
            config.set("ai.paid-confirmed", confirmed);
            try {
                config.save(CONFIG_FILE);
            } catch (IOException e) {
                Bukkit.getLogger().warning("[VoltPur] Save failed: " + e.getMessage());
            }
        }
    }

    public static void setAiKeyAndProvider(String provider, String key) {
        aiProvider = provider;
        aiApiKey = key;
        aiEnabled = true;
        if (config != null && CONFIG_FILE != null) {
            config.set("ai.enabled", true);
            config.set("ai.provider", provider);
            config.set("ai.api-key", key);
            try {
                config.save(CONFIG_FILE);
            } catch (IOException e) {
                Bukkit.getLogger().warning("[VoltPur] Save failed: " + e.getMessage());
            }
        }
    }

    public static void setAiModel(String model) {
        aiModel = model;
        if (config != null && CONFIG_FILE != null) {
            config.set("ai.model", model);
            try {
                config.save(CONFIG_FILE);
            } catch (IOException e) {
                Bukkit.getLogger().warning("[VoltPur] Save failed: " + e.getMessage());
            }
        }
    }

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
        config.addDefault("modules.performance.autotune-mode", autotuneMode);
        config.addDefault("modules.performance.autotune-interval-seconds", autotuneIntervalSeconds);
        config.addDefault("modules.performance.autotune-min-view-distance", autotuneMinViewDistance);
        config.addDefault("modules.performance.autotune-min-simulation-distance", autotuneMinSimulationDistance);
        config.addDefault("modules.performance.autotune-smart-item-limiter", autotuneSmartItemLimiter);
        config.addDefault("modules.performance.autotune-item-exempt-ticks", autotuneItemExemptTicks);
        config.addDefault("modules.performance.autotune-item-exempt-radius", autotuneItemExemptRadius);
        config.addDefault("modules.performance.pregen-enabled", pregenEnabled);
        config.addDefault("modules.performance.pregen-max-players", pregenMaxPlayers);
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

        // AI options (§5.7)
        config.addDefault("ai.enabled", aiEnabled);
        config.addDefault("ai.api-key", aiApiKey);
        config.addDefault("ai.provider", aiProvider);
        config.addDefault("ai.base-url", aiBaseUrl);
        config.addDefault("ai.model", aiModel);
        config.addDefault("ai.monthly-budget-usd", aiMonthlyBudgetUsd);
        config.addDefault("ai.paid-confirmed", aiPaidConfirmed);
        config.addDefault("ai.include-player-data", aiIncludePlayerData);

        config.options().copyDefaults(true);

        performanceEnabled = config.getBoolean("modules.performance.enabled", performanceEnabled);
        maxItemsPerWorld = config.getInt("modules.performance.max-items-per-world", maxItemsPerWorld);
        tpsMonitor = config.getBoolean("modules.performance.tps-monitor", tpsMonitor);
        entityLimiter = config.getBoolean("modules.performance.entity-limiter", entityLimiter);
        chunkOptimization = config.getBoolean("modules.performance.chunk-optimization", chunkOptimization);
        optimizerEnabled = config.getBoolean("modules.performance.optimizer-enabled", optimizerEnabled);
        autotuneMode = config.getString("modules.performance.autotune-mode", autotuneMode);
        autotuneIntervalSeconds = config.getInt("modules.performance.autotune-interval-seconds", autotuneIntervalSeconds);
        autotuneMinViewDistance = config.getInt("modules.performance.autotune-min-view-distance", autotuneMinViewDistance);
        autotuneMinSimulationDistance = config.getInt("modules.performance.autotune-min-simulation-distance", autotuneMinSimulationDistance);
        autotuneSmartItemLimiter = config.getBoolean("modules.performance.autotune-smart-item-limiter", autotuneSmartItemLimiter);
        autotuneItemExemptTicks = config.getInt("modules.performance.autotune-item-exempt-ticks", autotuneItemExemptTicks);
        autotuneItemExemptRadius = config.getDouble("modules.performance.autotune-item-exempt-radius", autotuneItemExemptRadius);
        pregenEnabled = config.getBoolean("modules.performance.pregen-enabled", pregenEnabled);
        pregenMaxPlayers = config.getInt("modules.performance.pregen-max-players", pregenMaxPlayers);
        hopperSleepEnabled = config.getBoolean("modules.performance.hopper-sleep.enabled", hopperSleepEnabled);
        hopperSleepCooldown = Math.max(1, config.getInt("modules.performance.hopper-sleep.cooldown", hopperSleepCooldown));
        hardwareReport = config.getBoolean("modules.hardware.report-enabled", hardwareReport);
        hardwareAutoTune = config.getBoolean("modules.hardware.auto-tune", hardwareAutoTune);
        hardwareWarn = config.getBoolean("modules.hardware.warn-incompatible", hardwareWarn);
        githubToken = resolveSecret(config.getString("update.github-token", githubToken));
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

        // AI values
        aiEnabled = config.getBoolean("ai.enabled", aiEnabled);
        aiApiKey = resolveSecret(config.getString("ai.api-key", aiApiKey));
        aiProvider = config.getString("ai.provider", aiProvider);
        aiBaseUrl = config.getString("ai.base-url", aiBaseUrl);
        aiModel = config.getString("ai.model", aiModel);
        aiMonthlyBudgetUsd = config.getDouble("ai.monthly-budget-usd", aiMonthlyBudgetUsd);
        aiPaidConfirmed = config.getBoolean("ai.paid-confirmed", aiPaidConfirmed);
        aiIncludePlayerData = config.getBoolean("ai.include-player-data", aiIncludePlayerData);

        try { config.save(CONFIG_FILE); } catch (IOException e) { Bukkit.getLogger().warning("[VoltPur] Save failed: " + e.getMessage()); }
    }

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
