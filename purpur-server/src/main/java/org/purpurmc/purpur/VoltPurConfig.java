package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.logging.Level;

/**
 * VoltPurConfig - every option here is READ by real code.
 *
 * Policy (see POLICY.md): an option may only exist if some module consumes it.
 * When behaviour is risky, the safe value is the default:
 *   - item limiting (deletes player items)      -> OFF by default
 *   - dynamic optimizer (changes gameplay)      -> OFF by default
 *   - PAdmin WebUI (network listener)           -> OFF by default, needs password
 *   - destructive clean reinstall on update     -> OFF by default
 *   - update checksum verification              -> REQUIRED by default
 *
 * Removed for being dead (they were read from voltpur.yml but no code used them):
 *   modules.performance.entity-limiter, modules.performance.hopper-sleep.*
 * Keys kept for backwards compatibility are marked "legacy" and only logged.
 */
public class VoltPurConfig {
    private static File CONFIG_FILE;
    public static YamlConfiguration config;

    // ---- Core: real performance tasks ----
    public static boolean performanceEnabled = true;
    public static boolean tpsMonitor = true;
    public static boolean chunkWarning = true;

    // ---- ItemLimiter (deletes dropped items) - OPT-IN ----
    public static boolean itemLimiterEnabled = false;
    public static int itemLimiterMaxPerWorld = 500;
    public static int itemLimiterMinAgeSeconds = 60;
    public static boolean itemLimiterKeepNamed = true;
    public static boolean itemLimiterWarnPlayers = true;

    // ---- DynamicOptimizer (changes spawn limits / simulation distance) - OPT-IN ----
    public static boolean optimizerEnabled = false;
    public static int optimizerStartupGraceSeconds = 180;
    public static int optimizerSustainSeconds = 120;
    public static int optimizerRevertAfterMinutes = 15;

    // ---- Hardware module ----
    public static boolean hardwareReport = true;
    public static boolean hardwareAutoTune = false; // opt-in: writes server.properties/spigot.yml
    public static boolean hardwareWarn = true;

    // ---- Updater ----
    public static String githubToken = "";
    public static boolean updateRequireChecksum = true; // refuse unverifiable downloads
    public static boolean updateAutoBackup = true;      // real backup before an update (was dead before)
    public static boolean updateKeepJarBackup = true;   // server.jar.bak-<timestamp> for rollback
    public static boolean updateCleanReinstall = false; // destructive mode, OFF (only generated dirs)

    // ---- PAdmin WebUI (loopback + Basic Auth only) ----
    public static boolean padminEnabled = false;
    public static String padminUser = "admin";
    public static String padminPassword = "";
    public static int padminPort = 25567;

    // ---- Discord Webhook (opt-in) ----
    public static boolean discordEnabled = false;
    public static String discordWebhookUrl = "";
    public static boolean discordAnnouncePlayers = true;
    public static boolean discordAnnounceServer = true;

    // ---- World Backup (opt-in) ----
    public static boolean backupEnabled = false;
    public static int backupIntervalMinutes = 60;
    public static int backupKeep = 5;
    public static String backupFolder = "backups";
    public static boolean backupConsistent = true; // save-off / save-all flush / save-on around the zip

    // ---- Resource Pack HTTP (opt-in) ----
    public static boolean resourcePackEnabled = false;
    public static int resourcePackPort = 25568;
    public static String resourcePackFile = "resourcepack.zip";
    public static String resourcePackToken = ""; // random path segment; generated on first enable

    public static void init() {
        CONFIG_FILE = new File("voltpur.yml");
        config = new YamlConfiguration();
        try {
            if (CONFIG_FILE.exists()) config.load(CONFIG_FILE);
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.WARNING, "[VoltPur] Could not load voltpur.yml", ex);
        }

        warnAboutLegacyKeys();

        config.addDefault("version", 2);

        config.addDefault("modules.performance.enabled", performanceEnabled);
        config.addDefault("modules.performance.tps-monitor", tpsMonitor);
        config.addDefault("modules.performance.chunk-warning", chunkWarning);

        config.addDefault("modules.performance.item-limiter.enabled", itemLimiterEnabled);
        config.addDefault("modules.performance.item-limiter.max-items-per-world", itemLimiterMaxPerWorld);
        config.addDefault("modules.performance.item-limiter.min-age-seconds", itemLimiterMinAgeSeconds);
        config.addDefault("modules.performance.item-limiter.keep-named", itemLimiterKeepNamed);
        config.addDefault("modules.performance.item-limiter.warn-players", itemLimiterWarnPlayers);

        config.addDefault("modules.performance.optimizer.enabled", optimizerEnabled);
        config.addDefault("modules.performance.optimizer.startup-grace-seconds", optimizerStartupGraceSeconds);
        config.addDefault("modules.performance.optimizer.sustain-seconds", optimizerSustainSeconds);
        config.addDefault("modules.performance.optimizer.revert-after-minutes", optimizerRevertAfterMinutes);

        config.addDefault("modules.hardware.report-enabled", hardwareReport);
        config.addDefault("modules.hardware.auto-tune", hardwareAutoTune);
        config.addDefault("modules.hardware.warn-incompatible", hardwareWarn);

        config.addDefault("update.github-token", githubToken);
        config.addDefault("update.require-checksum", updateRequireChecksum);
        config.addDefault("update.auto-backup", updateAutoBackup);
        config.addDefault("update.keep-jar-backup", updateKeepJarBackup);
        config.addDefault("update.clean-reinstall", updateCleanReinstall);

        config.addDefault("modules.padmin.enabled", padminEnabled);
        config.addDefault("modules.padmin.user", padminUser);
        config.addDefault("modules.padmin.password", padminPassword);
        config.addDefault("modules.padmin.port", padminPort);

        config.addDefault("modules.discord.enabled", discordEnabled);
        config.addDefault("modules.discord.webhook-url", discordWebhookUrl);
        config.addDefault("modules.discord.announce-players", discordAnnouncePlayers);
        config.addDefault("modules.discord.announce-server", discordAnnounceServer);

        config.addDefault("modules.backup.enabled", backupEnabled);
        config.addDefault("modules.backup.interval-minutes", backupIntervalMinutes);
        config.addDefault("modules.backup.keep", backupKeep);
        config.addDefault("modules.backup.folder", backupFolder);
        config.addDefault("modules.backup.consistent", backupConsistent);

        config.addDefault("modules.resource-pack.enabled", resourcePackEnabled);
        config.addDefault("modules.resource-pack.port", resourcePackPort);
        config.addDefault("modules.resource-pack.file", resourcePackFile);
        config.addDefault("modules.resource-pack.token", resourcePackToken);
        config.options().copyDefaults(true);

        performanceEnabled = config.getBoolean("modules.performance.enabled", performanceEnabled);
        tpsMonitor = config.getBoolean("modules.performance.tps-monitor", tpsMonitor);
        chunkWarning = config.getBoolean("modules.performance.chunk-warning", chunkWarning);

        itemLimiterEnabled = config.getBoolean("modules.performance.item-limiter.enabled", itemLimiterEnabled);
        // legacy fallback: the old flat key is honoured (and reported) instead of being ignored.
        if (!config.isSet("modules.performance.item-limiter.max-items-per-world")
                && config.isSet("modules.performance.max-items-per-world")) {
            itemLimiterMaxPerWorld = config.getInt("modules.performance.max-items-per-world", itemLimiterMaxPerWorld);
        }
        itemLimiterMaxPerWorld = Math.max(50, config.getInt("modules.performance.item-limiter.max-items-per-world", itemLimiterMaxPerWorld));
        itemLimiterMinAgeSeconds = Math.max(0, config.getInt("modules.performance.item-limiter.min-age-seconds", itemLimiterMinAgeSeconds));
        itemLimiterKeepNamed = config.getBoolean("modules.performance.item-limiter.keep-named", itemLimiterKeepNamed);
        itemLimiterWarnPlayers = config.getBoolean("modules.performance.item-limiter.warn-players", itemLimiterWarnPlayers);

        optimizerEnabled = config.getBoolean("modules.performance.optimizer.enabled", optimizerEnabled);
        optimizerStartupGraceSeconds = Math.max(30, config.getInt("modules.performance.optimizer.startup-grace-seconds", optimizerStartupGraceSeconds));
        optimizerSustainSeconds = Math.max(60, config.getInt("modules.performance.optimizer.sustain-seconds", optimizerSustainSeconds));
        optimizerRevertAfterMinutes = Math.max(5, config.getInt("modules.performance.optimizer.revert-after-minutes", optimizerRevertAfterMinutes));

        hardwareReport = config.getBoolean("modules.hardware.report-enabled", hardwareReport);
        hardwareAutoTune = config.getBoolean("modules.hardware.auto-tune", hardwareAutoTune);
        hardwareWarn = config.getBoolean("modules.hardware.warn-incompatible", hardwareWarn);

        githubToken = resolveSecret(config.getString("update.github-token", githubToken));
        updateRequireChecksum = config.getBoolean("update.require-checksum", updateRequireChecksum);
        updateAutoBackup = config.getBoolean("update.auto-backup", updateAutoBackup);
        updateKeepJarBackup = config.getBoolean("update.keep-jar-backup", updateKeepJarBackup);
        updateCleanReinstall = config.getBoolean("update.clean-reinstall", updateCleanReinstall);

        padminEnabled = config.getBoolean("modules.padmin.enabled", padminEnabled);
        padminUser = config.getString("modules.padmin.user", padminUser);
        padminPassword = resolveSecret(config.getString("modules.padmin.password", padminPassword));
        padminPort = clampPort(config.getInt("modules.padmin.port", padminPort), 25567);

        discordEnabled = config.getBoolean("modules.discord.enabled", discordEnabled);
        discordWebhookUrl = config.getString("modules.discord.webhook-url", discordWebhookUrl);
        discordAnnouncePlayers = config.getBoolean("modules.discord.announce-players", discordAnnouncePlayers);
        discordAnnounceServer = config.getBoolean("modules.discord.announce-server", discordAnnounceServer);

        backupEnabled = config.getBoolean("modules.backup.enabled", backupEnabled);
        backupIntervalMinutes = Math.max(5, config.getInt("modules.backup.interval-minutes", backupIntervalMinutes));
        backupKeep = Math.max(1, config.getInt("modules.backup.keep", backupKeep));
        backupFolder = config.getString("modules.backup.folder", backupFolder);
        backupConsistent = config.getBoolean("modules.backup.consistent", backupConsistent);

        resourcePackEnabled = config.getBoolean("modules.resource-pack.enabled", resourcePackEnabled);
        resourcePackPort = clampPort(config.getInt("modules.resource-pack.port", resourcePackPort), 25568);
        resourcePackFile = config.getString("modules.resource-pack.file", resourcePackFile);
        resourcePackToken = config.getString("modules.resource-pack.token", resourcePackToken);
        if (resourcePackEnabled && (resourcePackToken == null || resourcePackToken.isBlank())) {
            resourcePackToken = randomToken(); // unguessable path so the pack is not a public open endpoint
            config.set("modules.resource-pack.token", resourcePackToken);
        }

        save();
    }

    private static void warnAboutLegacyKeys() {
        List<String> dead = List.of(
                "modules.performance.entity-limiter",
                "modules.performance.hopper-sleep.enabled",
                "modules.performance.hopper-sleep.cooldown",
                "modules.performance.chunk-optimization"
        );
        for (String key : dead) {
            if (config.isSet(key)) {
                Bukkit.getLogger().info("[VoltPur] voltpur.yml key '" + key
                        + "' is no longer used by any module and can be deleted (see POLICY.md).");
            }
        }
    }

    private static int clampPort(int port, int fallback) {
        return (port > 0 && port <= 65535) ? port : fallback;
    }

    private static String randomToken() {
        byte[] bytes = new byte[8];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
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
                Bukkit.getLogger().warning("[VoltPur] Env var " + env + " is not set; using an empty value.");
                return "";
            }
            return resolved;
        }
        return value;
    }

    private static void save() {
        try {
            config.save(CONFIG_FILE);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[VoltPur] Could not save voltpur.yml: " + e.getMessage());
        }
    }
}
