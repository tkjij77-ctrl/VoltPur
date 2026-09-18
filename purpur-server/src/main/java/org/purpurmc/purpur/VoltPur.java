package org.purpurmc.purpur;

import org.bukkit.Bukkit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * VoltPur entry point - branding, honest startup banner and module wiring.
 *
 * Hardening changes:
 *  - The banner reports implementation counts AND the current safety posture, so an
 *    operator can see at a glance whether risky behaviour is enabled.
 *  - Module startup is deferred to the first server tick (when the scheduler is
 *    guaranteed to exist) with a bounded retry, instead of racing the scheduler.
 *  - The "plugin-pro/" text no longer claims a load-order guarantee that Paper does
 *    not provide; it is described as what it is (a priority plugin folder whose
 *    JARs are still ordered by plugin dependencies).
 */
public class VoltPur {

    /** Single source of truth for the version string (docs must match this). */
    public static final String VERSION = "26.2.0-rc2";
    public static final String BRAND = "VoltPur";
    /**
     * Fallback only - used when the server cannot be asked (very early startup).
     * The real value comes from mcVersion(), which asks the running server. This
     * constant used to be the single source of truth and it silently went stale:
     * the server reported "Minecraft 26.2" while VoltPur printed "MC 1.21.10".
     */
    public static final String MC_VERSION_FALLBACK = "unknown";

    private static String mcVersionCache = null;

    /** Minecraft version as reported by the running server (never a guess). */
    public static String mcVersion() {
        if (mcVersionCache != null) return mcVersionCache;
        try {
            String reported = org.bukkit.Bukkit.getMinecraftVersion();
            if (reported != null && !reported.isBlank()) {
                mcVersionCache = reported.trim();
                return mcVersionCache;
            }
        } catch (Throwable notReadyYet) {
            // Called before the server finished starting, or the API is missing.
        }
        return MC_VERSION_FALLBACK;
    }

    private static boolean initialized = false;

    private VoltPur() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        Logger logger = Bukkit.getLogger();
        try {
            VoltPurConfig.init(); // load config first so the banner reports the real posture, not defaults
        } catch (Throwable t) {
            logger.warning("[VoltPur] Config load failed: " + t.getMessage());
        }
        logger.info("━━━━━━━━━━━━━━━━ VoltPur ⚡ " + VERSION + " ━━━━━━━━━━━━━━━━");
        logger.info("[VoltPur] Modules: " + VoltPurModules.activeCount() + " implemented, "
                + VoltPurModules.partialCount() + " partial, " + VoltPurModules.plannedCount()
                + " planned - run /voltpur modules for live health");
        logger.info("[VoltPur] Safety: item-limiter=" + state(VoltPurConfig.itemLimiterEnabled)
                + " optimizer=" + state(VoltPurConfig.optimizerEnabled)
                + " padmin=" + state(VoltPurConfig.padminEnabled)
                + " destructive-reinstall=" + state(VoltPurConfig.updateCleanReinstall)
                + " update-checksum=" + (VoltPurConfig.updateRequireChecksum ? "required" : "optional"));
        logger.info("[VoltPur] Commands: /voltpur help | /vo up list (verified, reversible updates)");
        printInstalledStamp(logger);

        scheduleOnFirstTick(() -> {
            VoltPurPerformance.init();
            VoltPurWorldCheck.init();
            VoltPurOptimizer.init();
            VoltPurDiscord.init();
            VoltPurBackup.init();
            VoltPurResourcePack.init();

            try {
                VoltPurHardware.detect();
                if (VoltPurConfig.hardwareReport) {
                    VoltPurHardware.reportToLogAndFile(VoltPurConfig.hardwareWarn);
                }
            } catch (Throwable t) {
                VoltPurGuard.failure("HardwareDetection", t);
            }
            if (VoltPurConfig.hardwareAutoTune) {
                try {
                    VoltPurTuning.onServerStart();
                } catch (Throwable t) {
                    VoltPurGuard.failure("HardwareAutoTune", t);
                }
            } else {
                VoltPurModules.setRuntime("HardwareAutoTune", false);
            }
            VoltPurModules.setRuntime("PAdminWebUI", false); // starts only on /padmin
        });
    }

    private static String state(boolean enabled) {
        return enabled ? "ON" : "off";
    }

    /**
     * Runs module wiring once the server tick loop exists. Bukkit's scheduler is not
     * guaranteed to be available while the server is still constructing itself, so
     * we defer to the first tick and retry briefly if needed.
     */
    private static void scheduleOnFirstTick(Runnable work) {
        Runnable guarded = () -> VoltPurGuard.run("Startup", work);
        try {
            Bukkit.getScheduler().runTask(VoltPurPlugin.get(), guarded);
            return;
        } catch (Throwable notReadyYet) {
            Bukkit.getLogger().info("[VoltPur] Scheduler not ready during config init - deferring module startup.");
        }
        Thread retry = new Thread(() -> {
            for (int attempt = 0; attempt < 60; attempt++) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    Bukkit.getScheduler().runTask(VoltPurPlugin.get(), guarded);
                    return;
                } catch (Throwable stillNotReady) {
                    // keep retrying until the server tick loop is up (max ~30s)
                }
            }
            Bukkit.getLogger().warning("[VoltPur] Module startup could not be scheduled after 30s.");
        }, "VoltPur-Startup");
        retry.setDaemon(true);
        retry.start();
    }

    public static String getVersion() {
        return VERSION;
    }

    /**
     * plugin-pro/ - a priority plugin folder, nothing more.
     *
     * The folder is registered as an extra provider source ahead of plugins/ by
     * VoltPur's paperweight patch, which makes its JARs *discovered* first. It does
     * NOT override Paper's load-order rules: the real order is decided by
     * depend/softdepend/loadbefore (Bukkit plugins) or load: BEFORE|AFTER
     * (paper-plugin.yml). VoltPur states this plainly instead of claiming a
     * guaranteed "loads before everything" behaviour.
     */
    public static void ensurePluginProFolder() {
        try {
            File folder = new File("plugin-pro");
            if (folder.exists()) return;
            if (!folder.mkdirs()) {
                Bukkit.getLogger().warning("[VoltPur] Could not create plugin-pro/ - check folder permissions.");
                return;
            }
            File readme = new File(folder, "README.txt");
            try (java.io.FileWriter writer = new java.io.FileWriter(readme)) {
                writer.write("VoltPur plugin-pro/ - priority plugin folder.\n");
                writer.write("JARs here are discovered before plugins/, but the final load order still\n");
                writer.write("follows plugin dependencies (depend/softdepend, or load: BEFORE|AFTER).\n");
                writer.write("Use it to keep infrastructure plugins (spark, Chunky, log filters) separate.\n");
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VoltPur] Could not prepare plugin-pro/: " + e.getMessage());
        }
    }

    /** Backwards-compatible wrapper kept for PurpurConfig.init(). */
    public static void loadPluginPro() {
        ensurePluginProFolder();
        File folder = new File("plugin-pro");
        File[] jars = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        int count = jars == null ? 0 : jars.length;
        Bukkit.getLogger().info("[VoltPur] plugin-pro/: " + count + " plugin jar(s) present "
                + "(discovered before plugins/; load order still follows each plugin's dependencies).");
    }

    /**
     * After a verified update, the updater writes voltpur-installed.txt. We print it
     * at boot so the operator can confirm exactly which build is running.
     */
    private static void printInstalledStamp(Logger logger) {
        try {
            Path stamp = Path.of("voltpur-installed.txt");
            if (!Files.isRegularFile(stamp)) return;
            List<String> lines = Files.readAllLines(stamp, StandardCharsets.UTF_8);
            String build = "?", sha = "?", verified = "?", installedAt = "?";
            for (String line : lines) {
                if (line.startsWith("build=")) build = line.substring(6).trim();
                else if (line.startsWith("commit=")) sha = line.substring(7).trim();
                else if (line.startsWith("checksum-verified=")) verified = line.substring(18).trim();
                else if (line.startsWith("installed-at=")) installedAt = line.substring(13).trim();
            }
            String shortSha = sha.length() >= 7 ? sha.substring(0, 7) : sha;
            logger.info("[VoltPur] Installed via /vo up -> build #" + build + " | commit " + shortSha
                    + " | checksum verified: " + verified + " | at " + installedAt);
            logger.info("[VoltPur] Cross-check: the 'This server is running Purpur ...@<commit>' line should match "
                    + (shortSha.isEmpty() ? "the stamp" : shortSha) + ".");
        } catch (Exception e) {
            logger.fine("[VoltPur] Could not read voltpur-installed.txt: " + e.getMessage());
        }
    }
}
